# Themes and essential configuration

Status: implementation authorized on 2026-09-20. All seven themes have initial vanilla-texture resource-pack palettes; Quiet Workshop is the implementation default. The historical proposals below are superseded where necessary by [the implementation contract](implementation.md), which records actual settings, bounds and third-party customization. One visual style per modpack remains the scope.

## 1. Feasibility and scope

**A replaceable visual skin for the whole mod is straightforward if assets are designed for it from the start. Arbitrary, simultaneously different skins in different players' areas require additional architecture.**

Minecraft separates client resources (`assets/`) from server data (`data/`):

| Change | Delivery mechanism | Boundary |
| --- | --- | --- |
| Wall/floor/ceiling textures, block/item models, text, existing sound assets | Resource pack | Changes presentation, not server collision or gameplay |
| Recipes, loot and supported tags | Datapack | Uses the appropriate existing Minecraft/NeoForge systems |
| Which existing blocks the room generator places, doorway distribution and layout presets | Datapack through a deliberately implemented generator/profile schema | This mod must implement the consumer; a JSON file alone adds no behavior |
| New independent blocks/items, collision behavior or mechanics | Addon code, or a specifically designed mod extension API | Not provided by an ordinary texture pack or plain vanilla datapack |

NeoForge's resolved `AddPackFindersEvent.addPackFinders` supports optional bundled resource/data packs. We can supply built-in styles selectable in Minecraft's existing pack screen and a documented resource-pack template for third parties. A separate theme menu or a duplicate config selector is not necessary initially.

A server can distribute a matching resource pack, or a modpack can bundle it. Installing a datapack on the server does not deliver image/audio files to clients. Package author instructions must distinguish installing the server data and client assets even if one download contains both parts. Ordinary resource-pack priority determines which assets win when several packs override the same paths.

## 2. Recommended first implementation after approval

Keep one set of neutral functional blocks, e.g. floor, border, corner, partition, ceiling, light, frame and anchor. These roles are proposed contracts, not existing registered block IDs.

- Use normal baked block models for room surfaces and static frame parts. Avoid adding a ticking/rendered block entity to every decorative block.
- Expose stable asset paths and texture variables for each role; share parent models where useful. Third-party authors can replace the art without understanding portal code.
- Target all seven illustrated styles as interchangeable appearances, as requested on 2026-09-20. One will provide the default appearance and the remaining six can be optional bundled resource packs; the default is undecided. Produce actual textures and compatible models from the concepts. Simplify illustrative details where needed to preserve common gameplay, geometry and performance. This supersedes the earlier proposal to provide only three appearances initially; delivery sequencing remains to be agreed.
- Represent the anchor consistently as a thin, selectable floor marking with no collision: workshop address plate, magic seal, woven knot or mosaic. The skin does not change its function.
- Keep safe collision/occlusion, real light emission, structural identity and tool behavior constant across purely cosmetic skins. A painted rune can reuse the existing light block; a different light-emission value is a gameplay change.
- Models may add shallow detail, but changing a solid wall into a visibly open arch while retaining full-block collision is not acceptable. Actual arch geometry needs matching implemented blocks and generator support.
- Every theme must preserve readable cell boundaries, identifiable editable panels and clear portal/anchor states. Distinguish roles by shape/pattern as well as color.

Accepted scope: choose one skin for the modpack as a whole. No per-player selection or simultaneous per-area theme system is needed. The recommended delivery mechanism is a resource pack. A client's personal resource-pack override would change how that client sees all matching blocks, including other players' areas; it would not assign an appearance to only that player's base.

Changing a pure skin can restyle existing mod blocks without rewriting the world. It does not recolor arbitrary machines or blocks supplied by other mods unless a pack explicitly replaces their assets too.

## 3. Extension options and trade-offs

| Option | Relative effort | Benefit | Limitation |
| --- | --- | --- | --- |
| A: global resource-pack skins | Low incremental code; art still needs production and QA | Easy pack-author customization, existing pack controls, no world rewrite | No simultaneously different per-area appearances from the same block states |
| B: datapack generation palettes using existing compatible blocks | Moderate | Different actual building materials, better use of a modpack's existing art | Old chunks retain blocks; changing the palette affects new generation and needs an explicit restoration contract |
| C: a finite set of built-in theme variants | Moderate | Different themed rooms can coexist through registered blocks/states | Third parties can reskin the slots, but cannot add unlimited new state values using JSON |
| D: extensible per-area theme IDs plus a custom cached model/data system | Substantial | Arbitrary third-party themes can coexist in one world | Persistence, networking, cache invalidation, chunk rebuilds and boundary handling become part of the mod |

