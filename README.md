# Elsebase

Minecraft **1.21.1**, **NeoForge**, **Java 21**.

A permanent workspace beyond the world, reached through movable doorways. First playable development build with animated, translucent portal surfaces.

Press **F** to summon/recall your personal portal. Craft reusable anchor and portal-linking tools, plus separate removal and creation tools. Look at a wall, floor or ceiling: a translucent preview shows the selection, and **right-click** applies the tool. No structural modes remain. Tooltips explain the controls. Quiet Workshop is the default, with six optional bundled resource-pack themes.

- [Player guide and recipes](docs/player-guide.md)
- [Implementation, configuration and skin contract](docs/implementation.md)

- [Original proposal](docs/backroom-industry-design.md)
- [Open decisions and technical findings](docs/design-discussion.md)
- [Setup, commands and verification](docs/development.md)
- [Project instructions](AGENTS.md)

With `JAVA_HOME` pointing to a JDK 21 installation:

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

The built mod is written to `build/libs/elsebase-0.1.0-SNAPSHOT.jar`; install it on both client and server with NeoForge 21.1.250. Build includes domain tests. GameTests use isolated `build/gametest-v4` data. Regenerate checked-in resources using `node tools/generate-resources.mjs`.

The seven themes use vanilla textures with custom doorway, panel and tool models. F overlaps vanilla swap-hands; adjust Controls, and reset existing Elsebase bindings to adopt F. New generation has 16 stacked room levels within 128 blocks, at least one exit per room and a bedrock bottom. Reference carpets load their chunk while the owner is online and guard their supporting floor. Worlds from the preceding double-wall build remain usable (save version 4); older development formats require a new world. Live portal views and final custom artwork/audio remain follow-up work.
Configure via **Mods → Elsebase → Config** or `config/elsebase-common.toml`. Rooms have independent borders, double walls and 2×2 passages; portals stay 1×2. Default brightness uses no generated lamps. Instant portals close after one minute of inactivity outside the Backdoor; they remain available while their owner is inside.

The mod's distribution license is undecided; metadata retains `All Rights Reserved`.
The upstream MDK's separate license is preserved in `TEMPLATE_LICENSE.txt`.
