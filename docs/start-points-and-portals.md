# Start-point allocation and portal design

Status: design analysis, 2026-09-19. No gameplay implementation authorized.
The latest user requirements override conflicting proposals in the original specification. Recommendations below are not selected defaults until the user agrees.

## Confirmed requirements

- Choose each player's initial reference point using their UUID and the world seed (confirmed 2026-09-19). Target a server population of 100, negligible accidental proximity and coordinates comfortably away from precision problems. A radius of 1,000,000 blocks is a hypothesis to evaluate, not a selected value.
- Each player has their own starting area and at most one instant portal pair in total: one Backroom endpoint and one endpoint in the current external dimension. Territory protection belongs to claims mods; this is not a private dimension or a built-in claim system.
- Every player can use the instant portal from the beginning, without crafting or progression requirements.
- The reference point and the current Backroom portal position are different state. Summoning the portal must not rebind the reference point.
- Keep the external return endpoint stable while the player is inside the Backroom so they can return to where they entered.
- Rebind the Backroom reference point only by placing a special carpet-like, non-colliding anchor with a reusable tool. Replacing the anchor removes the old anchor; do not require repeatedly crafting anchor blocks.
- Permanent portals cost vanilla resources, potentially through a dedicated intermediate item to distinguish the recipe. They stay at their placed positions until removed using the appropriate tool.
- Investigate whether fixed Backroom portal positions offer enough performance benefit to justify limiting placement.

“Backdoor” in the latest conversation refers to the same dimension called “Backroom” in the original document; it is not a second dimension or a selected final name.

## 1. Why a million-block random radius is insufficient

Assume each UUID is well mixed into an independent, uniformly distributed point in a disk of radius R. Uniform radius is not uniform area: a correct disk sampler uses radius `R * sqrt(u)` and angle `2*pi*v`. A poor UUID hash or biased mapping could behave worse.

For 100 players there are `100*99/2 = 4,950` pairs. For a proximity threshold d small compared with R:

```text
P(one particular pair closer than d) ≈ (d/R)^2
expected close pairs λ ≈ 4,950 * (d/R)^2
P(at least one close pair) ≈ 1 - exp(-λ)
```

The last line is a Poisson approximation, not an independence claim about all pair events. The analysis script integrates the exact disk-overlap pair-distance distribution, includes edge effects, and independently simulates whole 100-player populations.

At R = 1,000,000:

| “Too close” means less than | Approximate probability of at least one close pair | Simulation, 20,000 populations |
| --- | ---: | ---: |
| 1,024 blocks | 0.517% | 0.530% |
| 4,096 blocks | 7.956% | 8.380% |
| 8,192 blocks | 28.182% | 27.990% |
| 16,384 blocks | 73.274% | 73.210% |
| 32,768 blocks | 99.471% | 99.505% |

Simulation values have sampling error. The JSON records standard errors; neither column is an exact 100-point probability. The bound `P(any close pair) <= min(1, 4,950 * P(one close pair))` is rigorous for the continuous model. At d=8,192 it gives an upper bound of 33.1034%.

For perspective, the simple union bound would guarantee a risk at most one in a million only with `R >= d * sqrt(4,950 / 0.000001)`: about 576 million blocks for d=8,192. This is a sufficient conservative radius, not an exact necessary radius, and is far outside Minecraft's normal world border. Inflating a random disk is the wrong mechanism for a guaranteed spacing contract.

The population means all distinct players with retained assignments over the world's lifetime, not just concurrent online players.

## 2. Recommended allocation: UUID-selected reserved slots

Use a finite lattice of chunk-aligned start slots, with a persistent server-side reservation registry:

1. A stable, versioned hash of the tuple `(allocator domain/version, world seed, full UUID)` selects a preferred slot and deterministic fallback order. Mix the complete inputs rather than simply adding truncated numbers. Do not truncate to `UUID.hashCode()` and treat it as a unique identifier.
2. On first use, reserve the first available slot in that order. Serialize allocation on the server to prevent simultaneous duplicate claims.
3. Persist `UUID -> slot` and its initial reference point. Rejoins and restarts use the saved assignment, not a recomputation.
4. Keep reservations when players log out or move their anchors. Reassignment/deletion needs an explicit future policy; do not place a newcomer inside an old factory.
5. If all allowed slots are unavailable, fail clearly. Do not silently reuse a slot or expand beyond the configured radius.

All candidate positions use `(spacing * i + 8, spacing * j + 8)` with `x² + z² <= radius²`. Since both spacings below are multiples of 16, every candidate has the same chunk-local position. Distinct slots are at least `spacing` apart. Exactly enumerated capacities:

| Radius | Minimum separation | Candidate slots |
| --- | ---: | ---: |
| 65,536 | 8,192 | 195 |
| 131,072 | 8,192 | 795 |
| 131,072 | 16,384 | 195 |
| 262,144 | 16,384 | 795 |
| 262,144 | 32,768 | 195 |

