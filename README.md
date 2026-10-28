<div align="center">

# ⚡ LightningGrimAddons

**A Minecraft anticheat based on [GrimAC](https://github.com/GrimAnticheat/Grim) and [LightningGrim](https://github.com/Axionize/LightningGrim), with authenticated network bans through your proxy.**

<a href="https://github.com/ImApon877/LightningGrimAddons/releases/latest"><img src="https://img.shields.io/github/v/release/ImApon877/LightningGrimAddons?style=for-the-badge&logo=github&label=release&color=00BFFF" alt="Latest release"></a>
<a href="https://github.com/ImApon877/LightningGrimAddons/actions/workflows/gradle-publish.yml"><img src="https://img.shields.io/github/actions/workflow/status/ImApon877/LightningGrimAddons/gradle-publish.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=build" alt="Build"></a>
<a href="LICENSE"><img src="https://img.shields.io/github/license/ImApon877/LightningGrimAddons?style=for-the-badge&color=1F2937" alt="License"></a>

<img src="https://img.shields.io/badge/platform-Paper%20%7C%20Spigot%20%7C%20Folia-1F2937?style=flat-square" alt="Paper, Spigot, Folia">
<img src="https://img.shields.io/badge/Fabric-supported-1F2937?style=flat-square" alt="Fabric">
<img src="https://img.shields.io/badge/proxy-Velocity%20%7C%20BungeeCord-1F2937?style=flat-square" alt="Velocity, BungeeCord">
<img src="https://img.shields.io/badge/Java-17%2B-1F2937?style=flat-square&logo=openjdk" alt="Java 17+">

**[📥 Download](https://github.com/ImApon877/LightningGrimAddons/releases/latest)** ·
**[🌐 Network bans guide](docs/proxy-bridge.md)** ·
**[🐛 Report a bug](https://github.com/ImApon877/LightningGrimAddons/issues)** ·
**[📚 GrimAC wiki](https://github.com/GrimAnticheat/Grim/wiki)**

</div>

---

## ✨ Highlights

| | |
| --- | --- |
| 🧠 **Movement simulation** | Predicts every legitimate movement (walking, swimming, knockback, cobwebs, bubble columns, vehicles) instead of guessing with limits. |
| ⚔️ **Combat checks** | Reach, hitboxes, **WallHit** (hits through blocks), **EntityPierce** (hits through entities) and the **PacketOrder** family against NoSlow and AutoBlock. |
| 🌐 **Network bans** | The `[proxy-ban]` punishment sends an HMAC-SHA256 signed ban request to your proxy, which bans the player on the whole network. |
| 🛡️ **Proxy bridge** | Velocity and BungeeCord plugins that accept only signed requests from allowed backends, reject expired or replayed ones, and run *your* ban command. |
| ⚙️ **Async by design** | Checks run off the main thread where safe, with per-player world replication and latency compensation. |

> [!NOTE]
> Checks such as WallHit, EntityPierce and PacketOrder come from LightningGrim. This fork adds the **network bans** and the **proxy bridges**, plus the fixes listed in [What's new](#-whats-new).

## 📥 Download

Get the files from the **[latest release](https://github.com/ImApon877/LightningGrimAddons/releases/latest)**:

| File | Install on | Folder |
| --- | --- | --- |
| `grimac-bukkit-*.jar` | Paper, Spigot, Folia | `plugins/` |
| `grimac-fabric-*.jar` | Fabric | `mods/` |
| `grimac-bridge-velocity-*.jar` | Velocity (only for network bans) | proxy `plugins/` |
| `grimac-bridge-bungee-*.jar` | BungeeCord, Waterfall (only for network bans) | proxy `plugins/` |

## 🚀 Quick start

1. Use **Java 17 or newer** (Minecraft 1.20.5 and newer need Java 21).
2. Put the jar for your platform in `plugins/` (or `mods/` on Fabric). Install **ViaVersion** on the backend when you need it, and **Floodgate** on the backend if you use Geyser.
3. Start the server once, then review the generated files in `plugins/GrimAC/` (`config.yml`, `punishments.yml`, `messages.yml`).
4. Test on a staging server before enabling punishments in production.

### 🌐 Network bans in three steps

1. **Proxy**: install the bridge for your proxy, start it once, then in its `config.properties` set `enabled=true`, a secret from `openssl rand -base64 32`, your backend names in `allowedBackends` and your `banCommand` (for example `litebans:ban %player% Cheating`). Apply it with `/lgbridge reload`.
2. **Every backend**: in `plugins/GrimAC/config.yml` set `proxy-ban.enabled: true`, the same secret and `backend-id` equal to the server name on the proxy. The backend must run in proxy mode (`bungeecord: true` in `spigot.yml`, or Velocity modern forwarding in `paper-global.yml`).
3. **Punishments**: add a command such as `- "20:40 [proxy-ban]"` to a group in `punishments.yml`.

The full guide is in **[docs/proxy-bridge.md](docs/proxy-bridge.md)**.

## 🆕 What's new

### 2.3.74-addons.3
- No more `No SLF4J providers were found` errors at startup.
- Fixed a `NullPointerException` when a player's connection closed unexpectedly, which skipped the rest of the disconnect cleanup.

<details>
<summary><b>2.3.74-addons.2</b></summary>

- **`[proxy]` alerts and `[proxy-ban]` never worked**: the proxy was never detected (reading `spigot.yml` / `paper-global.yml` always failed). Fixed.
- `[proxy-ban]` also works when the banned player is alone on the backend.
- Git history connected to LightningGrim, so upstream updates can be merged; three large classes split.

</details>

<details>
<summary><b>2.3.74-addons.1</b></summary>

- The configuration failed to load on fresh installs (an invalid line was added to `punishments.yml`). Fixed, and broken files are repaired.
- The Velocity bridge failed to load (`NoClassDefFoundError`). Fixed.
- `/lgbridge reload` on the proxy; the build works from a clean checkout and in GitHub Actions.

</details>

**Tested** on Paper 1.21.11 behind Velocity 3.5.1 with bot players: fly, speed, NoFall, timer, impossible place cursor, reach and out-of-hitbox interactions are flagged, legit movement stays well below the default alert thresholds, and a cheater alone on a backend gets a network ban accepted by the proxy.

## 🧠 How it works

<details>
<summary><b>Movement simulation engine</b></summary>

LightningGrim reproduces the possible movements of a legitimate player, including walking, swimming, knockback, cobwebs, bubble columns and riding entities. The engine accounts for client-version differences, collision order, ViaVersion replacements, waterlogged blocks and vanilla collision boxes.

</details>

<details>
<summary><b>Asynchronous and multithreaded design</b></summary>

Movement checks and most listeners run asynchronously where safe, so the anticheat scales to large servers while keeping thread safety under control.

</details>

<details>
<summary><b>World replication and latency compensation</b></summary>

Each player has a world representation built from chunk data, block placements and block changes. World updates and inventory state are compensated for latency, which reduces false positives from delayed packets, ghost blocks and server-side changes.

</details>

<details>
<summary><b>Security-focused design</b></summary>

Prediction and validation are built around legitimate client behaviour rather than obscurity. No anticheat catches every cheat: correct configuration, testing, monitoring and human review remain important.

</details>

## 🔨 Compiling from source

```bash
git clone https://github.com/ImApon877/LightningGrimAddons.git
cd LightningGrimAddons
./gradlew build
```

On Windows use `.\gradlew.bat build`. The jars are created in each module's `build/libs/` folder (`bukkit`, `fabric`, `bridge-velocity`, `bridge-bungee`).

To bring in updates from LightningGrim:

```bash
git remote add upstream https://github.com/Axionize/LightningGrim.git
git fetch upstream
git merge upstream/lightning
```

## 🤝 Contributing

Pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md), keep changes focused, preserve upstream attribution and add tests where possible.

## 🔒 Security

Please do not publicly disclose working bypasses before they can be investigated. See [SECURITY.md](SECURITY.md) for responsible disclosure.

## 📜 License and attribution

This repository contains code derived from [GrimAC](https://github.com/GrimAnticheat/Grim) and [LightningGrim](https://github.com/Axionize/LightningGrim); all original copyright notices, license files and attribution requirements are preserved. See [LICENSE](LICENSE) for the full terms.

> [!IMPORTANT]
> This is an independent, community-maintained fork. It is **not affiliated with, sponsored by, maintained by or endorsed by** the GrimAC or LightningGrim authors. GrimAC, LightningGrim and all related names, trademarks and copyrights belong to their respective owners.
>
> The software is provided without warranty. The maintainers are not responsible for server issues, false positives or misuse.
