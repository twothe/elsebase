# Pre-1.0 audit — 2026-09-21

Scope: portal placement/travel/expiry, chunk tickets, structural permissions, template parsing/imports/persistence/synchronization, client rendering lifecycle, generated resources and packaging. This is a code audit and stabilization pass, not a published 1.0 release. The artifact version remains `0.1.0-SNAPSHOT`.

## Findings and corrections

| Priority | Proven failure path | Correction and regression |
| --- | --- | --- |
| P1 | Vanilla NBT getters return empty lists or zero coordinates for missing/wrongly typed fields. Existing save loaders could silently discard reservations or appearance data from a damaged file. | Shared strict `SavedFields` reads, duplicate record checks and allocation-grid validation. A GameTest removes required fields, supplies wrong list types and corrupts coordinates; all are rejected. Supported intact saves still round-trip. |
| P1 | A fresh portal preview could reuse server delivery acknowledgments after the client had lost historical column styles. Snapshot geometry returned, but the styles were never resent. | Invalidate only the new preview's bounded column footprint; a new chunk watch also renews delivery. Client eviction retains currently installed chunks and the active preview. The actual-client regression clears column styles before resource reload and checks that the custom yellow floor returns. |
| P2 | German source strings were already double-encoded in the resource generator. Regeneration reproduced broken umlauts. | Correct generator and generated language JSON. `verifyReleaseJar` checks a representative decoded German tooltip in the actual artifact. |
| P2 | Development tests and cursor/audio test mixins were shipped in the mod, with the common entry point directly referencing test registration. | Test registration now belongs to its own development subscriber. JAR exclusions remove test classes, mixins and fixture data; build verifies release contents. Development runs continue to discover all GameTests. |

## Architecture and performance

- Preserve the existing separation of travel, previews and server-owned themes. No world-renderer swapping or new gameplay dependencies were introduced.
- A shared surface resolver now handles both whole-column synchronization and single-block scan lookups. A scanned source block previously built 96 surface IDs; it now resolves only its own surface. Tests compare all 96 single-surface results with the existing whole-column contract, including independent slab faces.
- Keep validation at persistence boundaries explicit rather than relying on tolerant vanilla getters. Failures remain visible and do not reset saved registries.
- Keep release-fixture isolation in the build, with a check of the actual deliverable. Test source remains next to the implementation for the existing NeoForge development workflow.
- The suspected 32 KiB import transport issue was not confirmed: the resolved NeoForge custom-payload codec path uses the registered codec; vanilla's 32 KiB discarded-payload fallback is not the handler for these registered messages. No speculative fragmentation layer was added.

## Verification

- `gradlew build runGameTestServer`: domain tests, artifact checks and all **25** GameTests passed.
- `tools/check-portals.ps1`: vanilla Fancy client passed, including lost-style reacquisition, model quarantine/reload, actual portal pixels and both transition screens.
- `tools/check-templates.ps1 -Profile iris-active`: actual room/menu pixels, reentry, deletion, theme materials and lifecycle checks passed with the pinned active Iris shader fixture.
- Graphical fixtures remain silent and do not capture the desktop cursor.
- `tools/check-release-jar.py`: loads only the packaged Elsebase JAR in an isolated dedicated-server process, saves/stops, then reopens the same world. Uses the pinned resolved development runtime, not a production NeoForge installer. Logs live in `build/release-jar-check/`.

Repeat from the repository with Java 21 in `JAVA_HOME`:

```powershell
./gradlew.bat build runGameTestServer
python tools/check-release-jar.py
./tools/check-portals.ps1
./tools/check-templates.ps1 -Profile iris-active
```

The packaged-server check binds only loopback on an automatically chosen port. It never modifies an EULA file or a user's test world. Worlds, downloaded compatibility fixtures and logs remain in ignored `build/`.

## Before publishing 1.0

- Select the distribution license deliberately; metadata currently says `All Rights Reserved`, as in the development builds.
- Set the final version and release metadata, prepare release notes and choose the publication destination. Logo exports can be completed separately; their absence does not affect gameplay.
- Test the intended modpack on a real dedicated server with at least two independent clients, including its claims mod. Integrated-client tests and a headless packaged-server restart do not establish TCP multiplayer, arbitrary protection-mod compatibility or 100-player performance.
- No new save-format migration is introduced. Strict reads accept intact current formats (world 4, templates 2) and reject damaged/incompatible data. After 1.0, future format changes need an explicit compatibility policy.

No unresolved release-blocking defect was found within the exercised scope. That conclusion is bounded by the integration gaps above; it is not a blanket modpack-compatibility guarantee.
