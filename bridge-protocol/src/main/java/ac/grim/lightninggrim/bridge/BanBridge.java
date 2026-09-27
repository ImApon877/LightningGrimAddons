package ac.grim.lightninggrim.bridge;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-agnostic engine shared by the Velocity and Bungee bridge plugins:
 * config parsing/validation, replay suppression, and command dispatch.
 * The platform plugins only adapt transport (plugin message events) and
 * logging/console APIs onto this.
 */
public class BanBridge {
    /** Shared with the backend's config template so both sides document the same keys. */
    public static final String DEFAULT_CONFIG = String.join("\n",
            "enabled=false",
            "hmacSecretBase64=",
            "allowedBackends=",
            "requestTtlMs=10000",
            "replayTtlMs=60000",
            "banCommand=litebans:ban %player% Automated LightningGrim network ban",
            "");

    private static final long DEFAULT_REQUEST_TTL_MS = 10_000;
    private static final long DEFAULT_REPLAY_TTL_MS = 60_000;
    private static final long MAX_TTL_MS = 24L * 60 * 60 * 1000;

    private final ConcurrentHashMap<UUID, Long> acceptedRequests = new ConcurrentHashMap<>();
    private volatile Settings settings = Settings.DISABLED;

    /** Immutable snapshot; swapped atomically on (re)load. */
    public record Settings(boolean enabled, byte[] secret, Set<String> allowedBackends,
                           long requestTtlMs, long replayTtlMs, String banCommand) {
        public static final Settings DISABLED =
                new Settings(false, new byte[0], Set.of(), DEFAULT_REQUEST_TTL_MS, DEFAULT_REPLAY_TTL_MS, "");
    }

    /** Parse + validate a raw properties map. Never throws; an error becomes {@link ConfigResult#error}. */
    public static ConfigResult parseSettings(Map<String, String> properties) {
        boolean enabled = Boolean.parseBoolean(properties.getOrDefault("enabled", "false").trim());
        if (!enabled) return new ConfigResult(Settings.DISABLED, null);

        byte[] secret;
        try {
            secret = java.util.Base64.getDecoder().decode(properties.getOrDefault("hmacSecretBase64", "").trim());
        } catch (IllegalArgumentException e) {
            return new ConfigResult(Settings.DISABLED, "hmacSecretBase64 is not valid Base64");
        }

        Set<String> backends = new LinkedHashSet<>();
        for (String raw : properties.getOrDefault("allowedBackends", "").split(",")) {
            String id = raw.trim();
            if (id.isEmpty()) continue;
            if (!BridgeProtocol.isValidBackendId(id)) {
                return new ConfigResult(Settings.DISABLED, "allowedBackends contains an invalid id: " + id);
            }
            backends.add(id);
        }

        long requestTtlMs = parsePositive(properties.getOrDefault("requestTtlMs", ""), DEFAULT_REQUEST_TTL_MS);
        long replayTtlMs = parsePositive(properties.getOrDefault("replayTtlMs", ""), DEFAULT_REPLAY_TTL_MS);
        if (requestTtlMs <= 0 || requestTtlMs > MAX_TTL_MS) {
            return new ConfigResult(Settings.DISABLED, "requestTtlMs must be between 1 and " + MAX_TTL_MS);
        }
        if (replayTtlMs < requestTtlMs || replayTtlMs > MAX_TTL_MS) {
            return new ConfigResult(Settings.DISABLED, "replayTtlMs must be >= requestTtlMs and <= " + MAX_TTL_MS);
        }

        String banCommand = properties.getOrDefault("banCommand", "").trim();
        if (banCommand.isEmpty() || !banCommand.contains("%player%")) {
            return new ConfigResult(Settings.DISABLED, "banCommand must be set and contain %player%");
        }

        if (secret.length < 32) {
            return new ConfigResult(Settings.DISABLED,
                    "hmacSecretBase64 must decode to at least 32 bytes (generate one with: openssl rand -base64 32)");
        }
        if (backends.isEmpty()) {
            return new ConfigResult(Settings.DISABLED, "allowedBackends must list at least one backend id");
        }

        return new ConfigResult(new Settings(true, secret, Set.copyOf(backends), requestTtlMs, replayTtlMs, banCommand), null);
    }

    private static long parsePositive(String raw, long fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return -1; // rejected by the range checks above
        }
    }

    public record ConfigResult(Settings settings, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /** Swap in newly loaded settings (also used by reloads). */
    public void apply(Settings newSettings) {
        this.settings = newSettings == null ? Settings.DISABLED : newSettings;
    }

    public Settings settings() {
        return settings;
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    /**
     * Verify and act on one payload. Returns true if a ban command was
     * dispatched; false for disabled/unknown backend/malformed/expired/replayed
     * requests. Command dispatch runs on the calling thread; the platform
     * plugins decide whether that is their event thread or an async worker.
     */
    public boolean handlePayload(String backend, byte[] data, long now) {
        Settings current = settings;
        if (!current.enabled() || !current.allowedBackends().contains(backend)) return false;
        if (data == null || data.length > BridgeProtocol.MAX_PAYLOAD_BYTES) return false;

        BridgeProtocol.BanRequest request;
        try {
            request = BridgeProtocol.decode(data, current.secret(), now);
        } catch (Exception malformed) {
            return false;
        }

        long issuedAt = request.issuedAt();
        if (!request.backendId().equals(backend)
                || !BridgeProtocol.isValidPlayerName(request.playerName())
                || issuedAt > now || now - issuedAt > current.requestTtlMs()) {
            return false;
        }

        evictExpired(now, current);
        if (acceptedRequests.putIfAbsent(request.requestId(), now) != null) return false;

        return dispatch(current.banCommand().replace("%player%", request.playerName()), request, backend);
    }

    /** Platform hook: run the command as console. Overridden by the proxy plugins and tests. */
    protected boolean dispatch(String command, BridgeProtocol.BanRequest request, String backend) {
        return true;
    }

    /** Drop replay-cache entries older than the replay window. Returns the remaining count. */
    public int evictExpired(long now) {
        return evictExpired(now, settings);
    }

    private int evictExpired(long now, Settings current) {
        acceptedRequests.entrySet().removeIf(entry -> now - entry.getValue() > current.replayTtlMs());
        return acceptedRequests.size();
    }

    /** Exposed for tests. */
    int trackedRequests() {
        return acceptedRequests.size();
    }
}