Initial recommendation, pending the user's preferred distance: **radius 131,072 and spacing 8,192**. This supports 100 assignments with considerable capacity headroom and zero spacing violations among distinct reserved slots. If 16,384 is desired, radius 131,072 already has 195 slots; 262,144 provides more lifetime-population headroom.

These counts are geometric capacities, not guaranteed usable-space counts in a modified world. First arrival still needs a safe entry cell and must respect world borders, build height, existing construction and protection. Keep the selected reference coordinate fixed; prepare a safe first-use cell without moving it arbitrarily and accidentally weakening the distance guarantee. Arrival safety offsets are a separate contract.

Trade-off: collision resolution depends on existing reservations, hence first-allocation order. The preferred candidate depends on the world seed, UUID and allocator version, but the final slot is not guaranteed to be identical on different servers with different reservation histories. A stateless bounded mapping from all UUIDs cannot guarantee unique locations: there are more possible UUIDs than available slots. Adding the seed gives a new world-dependent candidate ordering, not collision protection.

Different seeds normally produce different candidate choices; bounded slot collisions remain possible. Recreating a world with the same seed, UUID and allocator version reproduces the same candidate sequence. A restart or login must not randomize it, and saved assignments take precedence even if configuration/code later changes. A unique per-save salt could make same-seed worlds differ too, but is not part of the current requirement. The earlier probability analysis is unchanged for well-mixed uniform independent positions; adding a seed does not remove the birthday effect.

Spacing protects initial starts only. It neither creates private dimensions nor prevents players from walking toward each other, expanding factories, sharing space or deliberately placing a new anchor near another player. It is not a permission system.

## 3. Coordinate precision

Resolved Minecraft 1.21.1 / NeoForge 21.1.250 sources show:

- `Entity.java:164` stores position as `Vec3`; `Vec3` stores X/Y/Z as doubles.
- `LevelRenderer.java:1063,1093` subtracts the camera coordinates before passing block-entity translations into the rendering matrix stack.
- `WorldBorder.java:20,28` uses an absolute default limit of 29,999,984; the configured border can be much smaller.

Spacing between representable numbers for raw positive coordinates:

| Coordinate magnitude | Float ULP | Double ULP |
| --- | ---: | ---: |
| 65,536 | 0.0078125 blocks | 1.4552e-11 blocks |
| 131,072 | 0.015625 blocks | 2.9104e-11 blocks |
| 262,144 | 0.03125 blocks | 5.8208e-11 blocks |
| 1,000,000 | 0.0625 blocks | 1.1642e-10 blocks |

Values at powers of two are the spacing at/above that boundary; values immediately below it have half that spacing. ULP is representable spacing, not the expected observed rendering error.

One million is not a universal Minecraft precision failure boundary. Core double positions remain very precise. Absolute-world-coordinate float calculations in custom renderers/shaders can lose detail, while camera-relative or chunk-local calculations avoid that large-coordinate rounding. No guarantee about every third-party mod follows from the vanilla code inspection.

Use doubles for world positions, integer chunk/cell addressing and double subtraction before converting to local render floats. A smaller starting radius reduces exposure to poorly implemented third-party rendering but does not replace correct arithmetic. The start radius is not a new world border; later travel/anchor placement can go farther unless the user explicitly chooses a separate limit.