Recommendation: **A**, matching the accepted pack-wide scope. B, C and D are comparison material, not planned requirements. Keep generator material selection cohesive without adding speculative theme registries. None of these options needs live portal previews.

The same world can show completely different block types through option B without a custom renderer, provided the blocks already exist and meet the structural contract. However, selecting a different material profile per player is not just a player preference: areas have no hard borders and players can meet. The generator would need a stable coordinate-to-region/theme rule, with persisted assignments independent of who loads a chunk first. Do not infer theme from the nearest online player or current anchor position.

Do not add ordinary stone/wood or arbitrary third-party machine blocks to global bulk-removal tags merely to support palettes. Material identity alone would then let the tool remove player construction. The initial supported structural palette should remain this mod's dedicated structural blocks; broad third-party palettes need an explicit provenance/editability contract. Reject block entities, inventories, fluids and valuable resource blocks as default freely restorable material choices. Do not introduce free-resource loops via themed restoration.

NeoForge supports custom datapack registries and optional codec-based client synchronization. Such registries are loaded with the world; do not promise arbitrary theme/worldgen changes through `/reload`. Metadata synchronization is not texture distribution. Minecraft block-state values are constructed as a fixed state set, so adding a new theme name to a datapack cannot automatically extend an enum property.

Missing cosmetic assets should have a diagnosable visual fallback. Missing gameplay palette/profile definitions referenced by a save must not silently replace blocks or shift generation. Validate IDs, dependencies and structural constraints, and explain the offending definition before generating affected chunks.

## 4. Requested styles for different modpacks

All names below are theme labels, not selected mod names. On 2026-09-20 the user accepted all seven visual directions and requested their inclusion where feasible, with the same gameplay and grid. Exact assets, richer geometry, sounds or decorative entities are not implied commitments. No default style is selected yet.

See the [seven-image gallery](theme-gallery.md) for comparable architectural studies. These are generated concepts, not in-game screenshots.

| Theme | Materials and palette | Portal / floor anchor | Narrative and fit |
| --- | --- | --- | --- |
| Quiet Workshop | Warm ivory panels, slate, muted teal, a little copper | Framed workshop doorway / address plate | A dependable workspace just outside ordinary geography; technology and mixed packs |
| Arcane Archive | Pale ashlar, dark blue inlays, restrained brass and violet runes | Inscribed rectangular stone threshold / binding seal | An ever-extending annex maintained by forgotten archivists; scholarly magic and alchemy |
| Verdant Cloister | Moss-green accents, terracotta, pale stone, timber-patterned panels | Carved square lintel / woven leaf-knot marking | A sheltered retreat tended by an unseen order; nature magic, farming and cozy packs |
| Astral Observatory | Deep blue stone, chalk-white grid lines, small silver star motifs | Celestial measurement frame / compass rose | Rooms charted between constellations; astral magic and exploration, with no actual moving sky required |
| Deepstone Halls | Cut basalt, warm sandstone highlights, bronze corner details | Heavy block lintel / mason's mark | An unfinished guildhall beneath the maps; dwarven, medieval and mining packs |
| Porcelain Sanctuary | Cream tile, red-brown wood motifs, jade accents, warm lantern textures | Lacquer-like rectangular frame / simple woven seal | A quiet extradimensional guesthouse/workroom; restrained fantasy without a technical backstory |
| Service Layer | Cool concrete, graphite, amber guidance marks and numbered bays | Maintenance opening / access stamp | The hidden infrastructure behind the world; liminal, technical or science-fantasy packs |

Use static inset lights and surface patterns for the first art pass. Hanging vines, bookshelves, archways or lanterns that protrude into usable space need actual model/collision evaluation. Do not add constant particles or ticking decorative entities to sell the magical appearance. Texture resolution and animation count still affect client cost; equal gameplay does not imply equal rendering cost for arbitrary third-party packs.

The user selected **Elsebase** as the theme-neutral mod name; see [the naming record](naming-and-logo.md). The earlier Elsewhere suggestion was superseded after an existing Minecraft mod with that name was found, and Hingespace was rejected. The user selected the first Elsebase logo direction, The doorway beyond; production assets remain unfinished. The shared story can simply be an enduring place outside ordinary geography whose appearance depends on the pack. Avoid a mandatory industrial corporation origin, machines, power systems or a magic-specific cosmology in core gameplay. The earlier three-route concept image remains a historical industrial exploration, not the full new scope.

