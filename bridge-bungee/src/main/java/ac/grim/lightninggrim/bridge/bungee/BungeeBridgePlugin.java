package ac.grim.lightninggrim.bridge.bungee;

import ac.grim.lightninggrim.bridge.BanBridge;
import ac.grim.lightninggrim.bridge.BridgeProtocol;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class BungeeBridgePlugin extends Plugin implements Listener {
    private static final String RELOAD_PERMISSION = "lightninggrim.bridge.reload";
    private final BanBridge bridge = new BanBridge() {
        @Override
        protected boolean dispatch(String command, BridgeProtocol.BanRequest request, String backend) {
            // dispatchCommand is thread-safe on BungeeCord, so this can stay on the netty thread.
            ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), command);
            getLogger().info("Accepted LightningGrim ban request " + request.requestId() + " from " + backend
                    + " for " + request.playerName() + " (" + request.checkKey() + ", VL " + request.violations() + ")");
            return true;
        }
    };

    @Override
    public void onEnable() {
        loadConfig();
        ProxyServer.getInstance().registerChannel(BridgeProtocol.CHANNEL);
        ProxyServer.getInstance().getPluginManager().registerListener(this, this);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new Command("lgbridge", RELOAD_PERMISSION) {
            @Override
            public void execute(CommandSender sender, String[] args) {
                if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
                    sender.sendMessage(new TextComponent("Usage: /lgbridge reload"));
                    return;
                }
                boolean ok = loadConfig();
                sender.sendMessage(new TextComponent(ok
                        ? "LightningGrim bridge reloaded (" + (bridge.isEnabled() ? "enabled" : "disabled") + ")."
                        : "LightningGrim bridge config is invalid: the bridge is now disabled, check the console."));
            }
        });
        if (bridge.isEnabled()) {
            getLogger().info("LightningGrim bridge enabled for backends: " + String.join(", ", bridge.settings().allowedBackends()));
        } else {
            getLogger().warning("LightningGrim bridge is disabled; configure config.properties before accepting network bans.");
        }
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(BridgeProtocol.CHANNEL)) return;
        // Never forward bridge traffic, even while the bridge is disabled or when a client sends it.
        event.setCancelled(true);
        if (!bridge.isEnabled() || !(event.getSender() instanceof Server source)) return;
        String backend = source.getInfo().getName();
        bridge.handlePayload(backend, event.getData(), System.currentTimeMillis());
    }

    /** Returns false when the configuration could not be applied (the bridge is then disabled). */
    private boolean loadConfig() {
        try {
            Path folder = getDataFolder().toPath();
            Files.createDirectories(folder);
            Path config = folder.resolve("config.properties");
            if (Files.notExists(config)) {
                Files.writeString(config, BanBridge.DEFAULT_CONFIG);
                getLogger().warning("Created bridge configuration at " + config + "; configure it before enabling the bridge.");
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
                getLogger().warning("LightningGrim bridge config is invalid and stays disabled: " + result.error());
            }
            return result.ok();
        } catch (IOException exception) {
            bridge.apply(BanBridge.Settings.DISABLED);
            getLogger().warning("Unable to load LightningGrim bridge configuration: " + exception.getMessage());
            return false;
        }
    }

    private static Map<String, String> toStringMap(Properties properties) {
        Map<String, String> map = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return map;
    }
}
