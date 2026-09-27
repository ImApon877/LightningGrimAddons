package ac.grim.lightninggrim.bridge.velocity;

import ac.grim.lightninggrim.bridge.BanBridge;
import ac.grim.lightninggrim.bridge.BridgeProtocol;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Plugin(id = "lightninggrim-bridge", name = "LightningGrimBridge", version = "2.3.74", authors = {"Axionize"})
public final class VelocityBridgePlugin {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(BridgeProtocol.CHANNEL);
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final BanBridge bridge = new BanBridge() {
        @Override
        protected boolean dispatch(String command, BridgeProtocol.BanRequest request, String backend) {
            proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
            logger.info("Accepted LightningGrim ban request {} from {} for {} ({}, VL {})",
                    request.requestId(), backend, request.playerName(), request.checkKey(), request.violations());
            return true;
        }
    };

    @Inject
    public VelocityBridgePlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();
        proxy.getChannelRegistrar().register(CHANNEL);
        if (bridge.isEnabled()) {
            logger.info("LightningGrim bridge enabled for backends: {}", String.join(", ", bridge.settings().allowedBackends()));
        } else {
            logger.warn("LightningGrim bridge is disabled; configure config.properties before accepting network bans.");
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL) || !bridge.isEnabled()
                || !(event.getSource() instanceof ServerConnection source)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        String backend = source.getServer().getServerInfo().getName();

        long start = System.nanoTime();
        boolean accepted = bridge.handlePayload(backend, event.getData(), System.currentTimeMillis());
        if (!accepted) logger.debug("Rejected or ignored LightningGrim bridge request from {}", backend);
        else logger.debug("Bridge dispatch took {} us", (System.nanoTime() - start) / 1000);
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path config = dataDirectory.resolve("config.properties");
            if (Files.notExists(config)) {
                Files.writeString(config, BanBridge.DEFAULT_CONFIG);
                logger.warn("Created bridge configuration at {}; configure it before enabling the bridge.", config);
                return;
            }
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(config)) {
                properties.load(input);
            }
            BanBridge.ConfigResult result = BanBridge.parseSettings(toStringMap(properties));
            if (result.ok()) {
                bridge.apply(result.settings());
            } else {
                logger.error("LightningGrim bridge config is invalid and stays disabled: {}", result.error());
            }
        } catch (IOException exception) {
            logger.error("Unable to load LightningGrim bridge configuration", exception);
        }
    }

    private static java.util.Map<String, String> toStringMap(Properties properties) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return map;
    }
}
