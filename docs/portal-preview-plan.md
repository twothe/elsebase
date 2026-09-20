# Portal previews and dimension transitions — implementation proposal

Status: **historical proposal, subsequently authorized and implemented**, 2026-09-20. See [delivered rendering contracts](portal-rendering.md) for actual scope, compatibility compromises and operating limits. The following text preserves the pre-approval proposal. The user requires discussion and an explicit Go before implementation. No runtime, asset, dependency or configuration changes are authorized by this document. Baseline: Minecraft 1.21.1, NeoForge 21.1.250, repository commit `05df4a2`.

## Recommendation

Implement a bounded, read-only destination scene, rendered from the viewer's transformed camera into a texture on the existing 1×2 doorway. Keep server travel authoritative and independent of preview availability. Cover the actual dimension handover with destination imagery instead of Minecraft's terrain-loading screen, only for explicitly identified Elsebase doorway transfers.

Start with a technical vertical slice after approval. It must prove camera perspective, lighting, compositing and the transition lifecycle before expanding synchronization. This is a substantial rendering/networking feature, not a replacement texture.

The first proposed scope includes room geometry, ordinary baked block models, fluids, block changes, biome tint and target lighting. It does **not** promise arbitrary animated machines, block-entity renderers, players/mobs, particles, custom dimension skies or shader-pack parity. These limits require agreement; they are not established product decisions. A block-only view can omit objects rendered entirely by block-entity renderers, such as chests. If these objects and moving entities are required immediately, choose the more extensive second-world approach below before implementation.

## Verified technical situation

Inspection used the actual resolved `build/moddev/artifacts/neoforge-21.1.250-sources.jar`, not current-version API assumptions. Extracted files under ignored `.setup/sources/` are investigation aids, not new dependencies.

| Evidence | Consequence |
| --- | --- |
| `ClientboundLevelChunkWithLightPacket` contains chunk X/Z, chunk data and lighting, no dimension key. `ClientPacketListener.handleLevelChunkWithLight` applies it to the current `level`. | Normal chunk packets cannot simply be sent for the other dimension. A separately addressed preview protocol/cache is necessary. |
| `ClientPacketListener.handleRespawn` constructs a new `ClientLevel` on dimension changes and replaces the local player. | Keeping destination data for a preview does not automatically preload the playable world or its compiled terrain. |
| `Minecraft.setLevel` opens a transition screen and forces a frame before switching engine worlds; `startWaitingForNewLevel` subsequently opens another screen with a readiness supplier. | Both screen creation points must be covered. Consuming a one-use marker on the first screen would allow the second screen to flash. |
| `LevelLoadStatusManager` waits for the server's loading-start notification and then the player's compiled render section, with exceptions for dead/spectator/out-of-height players. `ReceivingLevelScreen` also has a 30-second timeout. | Hiding a screen does not establish render readiness. Preserve this lifecycle; use a visual bridge while loading continues. |
| NeoForge `RegisterDimensionTransitionScreenEvent` and `DimensionTransitionScreenManager` expose custom receiving-screen factories. Incoming dimension registrations take precedence over outgoing registrations, below conditional registrations. `ScreenEvent.Opening` can replace the selected screen. | There are supported interception points. Dimension registration alone is too broad for portal-only behavior and may lose precedence when leaving for another mod's dimension. |
| `ClientLevel` construction posts a world-load event and retains a connection/renderer. `LevelRenderer` and `GameRenderer` repeatedly read `Minecraft.level`, player, camera and shared dispatchers. | A second vanilla renderer is not an isolated plug-in camera. Swapping singleton state has significant lifecycle and mod-compatibility implications. |
| `BlockRenderDispatcher.renderBatched` accepts `BlockAndTintGetter`, model data and render type. `TextureTarget`/`RenderTarget` supply offscreen color/depth buffers. | An isolated block-scene implementation has concrete native building blocks, but arbitrary mod models may demand more context. |
| `RenderLevelStageEvent` documents translucency/Fabulous caveats. Existing portal models bake their translucent fallback face into the chunk mesh. | Correct composition requires a deliberate surface/render-stage design and graphical tests, not drawing a second coplanar quad over the current texture. |

