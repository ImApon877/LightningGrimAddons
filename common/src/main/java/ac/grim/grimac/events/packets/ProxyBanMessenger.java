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

        var onlinePlayers = GrimAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers();
        PlatformPlayer carrier = null;
        for (PlatformPlayer candidate : onlinePlayers) {
            UUID uuid = candidate.getUniqueId();
            if (uuid != null && !uuid.equals(player.getUniqueId())) {
                carrier = candidate;
                break;
            }
        }
        if (carrier == null) {
            // The ban request piggybacks on a player connection; with only the
            // cheater online there is no trustworthy carrier. The request will
            // be retried naturally if more players join and the cheater flags again.
            LogUtil.warn("Proxy ban for " + player.getName()
                    + " skipped: no suitable online carrier (a player other than the target must be online).");
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
