# Development and verification

## First-login theme readiness — unreleased 1.1.0

The ordinary fresh-world run passed both with and without Sodium; the race required controlling delivery order to reproduce reliably. The expanded template fixture withholds only the first starter-column appearance packet until Sodium is about to upload its initial unthemed mesh, then delivers the actual packet and processes the production rebuild queue while the section is still unbuilt. The original implementation failed the visible-pixel assertion with **zero red pixels**, despite all client definitions and the column assignment being present. The corrected queue produced **102,318 red pixels** under the same ordering. Before/after screenshots were visually inspected. See [appearance delivery](surface-templates.md#appearance-delivery-during-travel) for the exact source/API trace and bounded scheduling contract.

Do not overlap Gradle development launches/compilation in this checkout. An initial combined verification overlapped another client's compilation and failed with `ClassNotFoundException: TemplateServer`; the class was rewritten one second after the running GameTest JVM tried to load it. Isolated world directories do not isolate the shared exploded class output. Subsequent verification must run sequentially.

Final verification: `build` (domain checks, all eight catalogs and fixture-free release packaging) and all **29 GameTests** passed sequentially. All three actual-client template profiles passed: vanilla, Sodium 0.6.13/Iris 1.8.12 without shaders, and the same pair with the authored active shaderpack. Checks cover direct first login, deterministic late-theme ordering in Sodium, actual pixels, live overwrites, slab face independence, dimension reentry and tool menus. Audio stayed muted and the cursor remained free. This verifies the installed compatibility fixtures, not arbitrary third-party shaderpacks or every Sodium release.

The packaged 1.1.0 JAR also passed both dedicated-server launches, world save/reload and clean shutdown. World/template formats and the unreleased version number remain unchanged; existing worlds need no regeneration.

## Blocked anchor recovery — unreleased 1.1.0

The regression test first failed on the original outside summon gate (`Occupied marker must not prevent an outside summon`). After introducing shared `AnchorArrival`, `build` and all **29 GameTests** passed. New coverage exercises actual portal entry around an occupied marker, preservation of nearby obstructions, unchanged saved anchor, head-only and crouched-entry standing clearance, a completely sealed search area, three-block emergency clearing, claim denial with full chest inventory rollback, foreign saved anchor protection, unchanged adjacent construction and absence of duplicate inventory drops. Normal structural block-entity exclusion is also asserted.

The first combined run exposed test contention on the production two-arrivals-per-tick cap: the new traversal fixture now runs at tick 10, separate from existing traversal fixtures at ticks 0 and 5. No runtime traffic limit was weakened. The owner explicitly froze version 1.1.0; world/template formats remain unchanged.

The final packaged JAR also passed both dedicated-server startup/save/reload/shutdown checks. All eight locale catalogs passed the build's verification. No configuration controls or client renderer changed in this fix, so the graphical config/render fixtures were not rerun. Third-party protection callbacks and unusual modded block-removal side effects still require pack-specific testing.

## 1.1.0 portal policies — 2026-09-24

`build` (domain checks, all eight localization catalogs and packaged-JAR verification) and all **27 GameTests** passed after the final respawn-support refinement. The new tests cover attack classification, queued summon denial, expiration without fire/poison refresh, disabled lock, logout state retention, Backdoor login, repeated login, independent known-return policy, outside summons, saved returns without a pair, missing respawn recovery, valid alternate spawn preservation and support repair even for a nominally valid forced spawn.

`tools/check-config.ps1` passed with the new native controls: translated delay label, both toggles and persisted TOML values, plus the existing German/Chinese theme-selector checks. Captures `config-portals-de.png` and `config-start-de.png` were visually reviewed. Native narrow labels use ellipsis with full help on hover. The client remained silent with a free cursor. Gameplay events use embedded GameTest connections; a complete multiplayer modpack and third-party damage attribution remain integration limits.

`tools/check-release-jar.py` passed both dedicated launches with **Elsebase 1.1.0**, clean world save/reload and shutdown, using the final packaged artifact. World format 4 and theme format 2 remain unchanged. See [policy semantics and configuration](portal-policies.md).

Release 1.0.0 verification: `build` (including domain tests and release-JAR checks), all 25 GameTests, and two packaged-JAR dedicated-server launches with save/reload and clean shutdown passed. The release retains world format 4 and theme format 2. Graphical vanilla/Iris results from the preceding audit remain applicable; they were not rerun for the version promotion.

Pre-release audit and repeatable artifact checks: [release readiness](release-readiness.md). `build` now verifies the packaged JAR, excluding development fixtures and checking generated language encoding.

## Localization verification

`build` checks all eight authored/generated language catalogs for matching keys and placeholders, missing configuration text and encoding regressions. See [localization maintenance](localization.md). `tools/check-config.ps1` passed in the real client with German/Chinese resources, local library selection, search, cancellation, undo/reset and saved TOML verification. The vanilla template fixture also passed after menu localization. Both fixtures were silent and kept the cursor free. JourneyMap itself and native-speaker review of all translations are outside this check. Version remains 1.0.0 because it has not been published yet.

## Template verification

Theme/portal maintenance: `build` and all 24 GameTests pass, covering owner-only theme deletion, persisted library cleanup, fallback bindings, narrow unsupported portal placement at chunk edges and immediate cleanup when either half is mined. The actual-client template fixture verifies confirmed deletion, button separation and visible theme pixels after dimension reentry. It also asserts dimension policy at level creation, before initial chunk compilation. Vanilla portal checks pass for live snapshots, custom floor appearance, reload, transition screens and fallback. The updated template fixture also passes with Iris/Sodium and shaders disabled. Active-shader fixtures were not rerun for this maintenance change. Checks remain silent and leave the cursor free.

Approved-theme replacement: `build`, all 23 GameTests and the vanilla actual-client fixture passed. The fixture validates every built-in palette's baked model and captures all seven themes with/without ceiling. Screenshots were reviewed and copied to [the current gallery](current-theme-gallery.md). Symmetry checks compare rotated positions and Minecraft-rotated block states, including log axes and glazed-terracotta facing. Legacy pack/skin assets are absent from the JAR. Shader profiles were not rerun for this pattern-only replacement; previous engine compatibility results remain separate evidence.

The theme UX fixture also checks first-use Paint selection, absence of Rotate, New Theme naming, discard/cancel protection and publishing a neutral private draft. Its generated library file is removed after success so repeated runs remain independent. Inspect `template-menu.png`, `theme-paint-menu.png` and `theme-new-draft.png` under the isolated client's screenshots directory for the fixed open-corner isometric view.

Open-material regression: GameTests cover the default-open policy, a test-only emerald-block blacklist, non-destructive player-built wall targeting before an adjacent Elsebase wall, invalid-block targeting, wall-relative log axes and quarter-turn symmetry of every built-in horizontal pattern. The test-only tag lives inside `gametest_pack`, never the normal active data root. Client checks exercise real log end/bark quads on all four wall orientations, gilded blackstone and animated atlas materials. `deepstone-room.png` / `deepstone-ceiling.png` capture the approved masonry theme in the actual menu.

All automated client flags (`verifyClient`, `verifyPreview`, `verifyPortals`, `verifyTemplates`) also load test-only volume overrides: every sound category reads as zero from startup. Normal launches and persisted audio settings are unaffected. The template fixture asserts every category stays muted.

`build` and `runGameTestServer` include template parsing, import blocking, authority, persistence, scanning and face assignment checks. Run `tools/check-templates.ps1` for actual-client pixel changes, menu and free-cursor assertions; optional `-Profile iris` and `-Profile iris-active` use the pinned fixtures prepared by `tools/prepare-render-compat.py`. `-PverifyTemplates` enables the isolated fixture and keeps the mouse free, like the portal check. See [Surface templates](surface-templates.md) for coverage and limits.

## Cursor behavior during automated client checks

`runClient -PverifyClient`, `-PverifyPreview` and `-PverifyPortals` (including `tools/check-portals.ps1`) automatically load a test-only mouse mixin through the launch arguments. It cancels `MouseHandler.grabMouse` before Minecraft centers or captures the desktop cursor. The pinned 1.21.1 implementation has no cancellable NeoForge event at this call. Test fixtures control their own camera; mouse-look is intentionally unavailable during these runs. Normal `runClient` and installed mod launches do not register this mixin and keep normal controls.

The portal regression checks both Minecraft's capture flag and GLFW's actual cursor mode throughout the run, including loading, resource reload and dimension travel, and explicitly attempts capture after joining the world. Keep the mixin configuration out of the mod's metadata: it must only be enabled by the automated launch flags.

Verified with a complete vanilla/Fancy portal client run: explicit capture was suppressed, the cursor remained normal throughout, and all rendering/transfer assertions passed. `build` also passed. The other two client fixtures share the launch hook but were not rerun for this test-only change.

## Bounded live portal rendering — current build

Implemented after the user's explicit Go. See [rendering contracts and reproducible commands](portal-rendering.md). Current verification includes the production quarantine domain checks, **15 GameTests**, and self-terminating real-client fixtures in isolated `build/portal-client-*` directories.

Actual client checks passed for vanilla Fast, Fancy and Fabulous, Iris **1.8.12** with Sodium **0.6.13** and shaders disabled, and the same combination with an active authored minimal shaderpack. The active-shader profile verified automatic static fallback, no destination streaming, working portal travel and replacement of exactly the two receiving screens. This is not live-view support inside shaderpacks or certification of arbitrary packs.

The final expanded live-view checks verify actual red/blue destination pixels on the doorway, offscreen image variation, both-sided/cardinal off-axis projection, block updates, fluid/glass models, target darkness while the source stays bright, resource reload with fresh snapshots, injected faulty-model quarantine/reset, full-screen arrival capture, normal behavior for unmarked teleports, and quality-OFF cleanup. The final full expanded runs used Fabulous and Iris/Sodium without shaders; earlier Fast/Fancy runs covered the same lifecycle, with the visible-pixel check added subsequently. Screenshots were also visually inspected for the live doorway and dark target. A broken emerald model is intentional test injection and produces an expected bounded warning rather than a crash.

Example measured p95 CPU render-submission time for one balanced preview on the test host's RTX 3070 Ti: **0.559 ms** in the final Fabulous run and **0.113 ms** with Iris/Sodium and shaders disabled. These values exclude mesh construction, GPU completion, WAN latency and multi-client load; they are fixture observations, not performance guarantees. Per-run JSON reports and screenshots are retained with the fixture.

Regression work exposed and corrected four concrete lifecycle/rendering defects: a viewer removed during the bounded snapshot loop could be revisited from its old iteration list; vanilla float sine-table rotation did not produce exact cardinal round-trips; local resource-cache reset required an explicit server snapshot restart; and rendering the destination portal's own fallback face covered the scene. Actual-aperture pixel assertions now catch the last issue, which framebuffer-only checks missed. Target portals elsewhere in the scene remain non-recursive placeholders.

`tools/check-portals.ps1` rejects nonzero client exit, missing reports and stale success. `tools/prepare-render-compat.py` pins and SHA-512-checks optional test-only dependencies. No third-party JARs, worlds, logs, game source extracts or shader fixture binaries are included in the mod artifact. Graphical tests require a desktop/OpenGL environment; explicit packet-codec GameTests supplement integrated-server client transport. Remote TCP multiplayer, adverse latency, 100 simultaneous viewers and arbitrary modpack models remain unverified.

No generation changes or save-format migration are introduced. Existing development portals may need recreation because surfaces now have non-ticking block entities; a new test world is recommended. Save version is still 4; network protocol is now 4.

Final `build runGameTestServer` completed successfully with all 15 required tests and clean server shutdown. JAR: `build/libs/elsebase-0.1.0-SNAPSHOT.jar`, SHA-256 `44358E99F124E0F7A199CA66D0C265D471B5029372873FEB17F5838AD67D1CE4`. All 95 generated/custom JSON resources parsed, including seven fallback models; original design SHA-256 remains unchanged. Final review also connected immediate logout cleanup and bounded subscription reconciliation after server-budget changes.

## Previous portal contact, visible surface and outside timeout build

`gradlew build runGameTestServer` passed with all **13 GameTests**, domain tests, clean world saving and exit code 0. The new test calls the actual BlockState entityInside callback with the player's feet below an unsupported inner portal; body contact successfully returns outside. Earlier full-body restrictions would reject this state. Wholly outside bodies still do not trigger. A personal entry test removes both the inner frame and its support, then confirms arrival on the repaired anchor pedestal without reconstructing the unsupported frame.

Lifetime coverage checks the exact 1199/1200-tick boundary, successful exit renewal, refreshed deadline retention, no expiry inside, offline-inside retention, saved deadline/residence round-trip, outside expiry removing both loaded surfaces, and preservation of a chest replacing one portal block plus independent recovery history. The new contact test runs five ticks after batch startup to avoid colliding with the existing fixtures' two-arrivals-per-tick global throttle; that production bound remains unchanged.

`runClient -PverifyPreview` loaded the generated translucent models and captured `run/screenshots/elsebase-portal-surface.png`. Visually inspected: purple portal surface within the existing trim, without destination rendering. It uses vanilla's animated nether-portal sprite. Client and integrated server saved and closed cleanly. All fourteen portal-half models across seven themes have the two-sided surface; resource generation is reproducible. This is not a shader-pack or multi-client compatibility test.

Final JAR SHA-256: `EB9EBDE8191135657FF3135AE9966EB73B1006853FFA2422778F805C4978F16A`. JAR includes InstantExpiry and the new model surface. Original design hash unchanged. Save version remains 4; the additive lifetime list preserves new timers across restarts, while old records without known owner residence are initialized on login rather than risking removal of an offline inside owner's only return. No new world is required.

## Previous portal recovery and current-anchor entry build

`gradlew build runGameTestServer` passed: domain checks and all **12 GameTests**, successful saving of all dimensions, exit code 0. Regression cases cover personal re-entry after inner recall and anchor movement, missing anchor footing during exit with all inner placement events denied, a half-destroyed inner frame, a destroyed outside frame replaced by a chest, safe nearby return around an obstructed landing, recall without rebuilding the outside location, old-surface break denial during renewal, F recovery when new inner placement is denied, pair deletion, absent return history and removed external dimensions. The independent return record survives SavedData round-trip and pair removal.

An additional unsafe-spawn fixture covers the emergency refuge path: every nearby floor is magma, so recovery adds a 3x3 stone platform in empty air and lands safely there. Existing magma remains untouched. The temporary test spawn is restored in a finally block. Normal build/claim protections remain active for new entrances and anchor repairs; retiring own instant surfaces and emergency empty-space footing are deliberate recovery exceptions.

Causal findings: arrival previously used the anchor only when the physical target endpoint equaled the current reference, so recalling the inner frame or moving the marker bypassed it. The shared replacement transaction revalidated both endpoints, so an obstructed/deleted outside doorway blocked an inside recall. Plane traversal required both frames and the exact mapped landing; removing a frame or filling that landing prevented exit. The old anchor-repair call already had an entry-only guard; no direct anchor-floor-only exit dependency was found. The new regression explicitly verifies that missing inner support and denied inner repairs do not affect leaving, alongside damaged frame recovery.

The recovery fixture initially chose a spot near a chunk boundary; seeded walls sometimes prevented a new doorway and correctly triggered direct escape. It now selects the guaranteed clear central interior when asserting successful frame recall, and separately denies placement when asserting direct F escape.

Final JAR SHA-256: `8D5372804DEAE06BE42C75F13CE4F93FF05DF2493CA4892907C8BF1B2F769011`. JAR contains ReturnTravel and the updated portal/save logic; original design SHA-256 unchanged. Saved-data version remains 4 with an additive returns list. Existing current-format worlds can continue; no generation changes. No new client-rendering code changed or interactive multiplayer session was run. Third-party teleport interception and a completely filled/protected custom world spawn still require modpack-specific testing; if neither a safe landing nor empty refuge space exists, recovery reports it instead of destroying builds or crashing.

## Previous gaze-based structural tools build

`gradlew build runGameTestServer` passed: domain checks plus all **11 GameTests**, followed by successful world saving and exit code 0. New coverage exercises six gaze-selected surfaces at negative coordinates, adjoining-wall selection, far/diagonal/vertical-neighbor exclusion, missing-border wall reconstruction, full slab perimeter repair, preservation of repaired walkways during removal and queued-edit range revalidation. Tool tests now assert air-use and block-use operate on gaze rather than clicked coordinates; Shift does not change operation. Existing anchor support, claims, persistence and portal tests remain green.

`gradlew runClient -PverifyPreview` creates a uniquely named development fixture world and prepares a wall with its entire floor/ceiling border row missing. In the actual client, the translucent green wall preview was captured and inspected; normal client item use sent the action and restored that wall, confirmed from synchronized client block states. A second capture verified the full-slab floor preview. Files: `run/screenshots/elsebase-preview-wall.png` and `run/screenshots/elsebase-preview-floor.png`. Client and integrated server closed cleanly and saved every dimension. The opt-in fixture makes future preview checks repeatable without touching existing user saves. Orange removal uses the same rendering path; shader packs and a separate multiplayer client remain unverified.

The first new GameTest attempted selection immediately after a synthetic teleport into an unloaded negative-coordinate chunk. Selection correctly returned null rather than causing generation; the fixture now explicitly prepares its chunk. That failed run also retained its mock player and reproduced the documented vanilla unload-loop shutdown issue (confirmed by thread dump); only that test server was terminated. Subsequent complete runs passed and shut down normally.

Resources regenerate identically and parse as UTF-8. JAR inspection confirms the preview class and current tooltips, and no obsolete structural Mode class. Final JAR SHA-256: `A9890E6CB8EE3BC694FBA91302B7DCA444A4402E971E7E5C9285159D404AEA56`. Original specification hash unchanged. Geometry and saved-data version remain 4; the preceding double-wall test world can continue without regeneration.

## Previous independent room boundaries and native configuration build

`gradlew build runGameTestServer` passed: production domain checks and all **10 GameTests**, clean dimension saving and exit code 0. New assertions verify aligned 2x2 passages through both independently editable wall halves, all four preserved floor borders, removal of only the targeted half, solid wall creation at seeded openings, grass/snow clearance at the chosen portal location, solid/block-entity refusal and claim-denied placement rollback. Existing anchor safety, lifecycle, tools, selective explosions, persistence and mirror loading continue to pass. Save version 4 round-trips; versions 1, 2, 3 and 5 are rejected. Fixtures use `build/gametest-themes-v2`.

Default visual brightness and darkness are tested through the production lightmap policy, including server override/reset. Generated blocks and decorative light panels emit no light. Synthetic GameTest connections skip payload negotiation; the lighting sender checks the pinned NeoForge `hasChannel` contract before sending. A real multi-client server test remains outstanding.

`gradlew runClient -PverifyClient` loaded resources and opened the registered native NeoForge config editor from the main menu. The opt-in check asserts COMMON config is loaded and the editable section screen opens, then captures `run/screenshots/elsebase-config-check.png`. The capture was visually inspected: five editable groups and normal Done control. First-launch accessibility onboarding is bypassed only for this development check. The client exited cleanly. This verifies the actual registered factory/screen, not manual navigation or every individual setting edit.

The final `gradlew build` passed after client-check and localization changes. Generated resources are reproducible and all JSON parses as UTF-8. JAR contents include the client config registration, lighting policy and renamed items. JAR SHA-256: `C112F529862E49C65903AE8231743AF99052BFAC2FBA44587CF8E0B03FBF39A6`. The original design hash remains unchanged. A 64-column geometry-only sample measured 10,308.5 solid blocks per column and 16–17 ms; no lighting, disk I/O or multiplayer performance claim follows from this measurement.

Use a new test world. Settings now live in installation-wide `config/elsebase-common.toml`, not the earlier per-world SERVER file. Full visual gameplay review, shader integration, third-party automation/claims and multiplayer lighting synchronization remain unverified.

## Previous stacked rooms, tools and anchors build

`gradlew build runGameTestServer` passed with nine GameTests, successful saving of every dimension and exit code 0. Tests cover initial carpet creation, landing on the carpet, entry-time floor repair, claim-denied repair rollback, online anchor ticket acquisition/transfer/logout release, separate structural tools, Shift-right-click-only mode changes without editing, current-level ceiling operations and marker-floor protection from above and below. Existing portal lifecycle, mirror loading, persistence, explosion, darkness and fake-player tests still pass. Unsupported saved-data versions 1, 2 and 4 are rejected; version 3 round-trips.

Domain checks exercise actual generated exits on all sixteen levels across positive/negative coordinates, doorway sizes/offsets, bedrock and height bounds. A 64-column layout sample measured 7,446.5 solid blocks per column and 19.4 ms of pure geometry evaluation; lighting, I/O and full server performance are not measured by that sample.

Client startup loaded the new tools/resources without model or texture errors; it was closed cleanly. The subsequent floor-model underside texture change was verified against local assets, not by another interactive playthrough. Model front elevations were regenerated and inspected. All 28 unique vanilla texture references resolve; generated JSON is valid UTF-8. JAR inspection confirms 128 height, both new tool recipes and absence of the obsolete combined-tool resources.

Final JAR SHA-256: `31F8C2DF012649A0BC3152BC3F62236ECA1F365E2B9339AC793D5FBBB659E4ED`. Original specification hash remains unchanged. The reduced-height build needs a new world; isolated tests now use `build/gametest-v3`, leaving previous fixtures untouched. The first run in the new test directory logged the vanilla missing `server.properties` message, created its defaults and completed successfully; subsequent runs loaded them normally.

Remaining verification: full interactive playthrough, third-party claims/automation behavior, multiplayer scale and complete lighting/I/O profiles. See [generation and loading trade-offs](stacked-rooms-and-anchors.md).

## Pre-1.0 compatibility simplification — 2026-09-20

Per explicit user direction, older development worlds need not remain compatible before release 1.0. Removed the version-1 portal migration; current version 2 round trips and orphan/state reconciliation remain covered. Unsupported versions 1 and 3 are explicitly rejected in tests. Earlier migration verification below is historical and does not describe the current contract.

`gradlew build runGameTestServer` passed: domain tests and all eight Minecraft GameTests, all dimensions saved, exit code 0. JAR SHA-256: `C77DF1501FB5040C26C63B6B92033FF5C55AC1F00030A5E0C1A893BC24F704E3`. No client rendering changed in this follow-up.

## Doorway refinement verification — 2026-09-20

- Final `gradlew build runGameTestServer`: successful, including domain tests and all **8 GameTests**, followed by saving every dimension and exit code 0.
- New coverage: exact 1×2 geometry, all twelve offsets, sparse boundary distribution, sealed rooms/dead ends, continuous bedrock material, actual vanilla bedrock mapping, narrow/tall traversal rejection, correct portal halves, version-1 migration, legacy block cleanup, preserved neighboring construction and reconciliation idempotence.
- `gradlew runClient`: OpenGL 4.6 initialization and model/texture atlas loading successful, no missing model/texture errors. Client closed cleanly. This verifies startup/resource loading, not a full interactive playthrough.
- `python tools/preview-models.py` (Pillow) validated **28 unique vanilla texture references**, including optional themes, and generated `build/model-preview.png`. Front elevations were visually inspected; this is not a gameplay screenshot. The helper makes future model changes reviewable without launching seven separate theme sessions.
- An expanded run passed all eight assertions but hung at shutdown. A thread dump again located the busy server thread in vanilla chunk-unload callbacks. The survival fixture remained connected until shutdown, unlike the previously corrected instant fixture. It now disconnects after assertions and allows 40 ticks of normal teardown. The subsequent complete run saved all worlds and exited successfully. No production chunk scheduling was patched; wider modpack shutdown behavior remains unverified.
- Final JAR SHA-256: `F09ECE68A526937FB644A704C5F696366A63A08A0CE59E5347C5EFC01CC4ED93`.
- Original specification SHA-256 unchanged. Existing room chunks retain previous geometry/bottom layers; version-2 portals migrate at chunk load. See the player guide for saved bindings and vanilla F conflict.

## Previous first-playable verification — 2026-09-20

Gameplay implementation is authorized and the first static-portal build is implemented as **Elsebase**, namespace `elsebase`, Java package/group `dev.elsebase`. Pinned toolchain versions below are unchanged. Output: `build/libs/elsebase-0.1.0-SNAPSHOT.jar`. The bootstrap record below is historical.

- `gradlew build runGameTestServer`: successful. The build runs the dependency-free `domainTest` runner; the ordinary JUnit test task is deliberately disabled because no JUnit framework is used.
- All **7 Minecraft GameTests passed**, including real portal crossing and return, obstructed return refusal, anchor/recall persistence contracts, registry serialization and overlap rejection, survival crafting-component consumption/refund and pair limits, ceiling editing/restoration/obstruction safety, fake-player mining, selective actual explosions, light/spawn policy, and independent mirror-ticket causes without cascades.
- `gradlew runClient`: client initialized, OpenGL 4.6 and resource/model atlases loaded. First launch found two invalid animated-item texture names; corrected to actual clock/compass frames and relaunched with no missing-model/texture warnings. Test clients were closed after inspection.
- Validated all **44 vanilla texture references** across seven theme palettes against the resolved Minecraft resources JAR.
- Original specification SHA-256 remains unchanged. JSON assets are reproducible using `node tools/generate-resources.mjs`.

GameTests use `build/gametest`, separate from client/server development data in `run`. The GameTest launcher bakes only its flat preset, so a test-only pack supplies the actual Elsebase dimension in that preset. A before-batch fixture resets test registry data for repeatable runs. No EULA was accepted or modified by the agent.

An expanded return test initially passed its assertions but hung during immediate test-server shutdown. The thread dump showed vanilla `ChunkMap.scheduleUnload` repeatedly waiting for generation references inside the unload queue. That isolated test process was terminated. The fixture now disconnects its embedded player after the final cross-dimensional return and allows 40 ticks for asynchronous teardown before ending the batch. The subsequent full run passed all seven tests, saved all dimensions and exited successfully. This is test-fixture lifecycle handling, not a claimed fix for every vanilla/modpack shutdown issue.

Final verified JAR SHA-256: `BD656E85C410B2A60E6AFA423C7D9D76375AC18E0BC41BDED288E0F3FE260696`. Inspected metadata expansion, entry point, access transformer, dimension data, portal model state and bundled theme pack entries.

Remaining limits: client startup is not a full interactive playthrough or visual review of all seven packs. Specific claims/modded drills, shaders/performance mods and 100-player performance remain unverified. Seven styles are functional vanilla-texture palettes; final bespoke artwork/logo exports and live previews are not delivered. See [implementation](implementation.md) and [player guide](player-guide.md).

## Historical bootstrap record

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
