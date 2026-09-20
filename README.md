# Backroom Industry

Minecraft **1.21.1**, **NeoForge**, **Java 21**.

This workspace currently contains the development foundation only. Gameplay implementation requires explicit approval after the design discussion.

- [Original proposal](docs/backroom-industry-design.md)
- [Open decisions and technical findings](docs/design-discussion.md)
- [Setup, commands and verification](docs/development.md)
- [Project instructions](AGENTS.md)

With `JAVA_HOME` pointing to a JDK 21 installation:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

The built mod is written to `build/libs/backroom_industry-0.1.0-SNAPSHOT.jar`.
The mod's distribution license is undecided; metadata retains `All Rights Reserved`.
The upstream MDK's separate license is preserved in `TEMPLATE_LICENSE.txt`.