## 5. Small configuration proposal

Expose settings only when they change a meaningful pack/server policy or bound expensive work. The revised proposal has **six operating settings, two allocation-initialization settings, one lighting setting and one client setting**. Values and lifecycle choices are starting proposals, not measured performance guarantees or accepted defaults. The corrected 2026-09-20 protection requirement supersedes the earlier explosion-damage toggle: protect the Backdoor's generated/restored structural walls, floors and ceilings, while player-placed blocks follow normal rules.

### Server/world operating settings

| Proposed key | Proposed default | Purpose |
| --- | --- | --- |
| `access.mode` | `instant_and_permanent` | One choice: both access types, instant only, permanent only, or closed to new connections. Normal default retains instant access from the start. |
| `portals.excludedExternalDimensions` | Empty list | Pack authors can prohibit creating/relocating entrances in challenge dimensions without a large permission subsystem. |
| `portals.maxPermanentPairsPerPlayer` | 4 | Bounds persistent infrastructure per player; distinct from the fixed one-instant-pair invariant. |
| `chunkloading.maxMirroredEndpointChunks` | 256 | Global budget for distinct `(dimension, endpoint chunk)` mirror targets. `0` disables proactive mirror loading. |
| `structure.maxChangedBlocksPerTick` | 1024 | One global structural-work budget shared across players, not a full budget for every request. |
| `world.allowNaturalMobSpawning` | `false` | Default calm work dimension; permitting natural spawns still requires appropriate biome entries and normal spawn conditions. Does not blanket-disable mod machines or spawners. |

Budget details belong to the implementation contract, not more exposed knobs:

- Mirror budget counts direct endpoint chunks, not all chunks indirectly affected by Minecraft tickets and not the total server memory footprint. Shared targets share a ticket/reference count. Capacity exhaustion uses a clear inactive/loading portal state; no blind unsafe teleport. Completed/closed pairs release references. Transient arrival loading has a separate internally bounded queue and is not an unbounded loophole when proactive loading is off.
- A structural operation validates all target blocks and permissions before committing a logical panel. Queue whole panels and commit only when the current tick's remaining budget can cover them; revalidate before committing. Require the configured budget to cover the largest supported individual panel and reject incompatible generator-profile/budget combinations clearly. Never accumulate allowance and then exceed the configured per-tick maximum. Hard internal panel-size/request/rate limits remain fixed and bounded. This proposal prioritizes whole-panel restoration over partly visible walls. A block-count budget does not strictly bound milliseconds when other mods react to block changes; profile the eventual implementation.
- Lowering a limit or disabling access does not delete saved portals, unregister blocks, erase reference points or move bases. Prevent new allocations first and reconcile tickets safely. Preserve a controlled return route for players already inside, including when the old outside dimension is now excluded. Do not enable a new bypass into a denied dimension through this exception; tie it to the pre-existing return endpoint.
- Endpoints cannot evade permanent-pair limits by transferring ownership to an unowned/public bucket; the placing owner remains the accounting owner unless an explicit admin transfer is performed.

### World-initialization settings

| Proposed key | Proposed default | Lifecycle |
| --- | --- | --- |
| `allocation.radius` | 131072 | Snapshot when allocation is initialized; not a live coordinate remap |
| `allocation.minimumSpacing` | 8192 | Snapshot with radius and allocator version; positive chunk-aligned spacing |

The radius/spacing recommendation is still open. Validate their combination and actual usable slot capacity. Reject ordinary edits to a world's frozen allocation parameters rather than silently changing the spacing guarantees for newcomers. A future explicit expansion/migration can preserve old reservations. Seed and UUID are internal inputs, not two extra config fields; do not expose the raw world seed through config synchronization.

### Lighting setting

The user requested this feature and accepted the generation-change lifecycle below; the key and default remain proposals.

| Proposed key | Proposed default | Purpose |
| --- | --- | --- |
| `world.darkness` | `false` | When enabled, remove ambient brightening, provide no skylight and generate no light-emitting fixtures or other automatic light sources. Player-placed torches, lamps and luminous machines retain normal behavior. |

Keep darkness independent of `world.allowNaturalMobSpawning`: both must be enabled for the intended dark survival variant; normal difficulty, spawn rules and biome entries still apply. Use the same rule across all seven skins. Portal/anchor art must not silently restore room illumination; avoid forced full-bright structural models in bundled dark appearances. Darkness means an unlit environment, not a promise of a pitch-black display despite gamma settings, night vision or third-party shaders.

