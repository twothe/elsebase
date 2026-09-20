# Development foundation

## Scope and provenance

Bootstrap prepared on 2026-09-19. No dimension, blocks, items, recipes, portals, structural editing, networking or renderer have been implemented. No gameplay configuration is invented before its contract is agreed.

The original design file is preserved byte-for-byte. Its initial SHA-256 is `20C600A94C9B8585DFF156F8A8949B4B5C4A3307738C12924467C5F59922872D`.

Source: [official Minecraft 1.21.1 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tree/16ba48426ca291b984f5b36cd5caaa93a0a776eb), commit `16ba48426ca291b984f5b36cd5caaa93a0a776eb`.
The wrapper and template license were copied from this revision. Build configuration was reduced to the relevant development tasks; example gameplay and publishing configuration were omitted.

The supplied project directory was not a Git repository. No remote repository or initial commit has been created. Ignore rules are ready for later version control.

The downloaded upstream checkout and inspected source extracts remain in ignored `.setup/`: the environment blocked the attempted temporary-directory cleanup. They are not build inputs or packaged mod content.

| Component | Pinned value |
| --- | --- |
| Minecraft | 1.21.1, exact runtime dependency |
| NeoForge | 21.1.250 |
| ModDevGradle | 2.0.147 |
| Gradle wrapper | 9.2.1 |
| Java compilation/runtime target | 21 |
| Parchment | Minecraft 1.21.1 / mappings 2024.11.17 |
| Foojay resolver plugin | 1.0.0 |
| Resolved FML loader | 4.0.44; metadata permits major version 4 |
| Mod ID | `backroom_industry` |
| Java package / Maven group | `dev.backroomindustry` (provisional) |
| Development version | `0.1.0-SNAPSHOT` |

Use pinned versions for reproducibility. A future upgrade needs its own build and runtime verification.

## Local environment

The initial machine configuration has `java` on PATH at Java 21.0.2, while `JAVA_HOME` points to Java 24.0.1. The setup uses the existing JDK 21.0.6 explicitly for its process; global environment settings are not changed.

PowerShell setup used for verification:

```powershell
$env:JAVA_HOME = 'C:\Work\Java\GraalVM\jdk-21.0.6'
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
.\gradlew.bat --version
.\gradlew.bat build
```

The local Gradle cache is ignored by Git and keeps downloaded dependencies inside this workspace. Reuse this `GRADLE_USER_HOME` in later terminal/IDE sessions to avoid downloading them again. Other developers can use a normal Gradle user home and their own JDK 21 location.

Import `build.gradle` as a Gradle project in the IDE. Select the wrapper and JDK 21 as the Gradle JVM. Java toolchains also constrain compilation to Java 21. Do not commit absolute machine-specific Java paths.

## Commands

| Command | Purpose |
| --- | --- |
| `.\gradlew.bat build` | Compile, process metadata, run available checks and produce the development JAR |
| `.\gradlew.bat runClient` | Start the development client with this mod |
| `.\gradlew.bat runServer` | Start a dedicated development server without a GUI |
| `.\gradlew.bat runData` | Run NeoForge data generation; no custom providers exist yet |
| `.\gradlew.bat runGameTestServer` | Reserved for future GameTests; no tests are registered yet |
| `.\gradlew.bat tasks --all` | Inspect available development tasks |

On Unix-like systems use `./gradlew` instead of `.\gradlew.bat`.

A dedicated server requires the operator to accept Minecraft's EULA in the generated run directory. No EULA acceptance is preconfigured. Generated worlds, logs and runtime configuration are development data and must remain untracked.

## Verification status

- `gradlew --version`: Gradle 9.2.1, launcher and daemon using Java 21.0.6.
- `gradlew build`: successful, including resolved/decompiled/patched Minecraft and NeoForge sources, Java compilation and JAR packaging.
- Inspected the JAR: it contains `BackroomIndustry.class`, expanded `META-INF/neoforge.mods.toml` and the template license, with no example gameplay or unresolved metadata placeholders.
- `javap -verbose`: compiled class major version 65 (Java 21).
- Copied wrapper JAR SHA-256 matches the pinned MDK copy: `7D3A4AC4DE1C32B59BC6A4EB8ECB8E612CCD0CF1AE1E99F66902DA64DF296172`.
- `gradlew runData`: successful. NeoForge discovered `Backroom Industry 0.1.0-SNAPSHOT`, then executed the entry point and logged `Backroom Industry bootstrap loaded; gameplay is not implemented yet.` No custom data providers ran, as expected.
- The data run downloaded the Minecraft assets, so dependencies, game sources and assets are locally available. Its non-fatal warnings concerned unavailable advanced terminal features and the development launcher's `union:` asset URL scheme; there were no mod-loading errors.
- Original design SHA-256 still matches after setup.

Not verified: interactive client rendering, joining/playing in a world, dedicated server world startup, multiplayer, performance-mod compatibility and GameTests. The data launch is evidence of mod discovery/initialization, not of those workflows.

API inspection used the resolved sources, not a different Minecraft release: `ClientPacketListener`, `ClientboundLevelChunkWithLightPacket`, `DistanceManager`, `TicketController`, `ForcedChunkManager` and `ChunkEvent`. Findings are recorded in D01/D02 of the design discussion. Minecraft/NeoForge source artifacts are available through the IDE and the ignored Gradle cache; do not vendor game sources into the repository.

No automated gameplay tests exist because there is no gameplay implementation. A successful build alone must not be reported as proof of working portals, multiplayer compatibility or rendering.

## Sources

- [NeoForge 1.21.1 getting started](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)
- [ModDevGradle documentation](https://docs.neoforged.net/toolchain/docs/plugins/mdg/)
- [Pinned MDK revision](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tree/16ba48426ca291b984f5b36cd5caaa93a0a776eb)
