package ac.grim.lightninggrim.bridge.velocity;

import ac.grim.lightninggrim.bridge.BridgeProtocol;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(id = "lightninggrim-bridge", name = "LightningGrimBridge", version = "2.3.74", authors = {"Axionize"})
public final class VelocityBridgePlugin {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(BridgeProtocol.CHANNEL);
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final ConcurrentHashMap<UUID, Long> acceptedRequests = new ConcurrentHashMap<>();
    private byte[] secret;
    private Set<String> allowedBackends = Set.of();
    private long requestTtlMs;
    private long replayTtlMs;
    private String banCommand;
    private boolean enabled;

    @Inject
    public VelocityBridgePlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        enabled = loadConfig();
        proxy.getChannelRegistrar().register(CHANNEL);
        if (enabled) logger.info("LightningGrim bridge enabled for backends: {}", String.join(", ", allowedBackends));
        else logger.warn("LightningGrim bridge is disabled; configure config.properties before accepting network bans.");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL) || !enabled || !(event.getSource() instanceof ServerConnection source)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        String backend = source.getServer().getServerInfo().getName();
        if (!allowedBackends.contains(backend)) return;
        try {
            BridgeProtocol.BanRequest request = BridgeProtocol.decode(event.getData(), secret);
            long now = System.currentTimeMillis();
            if (!request.backendId().equals(backend) || !request.playerName().matches("[A-Za-z0-9_]{1,16}")
                    || Math.abs(now - request.issuedAt()) > requestTtlMs) return;
            acceptedRequests.entrySet().removeIf(entry -> now - entry.getValue() > replayTtlMs);
            if (acceptedRequests.putIfAbsent(request.requestId(), now) != null) return;
            proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), banCommand.replace("%player%", request.playerName()));
            logger.info("Accepted LightningGrim ban request {} from {} for {} ({}, VL {})", request.requestId(), backend,
                    request.playerName(), request.checkKey(), request.violations());
        } catch (IOException | GeneralSecurityException exception) {
            logger.warn("Rejected malformed LightningGrim bridge request from {}", backend);
        }
    }

    private boolean loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path config = dataDirectory.resolve("config.properties");
            if (Files.notExists(config)) {
                Files.writeString(config, "enabled=false\nhmacSecretBase64=\nallowedBackends=\nrequestTtlMs=10000\nreplayTtlMs=60000\nbanCommand=litebans:ban %player% Automated LightningGrim network ban\n");
                logger.warn("Created bridge configuration at {}; configure it before enabling the bridge.", config);
                return false;
            }
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(config)) { properties.load(input); }
            if (!Boolean.parseBoolean(properties.getProperty("enabled", "false"))) return false;
            secret = Base64.getDecoder().decode(properties.getProperty("hmacSecretBase64", ""));
            allowedBackends = Set.of(properties.getProperty("allowedBackends", "").split(","));
            requestTtlMs = Long.parseLong(properties.getProperty("requestTtlMs", "10000"));
            replayTtlMs = Long.parseLong(properties.getProperty("replayTtlMs", "60000"));
            banCommand = properties.getProperty("banCommand", "").trim();
            return secret.length >= 32 && !allowedBackends.isEmpty() && !allowedBackends.contains("")
                    && requestTtlMs > 0 && replayTtlMs >= requestTtlMs && banCommand.contains("%player%");
        } catch (Exception exception) {
            logger.error("Unable to load LightningGrim bridge configuration", exception);
            return false;
        }
    }
}
