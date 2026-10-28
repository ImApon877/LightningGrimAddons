# Proxy Ban Bridge

Install exactly one bridge jar: `bridge-velocity` on Velocity, or `bridge-bungee` on BungeeCord/Waterfall. The bridge accepts only signed ban requests from allowed backends and executes the local `banCommand`; it never accepts a command from a backend payload.

On first startup it writes `config.properties` in `plugins/lightninggrim-bridge/` (Velocity) or `plugins/LightningGrimBridge/` (BungeeCord/Waterfall):

```properties
enabled=false
hmacSecretBase64=
allowedBackends=survival,skyblock
requestTtlMs=10000
replayTtlMs=60000
banCommand=litebans:ban %player% Automated LightningGrim network ban
```

Generate a secret with `openssl rand -base64 32`. Set that same value under `proxy-ban.hmac-secret-base64` in every backend's LightningGrim `config.yml`. Set `proxy-ban.enabled: true`, set each `proxy-ban.backend-id` to its proxy server name, and list that name in `allowedBackends`.

Each backend must also be configured as running behind a proxy, otherwise LightningGrim never sends the request: `settings.bungeecord: true` in `spigot.yml` (BungeeCord/legacy forwarding) or `proxies.velocity.enabled: true` in `config/paper-global.yml` (Velocity modern forwarding). On startup LightningGrim logs `Registering an outgoing plugin channel...` when it detected the proxy.

To request a ban, add `[proxy-ban]` to a punishment command such as `100:0 [proxy-ban]`. It is disabled by default (enable it per punishment group in `punishments.yml`).

Requests are rejected for unknown backends, expired timestamps (`requestTtlMs`), invalid signatures, duplicate IDs (within `replayTtlMs`), malformed payloads, or timestamps drifting more than 24 hours from the proxy clock. Backend IDs accept letters, digits, and the `_ - . /` symbols only; player names must be valid Minecraft names, so the substituted `%player%` can never inject console commands. One ban command runs at most once per request ID even if the payload is replayed.

The request is sent on the banned player's own connection, so it works even when that player is alone on the backend.

After editing `config.properties`, run `/lgbridge reload` on the proxy (permission `lightninggrim.bridge.reload`). An invalid configuration disables the bridge and the reason is printed to the proxy console.
