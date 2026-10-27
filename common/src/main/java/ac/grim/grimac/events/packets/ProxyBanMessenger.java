package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.lightninggrim.bridge.BridgeProtocol;

import java.util.Base64;
import java.util.UUID;

public final class ProxyBanMessenger {
    private ProxyBanMessenger() {}

    public static void send(GrimPlayer player, Check check, int violations) {
        var config = GrimAPI.INSTANCE.getConfigManager().getConfig();
        if (!ProxyAlertMessenger.isUsingProxy() || !config.getBooleanElse("proxy-ban.enabled", false)) return;

        String secret = config.getStringElse("proxy-ban.hmac-secret-base64", "");
        String backendId = config.getStringElse("proxy-ban.backend-id", "");
        if (secret.isBlank() || backendId.isBlank()) {
            LogUtil.warn("Proxy ban skipped: set proxy-ban.hmac-secret-base64 and proxy-ban.backend-id in config.yml.");
            return;
        }
        if (!BridgeProtocol.isValidBackendId(backendId)) {
            LogUtil.warn("Proxy ban skipped: proxy-ban.backend-id contains invalid characters (allowed: letters, digits, '_', '-', '.', '/', ' ').");
            return;
        }

        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret.trim());
            if (key.length < 32) throw new IllegalArgumentException("HMAC key is too short");
        } catch (IllegalArgumentException exception) {
            LogUtil.error("Proxy ban skipped: proxy-ban.hmac-secret-base64 is not valid Base64 (openssl rand -base64 32)", exception);
            return;
        }

        // The request travels backend -> proxy on a player's server connection: the client never sees or
        // forwards it (the proxy plugins consume the channel), so the target's own connection is a safe
        // carrier. Using only other players skipped every ban while the cheater was alone on the server.
        PlatformPlayer carrier = player.platformPlayer;
        if (carrier == null) {
            for (PlatformPlayer candidate : GrimAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers()) {
                if (candidate.getUniqueId() != null) {
                    carrier = candidate;
                    break;
                }
            }
        }
        if (carrier == null) {
            LogUtil.warn("Proxy ban for " + player.getName() + " skipped: no online player connection to the proxy.");
            return;
        }

        try {
            BridgeProtocol.BanRequest request = new BridgeProtocol.BanRequest(
                    UUID.randomUUID(), System.currentTimeMillis(), backendId, player.getUniqueId(),
                    player.getName(), check.getStableKey(), violations
            );
            carrier.sendPluginMessage(BridgeProtocol.CHANNEL, BridgeProtocol.encode(request, key));
        } catch (Exception exception) {
            LogUtil.error("Proxy ban request failed to encode or send", exception);
        }
    }
}
