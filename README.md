# LightningGrim

<div align="center">

<strong>An independent fork of GrimAC and LightningGrim</strong>

<br><br>

<a href="https://github.com/ImApon877/LightningGrimAddons/actions"><img src="https://img.shields.io/github/actions/workflow/status/ImApon877/LightningGrimAddons/build-and-publish.yml?style=flat&logo=github" alt="GitHub Actions"></a>
<a href="https://github.com/ImApon877/LightningGrimAddons/releases"><img src="https://img.shields.io/github/v/release/ImApon877/LightningGrimAddons?style=flat&logo=github" alt="GitHub Release"></a>
<a href="https://github.com/ImApon877/LightningGrimAddons/issues"><img src="https://img.shields.io/github/issues/ImApon877/LightningGrimAddons?style=flat&logo=github" alt="Issues"></a>

</div>

LightningGrim is an open-source Minecraft anticheat based on both [GrimAC](https://github.com/GrimAnticheat/Grim) and [LightningGrim](https://github.com/Axionize/LightningGrim).

This fork focuses on performance improvements, reach accuracy, block placement checks, interaction checks, packet-order validation, inventory protection, and additional staff and proxy integrations.

> **Important disclaimer**
>
> This repository is an independent, community-maintained fork.
>
> This project is **not affiliated with, sponsored by, maintained by, or officially endorsed by** the GrimAC or LightningGrim authors.
>
> GrimAC, LightningGrim, and all related names, trademarks, and copyrights belong to their respective owners.

## Changes in this fork

- **WallHit** – Detects attacks through blocks.
- **EntityPierce** – Detects attacks through other entities.
- Optimised reach, hitbox, and collision calculations.
- Bukkit piston-event optimisations.
- Packet-order checks for NoSlow, AutoBlock, and similar exploits.
- Inventory-interaction validation.
- Discord webhook and punishment integrations.
- Violation history and staff commands.
- BungeeCord and Velocity bridge modules.
- Additional compatibility and performance improvements.

## Downloads

Releases and development builds will be published on the [GitHub Releases page](https://github.com/ImApon877/LightningGrimAddons/releases).

Always test a new build on a staging server before using it in production.

## Requirements and installation

- Java 17 or newer.
- A supported Paper, Spigot, Folia, or Fabric server environment.
- ViaVersion installed on the backend server when required.
- If using Geyser, Floodgate must be installed on the backend server.

Place the appropriate platform JAR in the server's `plugins` or `mods` directory, start the server once, and review the generated configuration before enabling punishments.

## Resources

- [Original GrimAC repository](https://github.com/GrimAnticheat/Grim)
- [Original LightningGrim repository](https://github.com/Axionize/LightningGrim)
- [GrimAC Wiki](https://github.com/GrimAnticheat/Grim/wiki)
- [GrimAPI](https://github.com/GrimAnticheat/GrimAPI)
- [Issues and bug reports](https://github.com/ImApon877/LightningGrimAddons/issues)

## Pull requests

Pull requests are welcome.

Before contributing, read [CONTRIBUTING.md](CONTRIBUTING.md), keep changes focused, preserve upstream attribution, and add tests where possible.

## Compiling from source

```bash
git clone https://github.com/ImApon877/LightningGrimAddons.git
cd LightningGrimAddons
./gradlew build

On Windows:

git clone https://github.com/ImApon877/LightningGrimAddons.git
cd LightningGrimAddons
.\gradlew.bat build

The resulting JAR files are generated inside the individual <platform>/build/libs directories.

Core technology
Movement simulation engine

LightningGrim uses a detailed movement simulation engine designed to reproduce the possible movements of a legitimate player, including walking, swimming, knockback, cobwebs, bubble columns, and riding entities.

The engine accounts for client-version differences, collision order, ViaVersion replacements, waterlogged blocks, and vanilla collision boxes.

Asynchronous and multithreaded design

Movement checks and most listeners run asynchronously where safe, allowing the anticheat to scale to large servers while maintaining carefully controlled thread safety.

World replication

The anticheat maintains a per-player world representation based on chunk data, block placements, and block changes.

This allows safe asynchronous access, latency compensation, and correct handling of client-sided or packet-based blocks.

Latency and inventory compensation

World updates and inventory state are tracked and compensated for latency, reducing false positives caused by delayed packets, ghost blocks, or server-side changes.

Security-focused design

The prediction and validation systems are designed around legitimate client behaviour rather than relying on obscurity.

No anticheat can guarantee detection of every cheat or exploit. Correct configuration, testing, monitoring, and human review remain important.

Security

Please do not publicly disclose working bypasses before they can be investigated.

See SECURITY.md for responsible disclosure information.

License and attribution

This repository contains code derived from GrimAC and LightningGrim.

All original copyright notices, license files, and attribution requirements must be preserved.

See LICENSE for the complete license terms.

This project is provided without warranty. The maintainers are not responsible for server issues, false positives, or misuse of the software.
