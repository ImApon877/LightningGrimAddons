package ac.grim.lightninggrim.bridge.velocity;

import ac.grim.lightninggrim.bridge.BanBridge;
import ac.grim.lightninggrim.bridge.BridgeProtocol;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Plugin(id = "lightninggrim-bridge", name = "LightningGrimBridge", version = "2.3.74", authors = {"Axionize", "ImApon877"})
public final class VelocityBridgePlugin {
    private static final String RELOAD_PERMISSION = "lightninggrim.bridge.reload";
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
        registerReloadCommand();
        if (bridge.isEnabled()) {
            logger.info("LightningGrim bridge enabled for backends: {}", String.join(", ", bridge.settings().allowedBackends()));
        } else {
            logger.warn("LightningGrim bridge is disabled; configure config.properties before accepting network bans.");
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;
        // Never forward bridge traffic, even while the bridge is disabled or when a client sends it.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!bridge.isEnabled() || !(event.getSource() instanceof ServerConnection source)) return;
        String backend = source.getServer().getServerInfo().getName();

        long start = System.nanoTime();
        boolean accepted = bridge.handlePayload(backend, event.getData(), System.currentTimeMillis());
        if (!accepted) logger.debug("Rejected or ignored LightningGrim bridge request from {}", backend);
        else logger.debug("Bridge dispatch took {} us", (System.nanoTime() - start) / 1000);
    }

    private void registerReloadCommand() {
        CommandMeta meta = proxy.getCommandManager().metaBuilder("lgbridge").plugin(this).build();
        proxy.getCommandManager().register(meta, new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (invocation.arguments().length != 1 || !invocation.arguments()[0].equalsIgnoreCase("reload")) {
                    invocation.source().sendMessage(Component.text("Usage: /lgbridge reload"));
                    return;
                }
                boolean ok = loadConfig();
                invocation.source().sendMessage(Component.text(ok
                        ? "LightningGrim bridge reloaded (" + (bridge.isEnabled() ? "enabled" : "disabled") + ")."
                        : "LightningGrim bridge config is invalid: the bridge is now disabled, check the console."));
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission(RELOAD_PERMISSION);
            }
        });
    }

    /** Returns false when the configuration could not be applied (the bridge is then disabled). */
    private boolean loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path config = dataDirectory.resolve("config.properties");
            if (Files.notExists(config)) {
                Files.writeString(config, BanBridge.DEFAULT_CONFIG);
                logger.warn("Created bridge configuration at {}; configure it before enabling the bridge.", config);
                bridge.apply(BanBridge.Settings.DISABLED);
                return true;
            }
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(config)) {
                properties.load(input);
            }
            BanBridge.ConfigResult result = BanBridge.parseSettings(toStringMap(properties));
            bridge.apply(result.settings());
            if (!result.ok()) {
                logger.error("LightningGrim bridge config is invalid and stays disabled: {}", result.error());
            }
            return result.ok();
        } catch (IOException exception) {
            bridge.apply(BanBridge.Settings.DISABLED);
            logger.error("Unable to load LightningGrim bridge configuration", exception);
            return false;
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
