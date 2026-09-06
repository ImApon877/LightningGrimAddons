# LightningGrim

<div align="center">

<strong>An independent fork of GrimAC and LightningGrim</strong>

<br><br>

<a href="https://github.com/ImApon877/LightningGrimAddons/actions">
  <img src="https://img.shields.io/github/actions/workflow/status/ImApon877/LightningGrimAddons/build-and-publish.yml?style=flat&logo=github" alt="GitHub Actions">
</a>

<a href="https://github.com/ImApon877/LightningGrimAddons/releases">
  <img src="https://img.shields.io/github/v/release/ImApon877/LightningGrimAddons?style=flat&logo=github" alt="GitHub Release">
</a>

<a href="https://github.com/ImApon877/LightningGrimAddons/issues">
  <img src="https://img.shields.io/github/issues/ImApon877/LightningGrimAddons?style=flat&logo=github" alt="Issues">
</a>

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
