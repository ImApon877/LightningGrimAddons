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
        if (secret.isBlank() || backendId.isBlank() || GrimAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers().isEmpty()) {
            LogUtil.warn("Proxy ban skipped: configure proxy-ban and ensure a player is online to carry the message.");
            return;
        }

        try {
            byte[] key = Base64.getDecoder().decode(secret);
            if (key.length < 32) throw new IllegalArgumentException("HMAC key is too short");
            BridgeProtocol.BanRequest request = new BridgeProtocol.BanRequest(
                    UUID.randomUUID(), System.currentTimeMillis(), backendId, player.getUniqueId(),
                    player.getName(), check.getStableKey(), violations
            );
            PlatformPlayer carrier = GrimAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers().iterator().next();
            carrier.sendPluginMessage(BridgeProtocol.CHANNEL, BridgeProtocol.encode(request, key));
        } catch (Exception exception) {
            LogUtil.error("Proxy ban request was rejected before sending", exception);
        }
    }
}