Current Elsebase integration points:

- `Network` synchronizes the instant action and darkness only. There is no client portal registry, preview stream or destination renderer.
- `Portals.cross` triggers on body overlap, not exact camera-plane crossing. Personal entry always lands at the current anchor and stops velocity. The inner frame may be elsewhere or absent.
- Entry rotation currently uses the paired endpoint orientation, even after the anchor moves. `ReturnTravel.escape` keeps the incoming yaw and stops velocity. These paths do not yet share a spatial transform.
- `ReturnTravel` can choose a nearby safe location or overworld spawn. A destination displayed in advance can become invalid before crossing.
- `MirrorLoading` excludes its own, UNKNOWN and POST_TELEPORT tickets. Any new preview tickets must also be excluded as propagation roots. Existing mirror loading supplies server readiness, not client scene data.
- Bright Backdoor appearance comes from a client lightmap policy, not luminous terrain. A preview from the Overworld must explicitly use the target lighting policy; the active world's lightmap is insufficient.

Primary public references corroborating the pinned local source inspection:

- [NeoForge 1.21.1 custom payload registration and limits](https://docs.neoforged.net/docs/1.21.1/networking/payload/).
- [NeoForge 1.21.1 transition-screen selection](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/client/DimensionTransitionScreenManager.java).
- [NeoForge 1.21.1 transition-screen registration event](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/client/event/RegisterDimensionTransitionScreenEvent.java).

## Architecture alternatives

| Approach | Benefits | Costs and limitations | Recommendation |
| --- | --- | --- | --- |
| Read-only block scene with its own meshes, camera and framebuffer | Explicit limits; no second simulated world; uses installed block textures/models; relatively contained integration | Custom light/tint/fluid/transparency work; no general block-entity or entity renderer support; some mod models need adapters | Recommended first scope, subject to accepting its visual omissions |
| Second `ClientLevel` and `LevelRenderer`, isolated packet routing and carefully scoped render context | Better route toward full world appearance, entities and animated machines | World events, global state, dispatcher/lightmap handling, renderer replacement mods and resource lifecycle are substantial compatibility work; still needs a custom stream | Choose if full dynamic factory visibility is an immediate requirement |
| Fixed screenshot or fixed destination camera | Simpler render/cache | Fails the agreed parallax requirement; looks like a monitor | Not suitable as the main preview; cached imagery is useful during handover |

A general portal engine would be a separate dependency/product decision. No particular external library has been selected or certified for this pinned stack. Physically continuous worlds, looking/interacting around portal edges, entity halves across planes, cross-portal projectiles and recursive views remain outside this proposal.

## Destination and travel contract

Introduce one shared destination description for rendering and travel: pair identity/revision, source frame, effective target frame, target dimension and landing policy. Resolve it on the server; clients never nominate arbitrary target coordinates.

- Permanent entry uses its fixed destination frame.
- Personal entry uses the **current anchor/reference frame**, even if the recalled inner doorway is elsewhere or absent. Show the place the player will enter, not a view of a stale counterpart.
- Return previews normally show the stable outside endpoint. If safety checks redirect travel, send the final destination before transferring; abandon the obsolete image and use the transition effect. Preview inspection itself must not repair terrain or repeatedly run the expensive emergency-return search.
- Frame orientation, displayed view and arrival yaw must agree. Sharing this calculation corrects current orientation inconsistencies, but is a visible gameplay refinement to approve with this plan.
- Preserve anchor-centered landing, zero velocity for personal entry/escape, safe returns and body-contact activation. They intentionally prevent perfect geometric continuity. Do not move the anchor or tighten the trigger merely to improve rendering.

Recommendation: retain those safety/gameplay rules and bridge residual viewpoint changes visually. True continuous traversal would additionally require preserving relative position/velocity and aligning the teleport moment with crossing. That conflicts with exact anchor arrival and would require a separate user decision. Third-person cameras and approaching the reverse portal face need explicit tests; the two directed views must not accidentally mirror the scene or expose geometry behind the target clipping plane.

## Preview data and loading

1. Advertise only nearby usable endpoints. The client requests interest by portal ID and quality; the server checks distance, current dimension, ownership/public access, existence and rate limits. Personal portal interiors are visible only to their owner under the current access policy.
2. Establish a short-lived subscription with pair revision and scene generation. Revalidate after recall, anchor movement, expiry, removal and dimension changes; discard delayed messages from old generations.
3. Stream bounded snapshots of 16³ block sections with block states, light arrays and biome/tint inputs. Use per-message and aggregate decode-size limits, section counts, build bounds and backpressure. Do not transmit whole-world saves or full block-entity NBT/inventories. NeoForge's documented clientbound payload limit is 1 MiB; split substantially below it rather than treating the limit as an operating budget.
4. Copy required data on the server thread with bounded incremental work. Process immutable copies outside the world thread where safe. Do not read live chunks from a networking/worker thread. Deduplicate source snapshots across viewers while retaining per-viewer access checks.
5. Initial correctness can use bounded rotating section comparisons and coalesced replacements. Do not rely exclusively on player placement/break events: machines, fluids and other mods also change blocks. If a reliable block/light mutation hook is required by profiling, specify and verify it against this version before adding a narrow injection. Keep snapshot/delta revision ordering explicit.
6. Use short-lived, separately accounted preview tickets only while an authorized nearby viewer needs them. Exclude them from mirror-root analysis; no preview can spawn another preview/load chain. Do not create fake players or intentionally tick machines for rendering. Ticket neighbor effects still count toward measured server cost.
7. Prefer already loaded areas; queue any permitted extra preparation under global limits. Never synchronously generate a large target radius on every gaze update. Capacity exhaustion keeps the static surface and does not block existing travel.
8. Stop streaming on lost interest/disconnect and free leases, CPU caches and GPU buffers on removal, logout, resource reload and shutdown. Resource reload invalidates baked meshes even if block data is unchanged.

Backdoor culling must use **actual current blocks**, including mined/rebuilt double walls and openings in floors/ceilings. The old specification's horizontal cell search alone is insufficient for stacked rooms. Start with a conservative bounded section volume; add 3D opening/frustum traversal after correctness. Treat uncertain visibility as open within the hard budget, rather than wrongly hiding a room. Unknown/out-of-budget space gets a fog/fallback boundary, never an apparently open void.

## Rendering contract

- Transform camera position and orientation through the directed source/target frames. Subtract a nearby scene origin in double precision before GPU float conversion.
- Use portal-relative off-axis projection (or equivalent projective texture coordinates) and a destination clip plane. Merely putting a normal full-screen camera image on the rectangular doorway gives incorrect perspective.
- Render target geometry into a color/depth texture. Render no further live portals in this pass. Target portals use their fallback surface.
- Render the texture only inside the doorway aperture with normal source-world occlusion. Objects in front must cover it; geometry behind the source portal must not bleed through. Near-plane behavior needs testing while approaching, crouching, jumping and falling.
- Separate the static frame model from the surface. Let a dedicated surface renderer choose preview versus animated fallback, including `off`; avoid chunk mesh rebuilds for every readiness change. Preserve the resource-pack surface texture contract across all seven themes.
- Use target lighting, biome tint, relevant time/weather metadata and a simple distance fade. Bright Backdoor and dark Backdoor must both work while viewed from another dimension; the outside must not inherit Backdoor full brightness. Simplified external sky/fog is acceptable only if explicitly accepted; do not promise arbitrary mod skies.
- Cache meshes between block updates. Camera parallax updates are independent of network snapshot refresh. Restore framebuffer, viewport, shader, projection, depth/blend/cull and light bindings after each pass, including failure paths. Bound renderer error logs and degrade the affected scene to fallback.
- Check Fast, Fancy and Fabulous first. Shader packs and replacement terrain renderers require separate qualification; until verified, use an explicit fallback mode instead of claiming compatibility.

### Initial performance envelope, for measurement

These are **proposed starting limits, not benchmarks or guaranteed FPS**. A single `render.previewQuality = off | low | balanced | high` remains the client control. Server policy adds one `preview.budget = off | low | balanced | high` preset, covering aggregate work/traffic/load limits. A separate client `render.portalTransition = immersive | vanilla` provides a compatibility escape hatch for the independently requested loading-screen behavior.

| Client preset | Live surfaces at once | Render-target maximum per surface | Target volume ceiling | Block-data refresh target |
| --- | --- | --- | --- | --- |
| off | 0 | none | none | none |
| low | 1 | 256×512 | 3×3 columns × 3 vertical sections | up to 2 Hz |
| balanced | 1 | 512×1024 | 3×3 columns × 3 vertical sections | up to 5 Hz |
| high | 2 | 768×1536 | 3×3 columns × 5 vertical sections | up to 10 Hz |

The image dimensions are upper bounds, not a fixed projection/aspect ratio: allocate to the projected portal footprint and preserve perspective. Volumes clip to dimension bounds and visibility; the Backdoor usually needs fewer sections. These caps intentionally limit long hall/outdoor views. Larger areas are a later measured option, not implicit in `high`.

Proposed activation/preparation distance: 24 blocks. Within 8 blocks, update the image every rendered frame if budget allows; farther away reduce update rate and resolution. Prioritize visible screen area, with hysteresis to avoid flicker between portals. Hidden/tiny portals retain only a short bounded cache. A proposed 2 ms total client preview GPU budget is a measurement target, not a hard synchronous timer; use asynchronous timing and adaptive reductions.

Initial balanced server trial: 32 concurrent subscriptions, at most 128 distinct additionally leased chunk columns, 4 MiB/s aggregate preview traffic and 256 KiB/s sustained per client, with bounded initial-snapshot bursts/queues. Process at most two copied sections per tick and stop additional work at a measured time budget between operations; one expensive mod callback can still exceed it. These deliberately conservative values may yield slower initial loading on busy servers. Tune using real sections and a multi-client workload; do not assume 100 players can all receive maximum-quality updates simultaneously. Coalesce obsolete updates, drop excess subscriptions to fallback, and never let preview work starve traversal.

Count decoded cache bytes, meshes and render targets separately, not just chunk coordinates. Apply finite queue/cache/mesh byte caps in every preset. Choose those caps from the vertical slice's measured worst-case sizes before shipping. Report p50/p95 frame/tick costs, first-preview latency, network traffic and retained memory for each preset.

## Selective immersive dimension transition

Do not globally cancel `ReceivingLevelScreen` and do not mark every transfer involving the Backdoor as a portal transfer.

1. After validating the actual travel destination, the server sends a short-lived transfer ID with expected source/destination and final pose immediately before its teleport packets. Mark actual Elsebase doorway passage only; commands, login, death/respawn, other mods' teleports and direct F emergency escape retain their normal behavior.
2. The client prepares a retained image of the predicted destination at the final pose when available. This is rendered at appropriate screen perspective; do not stretch the narrow doorway texture across the whole screen. If no valid scene is ready, use a restrained portal-colored transition effect.
3. Recommended interception: replace the selected receiving screen through `ScreenEvent.Opening` while this exact transfer is armed. A wrapper should preserve the selected screen's readiness/timeout lifecycle while supplying Elsebase imagery. This avoids assuming that outgoing Backdoor factory registration wins another dimension's incoming registration. Prove delegation/initialization in the spike; a guarded factory is the alternative if wrapping another custom screen is unsafe.
4. Handle both the temporary screen in `Minecraft.setLevel` and the subsequent screen bound to `LevelLoadStatusManager`. Keep the transfer active through that sequence; do not consume it at the first opening. Expire/cancel on mismatched world/player lifecycle, disconnect, failed transfer or timeout. Ordered payload/respawn handling must be tested, not assumed from server send order alone.
5. Once the normal destination world has received/compiled its arrival area, reveal it with a short blend (starting proposal: 100–200 ms). Continue checking real readiness; the separate preview mesh is not proof that the playable world's terrain is ready. Do not inject preview snapshots into the ordinary chunk cache in this version.
6. During a delayed handover, retain imagery and safe input handling rather than showing empty terrain. At a short delay threshold, show a small honest loading indicator; retain normal timeout/disconnect behavior and accessible narration. A screen does not pause a multiplayer server: test post-teleport motion/damage carefully and keep any additional movement grace bounded and server-authoritative, if evidence requires it.

Expected result: no standard terrain-loading backdrop/text during normal ready Elsebase crossings. Some transitions can still visibly pause, especially for cold chunks or high latency. The forced world-change frame, resource work and network wait cannot be eliminated just by changing screens. This plan does not promise zero-frame stalls or the physical continuity of a general portal engine. Preview `off` and transition `vanilla` remain independently usable.

## Implementation sequence after explicit approval

1. **Prove the rendering/transition slice:** one known target scene; transformed camera; clip plane; target light; source depth; both receiving-screen openings; actual separate server/client transition. Record video/frame evidence and timings. Stop for a design decision if limitations contradict the accepted visual scope.
2. **Unify destination descriptions:** shared orientation/effective anchor target and final transfer metadata; preserve safe return/body-contact invariants. Add production-level transform and lifecycle tests.
3. **Build bounded subscriptions and scene cache:** permissions, revisioning, leases, snapshots, changes, cleanup and preset budgets. Verify machine/fluid changes and no mirror cascades before increasing radius.
4. **Integrate doorway surfaces:** resource-pack-compatible fallback selection, parallax, transparency, target brightness, conservative 3D visibility and adaptive quality.
5. **Integrate transition presentation:** server-identified doorway transfers, image retention, readiness/timeout, fallback, selective scope and user compatibility setting.
6. **Qualify and document:** targeted regression checks, `build`, `runGameTestServer`, real client rendering and separate dedicated-server networking tests. Publish measured preset costs and compatibility boundaries; update implementation/player/development docs to reflect actual delivered behavior.

## Acceptance checklist

- Lateral/head movement changes the apparent destination perspective; walls, fluids and changed blocks align, including all cardinal orientations and both approach sides.
- Nearby source blocks correctly occlude the aperture; no purple double surface, clipping leak or recursive render. Resource reload, resize, theme changes and world changes release/rebuild resources correctly.
- Current anchor is previewed after moving it or recalling the inner doorway. Missing inner flooring/frame, falling through a portal, safe exit relocation, expiry and direct recovery retain their existing contracts.
- Darkness, torch light and bright Backdoor work independently of the source dimension. Known unsupported models/animations are documented and visibly evaluated using representative factory blocks.
- Slow/missing/out-of-order scene revisions, disabled previews and exhausted budgets degrade visually without preventing travel. Unauthorized clients cannot subscribe to another owner's instant destination or arbitrary coordinates.
- Preview leases do not propagate mirror loads; subscriptions stop and memory/tickets recover after repeated creation/removal/disconnect cycles. Measure cold and warm views, open multi-level rooms, outside terrain and simultaneous viewers.
- Recording of an ordinary Elsebase crossing shows no vanilla loading-screen frame, including the initial forced render. Commands, login, death and other portals still use their existing screen behavior. Test repeated rapid transfers and disconnect/timeout cases.
- Dedicated server starts without client-class loading; all existing domain/GameTests pass. Graphical acceptance requires a real client and cannot be claimed from GameTests alone.

## Decisions for discussion

1. Is the bounded block-scene first scope acceptable, including initially absent moving entities and unsupported animated/block-entity-only objects, or is full dynamic factory visibility required at the outset?
2. Keep exact anchor/safe-return behavior and use a short visual handover, as recommended, or redesign travel semantics toward physical continuity?

The investigation itself made no runtime changes. Implementation was subsequently authorized; delivered behavior and measured verification supersede these proposed budgets and sequencing. See [portal rendering](portal-rendering.md) and [development results](development.md).