Accepted generation-change policy (2026-09-20): no automatic retroactive migration or regeneration is required for lighting or other generation-affecting options. Users choose a new world for consistent results or accept artifacts in an existing world. For example, old generated lamps may remain while new chunks omit lamps; do not silently remove existing fixtures or player construction. Clearly describe this consequence beside affected config entries. Apply generation changes to subsequently generated content; whether a particular setting requires a restart must be documented honestly. Global presentation changes can affect existing areas without rewriting their blocks. Structural restoration uses the applicable current generation settings without rewriting untouched panels.

This policy concerns generation consistency, not save integrity. Keep allocator reservations, ownership and references stable, and reject parameter changes that would invalidate those contracts. The existing frozen allocation radius/spacing rule remains separate. Do not promise a globally dark existing dimension after a config change when old lights still exist.

### Client settings

Initially expose only `audio.ambientVolume` (proposed 0.35, range 0–1, with Minecraft's master/category volume still applied) if custom ambience is actually implemented. Keybindings belong in Minecraft controls and skins in resource packs. No empty setting should appear before its feature exists.

Once live preview exists, add a single `render.previewQuality` choice: `off`, `low`, `balanced`, `high`. Each preset sets a tested combination of resolution, update rate, visibility radius and maximum simultaneous previews; traversal remains available in `off`. Avoid a dozen separate rendering sliders initially. Use actual measurements to define presets and preserve a static fallback under budget pressure.

### Keep out of the ordinary config

- Textures, palettes, models, terminology and sounds: resource packs.
- Recipes, loot and pack-specific ingredient tags: datapacks.
- Detailed wall/opening probabilities and generator palette definitions: named, validated generation profiles in datapacks once supported; no parallel TOML copy of each field. The selected profile belongs to the saved generator/world configuration. Never silently rebuild existing chunks to apply it.
- Per-player references, reservations and anchor state: saved data.
- Territory protection: claims mods, while this mod checks ownership for personal actions and respects protection integrations for placement/removal/bulk editing.
- Protection of generated/restored Backdoor structure against accidental destruction: a fixed requirement, not a dimension-wide explosion cancellation. Player construction follows normal rules; deliberate automated mining is permitted. See [the corrected protection contract](design-discussion.md#d09--structural-block-protection).
- Exactly one instant pair, no mirror-load cascades, no machine overwrites, server authority and safe arrival: fixed correctness contracts, not switches.

Use NeoForge `SERVER` config for world-authoritative runtime policy and `CLIENT` config for presentation preferences. Implement the stated initialization/reload restrictions explicitly; choosing a config type does not automatically protect saved generation parameters.

## 6. Evidence and remaining verification

Checked against Minecraft 1.21.1 / NeoForge 21.1.250 sources in `build/moddev/artifacts/neoforge-21.1.250-sources.jar`: `PackType`, `AddPackFindersEvent`, `DataPackRegistryEvent`, `StateDefinition`, `BlockBehaviour` and world-loading code. No theme code, datapack loader, claim integration or config has been implemented or benchmarked.

Additional source inspection on 2026-09-20: `DimensionType` exposes `has_skylight`, `ambient_light` and monster light settings; `LightTexture.getBrightness` uses ambient brightness for rendering, while `Monster.isDarkEnoughToSpawn` checks actual sky/block light and dimension spawn thresholds. `ExplosionEvent.Detonate` permits editing affected block positions without cancelling the entire explosion or removing affected entities. These are available mechanisms, not a completed implementation or universal mod compatibility guarantee. Model-level light overrides can also affect visual darkness, as described in the linked model documentation.

Primary references, checked 2026-09-19:

- [Resources: assets versus server data](https://docs.neoforged.net/docs/1.21.1/resources/)
- [Block and item models](https://docs.neoforged.net/docs/1.21.1/resources/client/models/)
- [Datapack registries and lifecycle](https://docs.neoforged.net/docs/1.21.1/concepts/registries/#datapack-registries)
- [Configuration types](https://docs.neoforged.net/docs/1.21.1/misc/config/#configuration-types)

Before shipping third-party theme support: test an external resource pack, pack precedence, resource reload, missing assets, default fallback, server/client mismatch, dedicated server startup and old-world preservation. Test the chosen claims integrations with bulk edits and portal/anchor replacement; merely calling `setBlock` does not establish protection compatibility.
