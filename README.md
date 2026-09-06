<div align="center">

&#x20;<h1>LightningGrim</h1>



&#x20;<p>

&#x20; <strong>An independent fork of GrimAC and LightningGrim</strong>

&#x20;</p>



&#x20;<div>

&#x20; <a href="https://github.com/ImApon877/LightningGrim/actions">

&#x20;  <img alt="GitHub Actions" src="https://img.shields.io/github/actions/workflow/status/ImApon877/LightningGrim/build-and-publish.yml?style=flat\&logo=github"/>

&#x20; </a>\&nbsp;\&nbsp;

&#x20; <a href="https://github.com/ImApon877/LightningGrim/releases">

&#x20;  <img alt="GitHub release" src="https://img.shields.io/github/v/release/ImApon877/LightningGrim?style=flat\&logo=github"/>

&#x20; </a>\&nbsp;\&nbsp;

&#x20; <a href="https://github.com/ImApon877/LightningGrim/issues">

&#x20;  <img alt="Issues" src="https://img.shields.io/github/issues/ImApon877/LightningGrim?style=flat\&logo=github"/>

&#x20; </a>

&#x20;</div>

&#x20;<br>

</div>



LightningGrim is an open-source Minecraft anticheat based on GrimAC and LightningGrim. This fork focuses on performance improvements, more accurate reach and interaction checks, packet-order validation, and additional staff and proxy integrations.



Important disclaimer



This repository is an independent, community-maintained fork. It is not affiliated with, sponsored by, maintained by, or officially endorsed by the GrimAC or LightningGrim authors. GrimAC, LightningGrim, and related names and trademarks belong to their respective owners.



Changes in this fork



WallHit: detects attacks through blocks.



EntityPierce: detects attacks through other entities.



Optimised reach, hitbox, and collision calculations.



Bukkit piston-event optimisations.



Packet-order checks for NoSlow, AutoBlock, and similar exploits.



Inventory-interaction validation.



Discord webhook and punishment integrations.



Violation history and staff-oriented commands.



BungeeCord and Velocity bridge modules.



Additional compatibility and performance fixes.



Downloads



Releases and development builds will be published on the GitHub Releases page.



Only download builds from sources you trust. Always test a new build on a staging server before using it in production.



Requirements and installation



Java 17 or newer.



A supported Paper, Spigot, Folia, or Fabric server environment.



ViaVersion installed on the backend server when required. ViaVersion should not be installed only on the proxy.



If using Geyser, Floodgate must be installed on the backend server so the anticheat can access its API.



Place the appropriate platform JAR in the server's plugins or mods directory, start the server once, and review the generated configuration before enabling punishments.



Resources



Original GrimAC repository



Original LightningGrim repository



GrimAC Wiki



GrimAPI



Issues and bug reports



When opening an issue, include the Minecraft version, server software, ViaVersion version, logs, and clear reproduction steps.



Pull requests



Pull requests are welcome. Please read CONTRIBUTING.md, keep changes focused, preserve upstream attribution, and add tests where possible.



Compiling from source



git clone https://github.com/ImApon877/LightningGrim.git

cd LightningGrim

./gradlew build



On Windows:



git clone https://github.com/ImApon877/LightningGrim.git

cd LightningGrim

.\\gradlew.bat build



The resulting JAR files are generated in the individual <platform>/build/libs directories.



Core technology



Movement simulation engine



LightningGrim uses a detailed movement simulation engine designed to reproduce the possible movements of a legitimate player, including walking, swimming, knockback, cobwebs, bubble columns, and riding entities.



The engine accounts for client-version differences, collision order, ViaVersion replacements, waterlogged blocks, and vanilla collision boxes.



Asynchronous and multithreaded design



Movement checks and most listeners run asynchronously where safe, allowing the anticheat to scale to large servers while maintaining carefully designed thread safety.



World replication



The anticheat maintains a per-player world representation based on chunk data, block placements, and block changes. This allows safe asynchronous access, lag compensation, and correct handling of client-sided or packet-based blocks.



Latency and inventory compensation



World updates and inventory state are tracked and compensated for latency, reducing false positives caused by delayed packets, ghost blocks, or server-side changes that have not yet reached the client.



Security-focused design



The prediction and validation systems are designed around the complete set of legitimate client behaviours rather than relying on obscurity. No anticheat can guarantee detection of every cheat, so configuration, testing, and human review remain important.



Security



Please do not publicly disclose working bypasses before they can be investigated. See SECURITY.md for responsible disclosure information.



License and attribution



This repository contains code derived from GrimAC and LightningGrim. All original copyright notices, license files, and attribution requirements must be preserved. See LICENSE for the complete license terms.



This project is provided without warranty. The maintainers are not responsible for server issues, false positives, or misuse of the software.

