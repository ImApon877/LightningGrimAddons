package ac.grim.lightninggrim.bridge.bungee;

import ac.grim.lightninggrim.bridge.BridgeProtocol;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

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

public final class BungeeBridgePlugin extends Plugin implements Listener {
    private final ConcurrentHashMap<UUID, Long> acceptedRequests = new ConcurrentHashMap<>();
    private byte[] secret;
    private Set<String> allowedBackends = Set.of();
    private long requestTtlMs;
    private long replayTtlMs;
    private String banCommand;
    private boolean enabled;

    @Override
    public void onEnable() {
        enabled = loadConfig();
        ProxyServer.getInstance().registerChannel(BridgeProtocol.CHANNEL);
        ProxyServer.getInstance().getPluginManager().registerListener(this, this);
        if (enabled) getLogger().info("LightningGrim bridge enabled for backends: " + String.join(", ", allowedBackends));
        else getLogger().warning("LightningGrim bridge is disabled; configure config.properties before accepting network bans.");
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(BridgeProtocol.CHANNEL) || !enabled || !(event.getSender() instanceof Server source)) return;
        event.setCancelled(true);
        String backend = source.getInfo().getName();
        if (!allowedBackends.contains(backend)) return;
        try {
            BridgeProtocol.BanRequest request = BridgeProtocol.decode(event.getData(), secret);
            long now = System.currentTimeMillis();
            if (!request.backendId().equals(backend) || !request.playerName().matches("[A-Za-z0-9_]{1,16}")
                    || Math.abs(now - request.issuedAt()) > requestTtlMs) return;
            acceptedRequests.entrySet().removeIf(entry -> now - entry.getValue() > replayTtlMs);
            if (acceptedRequests.putIfAbsent(request.requestId(), now) != null) return;
            ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(),
                    banCommand.replace("%player%", request.playerName()));
            getLogger().info("Accepted LightningGrim ban request " + request.requestId() + " from " + backend + " for " + request.playerName());
        } catch (IOException | GeneralSecurityException exception) {
            getLogger().warning("Rejected malformed LightningGrim bridge request from " + backend);
        }
    }

    private boolean loadConfig() {
        try {
            Path folder = getDataFolder().toPath();
            Files.createDirectories(folder);
            Path config = folder.resolve("config.properties");
            if (Files.notExists(config)) {
                Files.writeString(config, "enabled=false\nhmacSecretBase64=\nallowedBackends=\nrequestTtlMs=10000\nreplayTtlMs=60000\nbanCommand=litebans:ban %player% Automated LightningGrim network ban\n");
                getLogger().warning("Created bridge configuration at " + config + "; configure it before enabling the bridge.");
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
            getLogger().warning("Unable to load LightningGrim bridge configuration: " + exception.getMessage());
            return false;
        }
    }
}