Primary numeric reference: [Java 21 Math.ulp](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html#ulp(float)). Local game evidence: resolved `build/moddev/artifacts/neoforge-21.1.250-sources.jar`.

## 4. Instant portal state and interaction

Confirmed separate concepts:

| State | Meaning | Allowed change |
| --- | --- | --- |
| Initial allocated slot | Original UUID-based start allocation | First allocation; retained thereafter |
| Reference point | Normal destination in the Backroom | Initial allocation or successful anchor placement |
| Current inner endpoint | Current Backroom side of the instant pair | Portal creation/recall |
| External return endpoint | Dimension, position and orientation of the departure doorway | Explicit creation/relocation outside; frozen while inside |
| Anchor marker | Visible floor marker for the selected reference | Reusable tool replaces the previous marker |

Confirmed by the user on 2026-09-19: **one instant pair per player total** (one Backroom endpoint, one endpoint in the current external dimension). Do not retain an additional instant endpoint in every previously visited external dimension. Permanent portals are separate from this instant-session limit.

Current interaction proposal, incorporating the confirmed pair limit:

| Player location / action | Expected result |
| --- | --- |
| Outside, first key press | Place an entry near the player and its counterpart at the reference; walking through performs traversal |
| Outside, key press with an existing pair | Move the external entry to the player; set the inner destination to the reference, retire replaced endpoints atomically |
| Inside, key press | Recall only the inner endpoint near the player; preserve external return endpoint and reference |
| Walk through recalled inner endpoint | Return to the exact external doorway, with a safe arrival offset |
| Place anchor with the tool inside | Change only the reference/anchor; do not move the external return location |

This changes the original keybind proposal from open/close toggle to summon/recall. A separate explicit close action remains to be defined. Do not interpret a repeated key press as destructive close. Ordinary action remains “press key, walk through,” rather than silently changing to immediate teleportation.

Returning outside then reopening to the reference provides an intentional indirect return-to-base route. It still has no direct Backroom-to-Backroom portal pair, but the original broad “no fast travel” language needs this explicit qualification.

Failure contract proposal: validate new placement before retiring the old working pair; do not overwrite machines or silently choose a different outside destination. Rate-limit requests and reject conflicting actions while traversal is being committed. If a player has no stored outside return destination (for example first entering via someone else's permanent portal), the instant key needs an explicit recovery rule; do not invent an origin.

Restart/logout/death policy is still open. Recommendation: persist pair/reference/return state, release unnecessary tickets when neither endpoint is normally relevant, and restore usable endpoints on demand. Do not destroy the only return route merely because of a disconnect.

## 5. Anchor lifecycle

The confirmed anchor is a visible but non-colliding floor marker, created with a reusable tool. It is not an item that needs to be consumed every time, a world spawn/respawn point, a claim boundary, or a chunkloader.

Design proposals:

- One current anchor per player in the Backroom, including a facing for predictable portal orientation. Select it through a thin outline even though its collision shape is empty.
- Check floor support, portal/arrival clearance and ownership before committing a replacement. A failed placement preserves the old reference and anchor.
- A server-owned anchor revision makes only the latest marker active; cloned blocks must not create multiple references.
- If the old marker is unloaded, immediate physical removal requires briefly loading that old chunk. Alternatively it can be logically invalidated and physically removed when that chunk loads; the latter preserves one active anchor but is not literal immediate block removal. Choose this trade-off before implementation. Neither method needs a permanent chunk ticket or per-tick anchor update.
- Do not drop reusable anchor items on removal. Tool cost/durability and removal/break behavior remain open. Removing a marker must not silently lose the only valid return location.
- Placing an anchor while the instant portal is recalled elsewhere changes the next reference destination, not the external return point. Whether to visibly move the current inner portal immediately remains a UX decision; recommendation: apply on the next outside summon.

## 6. Permanent portal placement and performance

Recommendation: allow arbitrary valid locations, with cardinal orientation, fixed initial dimensions and preferably portal/arrival footprints contained in one horizontal chunk. A placement preview can explain a chunk-boundary restriction if it becomes necessary. Do not pre-generate fixed sockets or force long walks to a doorway solely on the assumption that fixed coordinates are faster.

Reasoning from the current APIs, not a benchmark of nonexistent portal code:

- Both fixed and freely placed portals can be indexed by dimension and chunk. Nearby traversal candidates can be found through bounded spatial lookup; no world-wide per-tick scan is required.
- Static frames and anchor markers do not require ticking block entities. Costs come from active work, not the number or magnitude of their coordinate values.
- Keeping portal geometry within one horizontal chunk simplifies placement, endpoint loading and removal. That constraint can be checked anywhere; it does not require preassigned locations. Ticket propagation may still load neighboring chunks, as already documented in D02.
- Grouping many portals into a hub could share loaded chunks, but only if those portals are actually clustered. A fixed socket in every distant player's base does not provide that saving.
- Live previews cost destination synchronization, visible render passes, pixels and rendered terrain/BlockEntities. Fixed sockets in guaranteed closed rooms could constrain visibility. Once walls can be removed, that guarantee disappears. The first static-surface version avoids the extra world render entirely.
- Far-apart bases generate/load their local chunks on demand; Minecraft does not load all intervening terrain because two players are far apart. `ChunkMap.isChunkTracked` and player view-distance logic scope normal client delivery to local tracking regions. Widely separated active factories still cost their independent loaded regions; a smaller world radius does not merge those regions by itself.

Before changing placement freedom, measure freely placed versus constrained portals under identical portal count, visibility, destination load radius and machine load. No percentage speedup is claimed without that experiment.

Recipe direction only: vanilla iron/redstone/copper or another agreed small ingredient set -> a mod-specific component -> permanent frame/tool. A custom intermediate/output alone does not guarantee recipe disambiguation; choose a distinct ingredient pattern and verify it against the eventual target modpack. No recipe or progression beyond immediate instant access is selected yet.

## Reproduction and acceptance

Run `node docs/analysis/start-point-analysis.mjs`; saved output is [start-point-results.json](analysis/start-point-results.json). Node 22.18.0 completed the calculation and 20,000-population simulation. Integral sanity checks: d=0 yields 0; d=2R yields 1 within 1.3e-10 numerical error.

Future implementation checks: 100 UUID allocations with forced hash collisions, deterministic reload, simultaneous joins, full-slot exhaustion, negative coordinates, changed configuration without moving existing bases, anchor relocation while old chunks are unloaded, occupied arrival space, logout/restart while inside, outside dimension changes and two users recalling portals simultaneously. These tests are planned, not implemented or run.
