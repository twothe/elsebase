# Portal rendering and regression workflow

Implemented after explicit approval on 2026-09-20. The [original proposal](portal-preview-plan.md) records alternatives; this document describes the delivered contracts and deliberate compatibility compromises.

## Player-visible behavior

A nearby usable doorway shows a perspective-correct view of a bounded target area. Moving sideways changes the view. Personal entrances show the current anchor/reference area, even when the inner doorway has been recalled elsewhere. Permanent doorways show their fixed destination. Rotation uses a shared cardinal frame transform; anchor-centered landing and safe outside recovery remain authoritative.

Actual Elsebase doorway transfers replace both Minecraft receiving-screen instances with a separately rendered image from the final landing pose, followed by a 150 ms blend. If an image is unavailable (including active shaders), a restrained purple/dark transition veil is used. After 750 ms it shows a small loading message. The original screen's readiness, narration and 30-second timeout still run. Loading/network stalls cannot be eliminated by rendering. Login, death, commands, other teleports and direct F emergency recovery are unmarked and retain their original screen handling.

The preview includes ordinary baked block geometry, biome tint, directional shading, sampled block/sky lighting and fluids. Transparent geometry renders after opaque geometry with sorted quad indices and no depth writes. Sorting across sections is approximate, as with ordinary section rendering. No live entities, arbitrary block-entity renderers, machine animations, custom dimension skies, recursive portals or cross-portal interaction are included. Objects rendered entirely by block-entity renderers, such as chests, may be absent. Simplified target sky/fog and bounded visibility are intentional.

## Compatibility and failure containment

- No second ClientLevel, global world/camera swap, renderer replacement, new access transformer or mixin is introduced.
- The scene implements a read-only `BlockAndTintGetter`. Models that require a live world or block entity may fail; the failed block ID is then quarantined for the resource session. Other blocks continue rendering. Partially generated geometry from a failing block is never committed.
- Model RuntimeExceptions and linkage failures are isolated, including vanilla's wrapping `ReportedException`. Nested fatal JVM failures are rethrown. This cannot prevent a native driver crash, out-of-memory failure or a crash in another mod's ordinary world renderer.
- Quarantine is memory-only. Restart or resource reload clears it, allowing repaired models to work. Diagnostic stack traces are capped at 16 failed IDs plus one summary warning. Failures outside one model (shader/section/framebuffer) degrade the scene or session to the static surface.
- Iris/Oculus detection uses only the optional public `IrisApi.isShaderPackInUse()` API, including the older package name ([public Iris API source](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/api/java/net/irisshaders/iris/api/v0/IrisApi.java)). If an installed integration cannot expose that API, live rendering is disabled conservatively. No hard Iris/Sodium dependency is shipped.
- **With an active Iris shaderpack, the portal uses the animated static surface.** No offscreen preview or snapshot subscription runs. Travel and the selective transition veil still work. With Iris installed but shaders disabled, the normal live-view path is available.
- Static frames remain baked block models. A non-ticking `PortalSurface` block entity draws the aperture through the normal renderer. Only the lower half draws the full 1×2 surface; its render bounds include both halves. No shader has to understand a second world pass.
- The offscreen renderer owns its framebuffer, meshes and shader. It restores framebuffer bindings, viewport, shader selection, texture selection, clear values, depth, blend and cull state. World-relative integer origins are subtracted before GPU floats; cardinal frame mapping avoids float sine-table drift.

This is graceful coexistence with shaders, not full live portal rendering inside a shaderpack. The authored small test shader does not certify every third-party shaderpack or graphics driver.

## Data, limits and lifecycle

`PreviewProtocol` carries a dimension-addressed scene description and fixed-size section snapshots, separate from vanilla chunk packets. No block-entity NBT, inventory contents, world seed or client-selected target coordinates are transmitted. Clients send quality and a bounded restart request only. The server checks portal ownership/public access, source identity, current dimension, distance and source presence. Those checks repeat before each snapshot, including after recall/anchor movement.

One nearest usable forward-facing portal within 24 blocks is selected per viewer. Each scene holds at most 3×3 chunk columns × 3 vertical sections (27 sections), clipped to dimension height. Both laterally opened rooms and vertically stacked rooms can appear within that fixed volume. There is no speculative room-connectivity traversal. Missing sections are opaque in neighbor lookups; the distant image fades into a target-colored background rather than an unbounded view.

**Previews use already-loaded chunks only.** They do not create tickets, synchronously generate terrain or tick a second world. Anchor and existing mirror loading remain responsible for target availability. Unavailable sections are explicitly withdrawn. If no central mesh is available, the animated portal remains visible. Looking at a portal cannot produce a recursive loading chain.

The server rotates through sections and compares actual block/light/biome arrays. This observes player, machine and fluid changes without depending on player-only events. Changed snapshots replace old data; unchanged sections produce no data packet. Work is round-robin across active subscriptions, with an approximately 2 ms cooperative copy budget checked between sections. A single section or mod callback can exceed that target. Geometry updates are eventually consistent, not every rendered frame: a quiet single-viewer balanced cycle takes roughly 0.7–1.4 seconds for 27 sections, and shared load increases that interval.

