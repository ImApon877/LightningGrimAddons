# Proxy Ban Bridge

Install exactly one bridge jar: `bridge-velocity` on Velocity, or `bridge-bungee` on BungeeCord/Waterfall. The bridge accepts only signed ban requests from allowed backends and executes the local `banCommand`; it never accepts a command from a backend payload.

On first startup it writes `plugins/LightningGrimBridge/config.properties`:

```properties
enabled=false
hmacSecretBase64=
allowedBackends=survival,skyblock
requestTtlMs=10000
replayTtlMs=60000
banCommand=litebans:ban %player% Automated LightningGrim network ban
```

Generate a secret with `openssl rand -base64 32`. Set that same value under `proxy-ban.hmac-secret-base64` in every backend's LightningGrim `config.yml`. Set `proxy-ban.enabled: true`, set each `proxy-ban.backend-id` to its proxy server name, and list that name in `allowedBackends`.

To request a ban, add `[proxy-ban]` to a punishment command such as `100:0 [proxy-ban]`. It is disabled by default (enable it per punishment group in `punishments.yml`).

Requests are rejected for unknown backends, expired timestamps (`requestTtlMs`), invalid signatures, duplicate IDs (within `replayTtlMs`), malformed payloads, or timestamps drifting more than 24 hours from the proxy clock. Backend IDs accept letters, digits, and the `_ - . /` symbols only; player names must be valid Minecraft names, so the substituted `%player%` can never inject console commands. One ban command runs at most once per request ID even if the payload is replayed.