| Control | Values/default | Contract |
| --- | --- | --- |
| COMMON `preview.budget` | OFF, LOW, **BALANCED**, HIGH | 0/8/16/32 subscriptions; at most 0/1/2/4 section copies per server tick globally |
| CLIENT `render.previewQuality` | OFF, LOW, **BALANCED**, HIGH | One live surface; up to 256×512 / 512×1024 / 768×1536 target pixels |
| CLIENT `render.immersivePortalTransition` | **true** / false | Portal-only transition replacement independent of preview quality |

All presets bound geometry to 65,536 vertices per section and 400,000 vertices per scene, with an 8,192-vertex ceiling for a single block. Oversized models are quarantined; oversized sections fall back rather than allocate unbounded buffers. At most one section mesh is rebuilt per client tick. Within eight blocks the image updates each visible frame; farther away it updates every other frame. Camera parallax does not require a new network snapshot. These are fixed work/size bounds, not guaranteed FPS or a GPU-time budget.

The fixed section encoding is under 25 KiB even with maximum-length IDs (4096 state IDs, 4096 packed light bytes, 64 biome IDs and a small header). Thus even HIGH's 80 copies/second cannot emit more than approximately 2 MiB/s of section payloads globally. Actual unchanged sections send nothing. There is no unbounded retransmission queue. Slow networks and 100 simultaneously interested players have not been load-tested; excess viewers receive fallback.

Server metadata heartbeats keep the client scene valid. Pair/anchor changes use new generations; stale section messages are discarded. Client resource reload or cache loss requests a new server generation, preventing the server's unchanged-section cache from withholding necessary data. Disconnect, stale heartbeat, shader activation, quality OFF and dimension changes release client resources. Server subscriptions expire with preferences or when sources cease to qualify.

Both sides must run this build's network protocol 4. SavedData stays at version 4. For pre-1.0 testing, use a new world or recreate earlier portals: older chunk saves may not contain the newly introduced surface block entities. No migration is supplied and no world data is silently reset.

## Resource-pack surface contract

Frames remain in `models/block/portal_lower.json` and `portal_upper.json`. The old baked center plane is removed to avoid two overlapping surfaces. `models/block/portal_surface.json` is now a separately registered model whose **particle texture** selects the animated fallback sprite. All seven bundled themes include it. A skin can replace that particle texture; it must not reinsert an opaque/live center plane into the static frame models. Preview contents use the client's normal block assets.

## Automated checks

Set Java 21 via `JAVA_HOME`; the scripts keep Gradle caches local to the project.

```powershell
./gradlew.bat build runGameTestServer
./tools/check-portals.ps1 -Profile vanilla -Graphics fast
./tools/check-portals.ps1 -Profile vanilla -Graphics fancy
./tools/check-portals.ps1 -Profile vanilla -Graphics fabulous

# Optional real Iris/Sodium compatibility fixtures; downloads only into ignored build/.
python tools/prepare-render-compat.py
./tools/check-portals.ps1 -Profile iris -Graphics fancy
./tools/check-portals.ps1 -Profile iris-active -Graphics fancy
```

The dependency preparer pins Iris 1.8.12 and Sodium 0.6.13 for NeoForge/Minecraft 1.21.1 using SHA-512. Update the pins deliberately for future qualification. It creates a minimal authored active shader fixture; it does not change the user's launcher/modpack. No third-party mod JAR is packaged in Elsebase.

Client checks require a working graphical desktop/OpenGL environment. Each run creates a uniquely named disposable world under `build/portal-client-<profile>`, then exits automatically. The PowerShell wrapper rejects a missing, failed or stale result file. Screenshots and `portal-check-result.json` stay in that directory; the tests never open a user's save. Resource reload is part of live-view checks. Fault injection wraps an emerald-block model only under the opt-in test flag and only when called with the preview world adapter; normal world rendering is untouched.

Coverage:

- Domain checks exercise the production quarantine: one attempt/report, isolation of other models, reload reset and nested fatal-error propagation.
- GameTests exercise real section capture/codecs, actual block updates, no loading of absent chunks, all cardinal frame round-trips at large/negative coordinates, ownership/source invalidation and current-anchor resolution, alongside existing travel/claim/lifetime tests.
- Actual-client checks exercise off-axis projection on both sides/all directions, a nonuniform destination framebuffer, block/light changes, fluid/glass model support, injected bad-model quarantine, resource reload/resynchronization, full-screen arrival capture, both transition screens, unmarked teleport behavior and quality-OFF cleanup. Active-shader checks verify fallback, no scene streaming and working traversal/transition.

Latest run results and artifact hashes are recorded in [development](development.md). These tests reduce repeated manual work but do not establish full modpack compatibility, WAN latency behavior, sustained multi-client load, every theme's aesthetics or arbitrary shaderpack support. GameTests use a dedicated server process; graphical network tests currently use an integrated server, with explicit codec tests supplementing local transport.
