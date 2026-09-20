# Backroom Industry — Design & Technical Specification

**Target:** Minecraft Java Edition 1.21.1  
**Recommended loader:** NeoForge  
**Document purpose:** Implementation handoff for Codex / software agent  
**Status:** Design specification  
**Language:** English for implementation clarity

---

## 1. Executive Summary

Backroom Industry adds a dedicated, effectively infinite industrial dimension intended for large modded Minecraft bases.

The mod solves a specific gameplay problem:

- Large modded factories are expensive to relocate.
- Players often want to explore and live in visually interesting locations.
- Building a permanent industrial base in the overworld creates unwanted location lock-in.
- Conventional teleporters add friction and make the industrial base feel like a remote location instead of an adjacent room.
- Fully seamless portal systems such as Immersive Portals are powerful but comparatively invasive and can create compatibility and stability issues.

Backroom Industry therefore provides:

1. An infinite, purpose-built industrial dimension.
2. A modular 16×16 horizontal room grid aligned exactly to Minecraft chunks.
3. Removable walls and ceilings so players can reshape the generated rooms without rebuilding everything manually.
4. Permanent cross-dimensional thresholds that visually look into the other side using a lightweight render-to-texture portal preview.
5. A keybind that creates a temporary portal pair for frictionless access while exploring.
6. Strict portal topology rules so the mod does not become a generic teleportation network.
7. Minimal built-in chunkloading: if one portal endpoint is normally loaded, its direct counterpart is kept loaded so the transition remains responsive.

The primary UX goal is:

> The industrial dimension should feel like a large room directly behind a doorway, not like a separate world reached through a teleporter.

---

## 2. Core Design Principles

### 2.1 The industrial base is permanent; its entrances are not

The player's machines, multiblocks, cables and storage systems remain in the Backroom dimension.

Players do not move their factory when they relocate their overworld home.

Instead, they move or create a new entrance.

This makes expensive factory construction location-independent without requiring arbitrary block serialization or relocation.

### 2.2 Entering the Backroom must be nearly frictionless

The user should not experience the interaction as:

1. Open menu.
2. Select destination.
3. Activate teleporter.
4. Wait.
5. Arrive.

The intended interaction is:

1. See the Backroom through an open threshold.
2. Walk through it.
3. Continue moving.

For temporary portals:

1. Press a key.
2. A portal appears.
3. Walk through.
4. Return through the same portal.
5. Press the key again when finished.

### 2.3 Portal rendering is an illusion, not a general portal engine

The mod must not attempt to reproduce all functionality of Immersive Portals.

It only needs to support a highly constrained case:

- one rectangular threshold,
- paired with one rectangular threshold in another dimension,
- no scaling,
- no arbitrary geometric transformations,
- no recursive portal rendering,
- no same-dimension connections,
- no direct portal-based item/energy/fluid transport,
- no portals used as generic fast travel.

This constrained scope is a deliberate compatibility and maintainability decision.

### 2.4 Functionality has priority over decoration

The Backroom dimension should look coherent and pleasant enough to live in, but it is fundamentally infrastructure.

Visual design should improve usability:

- chunk boundaries should be readable,
- room segmentation should be obvious,
- walls should be easy to identify,
- spaces should support large machinery,
- decorative details must not interfere with automation.

### 2.5 Most organizational decisions should remain reversible

Players should be able to:

- remove one wall section instantly,
- remove multiple connected wall sections,
- restore wall sections,
- remove ceiling sections,
- restore ceiling sections,
- create larger halls from small rooms,
- later subdivide those halls again.

The Backroom itself should never create the same commitment anxiety it is intended to solve.

---

## 3. Scope

### 3.1 In scope

- Dedicated Backroom dimension.
- Deterministic procedural room generation.
- Infinite horizontal world generation.
- 16×16 horizontal room cells aligned to chunks.
- Removable walls.
- Removable ceilings.
- Fast structural editing tool.
- Permanent portal thresholds.
- Temporary player-created portal sessions.
- Lightweight portal preview rendering via secondary camera and render texture.
- Cross-dimension entity transition for players.
- Direct counterpart chunkloading for active/loaded portals.
- Configuration options.
- Multiplayer-safe ownership and permission model.
- Safe persistence across restarts.
- Graceful fallback if portal preview rendering fails or is disabled.

### 3.2 Explicitly out of scope

- Generic teleportation network.
- Same-dimension portals.
- Direct overworld-to-nether portal links through this mod.
- Backroom-to-Backroom portal links.
- Recursive portal rendering.
- Portal-based item transport.
- Portal-based fluid transport.
- Portal-based energy transport.
- Built-in general-purpose chunkloading system.
- Base capture / structure serialization.
- Base relocation.
- Dynamic moving portals.
- Portal scaling.
- Arbitrary portal shapes.
- Non-Euclidean recursive spaces.
- General entity interaction through portals in the MVP.
- Raycasting through portals in the MVP.
- Walking halfway through a portal while simultaneously existing in both dimensions.

---

## 4. Target Platform

### 4.1 Minecraft

Target:

- Minecraft Java Edition 1.21.1

### 4.2 Mod loader

Preferred:

- NeoForge 1.21.1

Implementation should follow current NeoForge lifecycle, registration, data-generation and networking conventions.

### 4.3 Java

Use the Java version required by Minecraft 1.21.1 and the selected NeoForge version.

### 4.4 Compatibility priorities

Primary compatibility targets:

- major technology mods,
- large BlockEntity counts,
- shader-off vanilla renderer,
- common performance mods where feasible,
- multiplayer servers,
- normal modded chunkloaders.

The mod should avoid assumptions about specific technology mods.

---

## 5. Terminology

### Backroom

The dedicated industrial dimension provided by this mod.

### Cell

A horizontal 16×16 block area aligned exactly to one Minecraft chunk.

A cell is the fundamental room-layout unit.

### Section

A Minecraft `LevelChunkSection`, i.e. a 16×16×16 vertical chunk section.

The room system intentionally uses 16-block vertical increments where useful, but loading remains chunk-column based.

### Partition

A generated or player-restored wall panel between adjacent cells.

### Ceiling Panel

A generated 16×16 ceiling segment associated with one cell.

### Portal Endpoint

One side of a portal pair.

### External Endpoint

A portal endpoint in any dimension other than the Backroom.

### Backroom Endpoint

A portal endpoint located in the Backroom.

### Permanent Threshold

A persistent portal pair created as infrastructure.

### Temporary Portal Session

A player-owned portal pair created using a keybind and retained until explicitly closed.

### Normal Chunk Load

A chunk loaded by normal Minecraft rules, a nearby player, or another mod's chunkloader.

### Portal Mirror Load

A chunk loaded only because it contains the opposite side of a currently relevant portal.

---

## 6. Fundamental Portal Invariant

Every portal pair MUST satisfy:

> Exactly one endpoint is located in the Backroom dimension.

Valid examples:

- Overworld ↔ Backroom
- Nether ↔ Backroom
- End ↔ Backroom
- Modded Dimension X ↔ Backroom

Invalid examples:

- Overworld ↔ Overworld
- Overworld ↔ Nether
- Nether ↔ End
- Backroom ↔ Backroom

This rule must be enforced server-side.

The player may indirectly travel from one external dimension to another by physically moving through the Backroom and entering another portal, but the mod must never provide a direct external-to-external connection.

---

## 7. Backroom Dimension

### 7.1 General properties

The Backroom should be:

- horizontally infinite,
- deterministic,
- safe for base construction,
- mostly flat,
- free from natural hostile mob spawning by default,
- free from weather,
- free from normal ore/resource generation,
- free from villages and unrelated vanilla structures,
- suitable for machines and multiblocks,
- dimensionally persistent,
- compatible with normal BlockEntities.

Recommended properties:

- fixed or visually stable ambient lighting,
- normal gravity,
- normal redstone behavior,
- normal block updates,
- normal fluids unless configured otherwise,
- normal explosion behavior unless configured otherwise,
- beds disabled or handled explicitly,
- no natural world progression resources.

The dimension is infrastructure, not a resource dimension.

### 7.2 Floor level

Recommended default:

- floor top surface at Y = 64

This leaves substantial space below and above for future design flexibility.

The exact Y level should be configurable or at minimum centralized in constants.

### 7.3 Default room height

Recommended default interior height:

- 16 blocks

For example:

- floor surface: Y = 64
- room interior: Y = 65..80
- ceiling panel around Y = 81

Exact block boundaries may be adjusted to make wall and ceiling construction convenient.

### 7.4 Vertical expansion

Removing a ceiling panel should expose a large open build volume above the room.

Do not generate another normal room directly above every cell.

The design goal is:

- horizontal cells organize factories,
- removing ceilings creates tall multiblock halls.

The free volume above should support very tall structures.

A future optional mode may generate multiple vertical industrial layers, but this is not part of the MVP.

---

## 8. 16×16 Cell Grid

### 8.1 Alignment

Every cell MUST align exactly to a Minecraft chunk:

```text
cellX = chunkX
cellZ = chunkZ
```

Cell world bounds:

```text
minX = chunkX * 16
maxX = minX + 15
minZ = chunkZ * 16
maxZ = minZ + 15
```

### 8.2 Why chunk alignment matters

This alignment provides:

- immediate chunk awareness,
- predictable machine placement,
- predictable interaction with external chunkloaders,
- simple generation rules,
- simple partition ownership,
- cheap portal preview culling,
- clear spatial organization.

### 8.3 Floor markings

The floor should communicate chunk boundaries without looking like a debug overlay.

Requirements:

- every 16-block boundary must be visually recognizable,
- chunk corners should be distinguishable,
- the pattern should be attractive enough for long-term use,
- the pattern must not create visual noise,
- markings should remain visible under typical machine layouts.

Possible design:

- base industrial floor block,
- thin border strip on chunk edges,
- distinct corner tile,
- subtle repeating 4×4-cell macro-pattern,
- optional coordinate marker block at cell corners or centers.

Do not rely on F3+G.

---

## 9. Room Generation

### 9.1 Concept

The dimension begins as a semi-random labyrinth of industrial rooms.

The grid itself is regular.

The presence or absence of walls is procedural.

This gives the player an initial sense of exploration without making the dimension structurally inconvenient long-term.

### 9.2 Shared-edge generation

Wall state must be determined from the shared edge between adjacent cells.

Never independently roll the same boundary from both chunks.

For an edge between cells A and B, derive a canonical edge key from:

- world seed,
- lower canonical cell coordinate,
- edge axis/direction.

Example conceptual key:

```text
hash(seed, minCellX, minCellZ, axis)
```

The result determines the initial edge state.

### 9.3 Initial edge states

Each shared boundary may generate as:

- FULL_WALL
- WALL_WITH_OPENING
- OPEN

Suggested configurable probabilities:

```text
FULL_WALL          0.45
WALL_WITH_OPENING  0.40
OPEN               0.15
```

These values are placeholders and should be data-driven/configurable.

### 9.4 Connectivity guarantee

Pure independent randomness can generate isolated cells.

The generator should guarantee practical traversability.

Recommended approach:

1. Divide the world conceptually into deterministic macro-regions, e.g. 8×8 or 16×16 cells.
2. Generate a deterministic spanning network inside each macro-region.
3. Guarantee at least one traversable connection between adjacent macro-regions.
4. Add additional random openings afterward according to style probabilities.

The result should feel random while remaining navigable.

### 9.5 Doorway generation

For `WALL_WITH_OPENING`:

- opening width: configurable, default 3 blocks,
- opening height: configurable, default 4 blocks,
- horizontal position: deterministic random within safe bounds,
- avoid openings immediately intersecting structural corner details.

Occasional wider industrial openings are desirable.

For example:

```text
80% normal doorway
15% wide opening
5% almost fully open wall
```

### 9.6 Structural determinism

The same seed and coordinates MUST generate the same original room structure.

Player edits override generation and persist normally as block changes.

No custom per-wall persistence is necessary if the world is represented by actual blocks after generation.

---

## 10. Wall and Ceiling Blocks

### 10.1 Partition blocks

Generated partitions should use dedicated mod blocks/tags.

Requirements:

- visually coherent,
- easy to identify,
- mineable normally,
- quickly editable with the dedicated tool,
- safe to distinguish from player construction,
- not accidentally affected by bulk tools after being replaced with normal blocks.

Use tags such as:

```text
backroom_industry:partition_blocks
backroom_industry:ceiling_blocks
backroom_industry:floor_blocks
```

### 10.2 Fast edit safety

The bulk edit system MUST operate only on whitelisted structural blocks/tags.

It must never remove:

- machines,
- cables,
- pipes,
- storage,
- player walls,
- arbitrary mod blocks.

### 10.3 Ceiling

Every initial cell has a removable ceiling panel.

The ceiling panel is logically 16×16.

Removal should be fast via the structural tool.

Normal block breaking may also work.

### 10.4 Restoration

The structural tool should also restore original partition or ceiling material.

This makes room organization reversible.

Restoration must refuse to overwrite occupied blocks unless explicitly configured.

Default behavior:

- preview or fail if non-replaceable blocks occupy the target volume.

---

## 11. Partition Tool

### 11.1 Purpose

The Partition Tool allows players to reshape the generated Backroom without tedious block-by-block editing.

### 11.2 Required modes

#### Panel mode

Removes or restores exactly one logical 16×16 wall panel.

#### Connected Run mode

Removes or restores a contiguous run of compatible wall panels along the same plane.

The operation stops at:

- a corner,
- a perpendicular structural intersection,
- an already open edge,
- a configured maximum operation length.

Do not use unconstrained block flood-fill.

#### Ceiling mode

Removes or restores exactly one 16×16 ceiling panel.

### 11.3 Optional future modes

- Rectangle selection
- Multi-cell ceiling removal
- Floor replacement
- Structural recoloring
- Blueprint mode

These are not required for MVP.

### 11.4 Performance constraints

Bulk operations must be bounded.

Recommended:

```text
maxBlocksPerOperation = configurable
maxPanelsPerOperation = configurable
```

Large operations should be processed safely without freezing the server tick.

For MVP, modest synchronous operations may be acceptable if bounded to small numbers of panels.

### 11.5 Interaction design

Suggested controls:

- Right-click: remove target structure according to current mode.
- Shift + right-click: restore target structure.
- Mode-change key: cycles Panel / Run / Ceiling.

Exact bindings may be changed.

---

## 12. Permanent Thresholds

### 12.1 Concept

A permanent threshold is a persistent rectangular opening connecting one external dimension with one Backroom location.

It should appear permanently open.

There is no door animation.

There is no activation step for traversal.

The player should always be able to see the preview of the other side while both required chunks are available.

### 12.2 Shape

Recommended initial shape:

- width: 2 or 3 blocks
- height: 3 or 4 blocks
- rectangular
- axis-aligned

Arbitrary shapes are explicitly out of scope.

### 12.3 Endpoint data

Each endpoint needs:

```text
portalId
endpointId
dimensionKey
blockPos
facing
width
height
owner/team
type
pairedEndpointId
```

`type` is one of:

```text
PERMANENT
TEMPORARY
```

### 12.4 Backroom placement

Permanent thresholds should be placeable at any valid Backroom location.

No central entrance hall exists.

A threshold may be placed:

- in a generated wall,
- in an opened wall boundary,
- as a freestanding industrial frame if permitted.

The system should not force all access through a hub.

---

## 13. Temporary Portal Sessions

### 13.1 Goal

A player exploring anywhere should be able to instantly create a temporary connection to the Backroom.

Typical flow:

1. Player is deep underground.
2. Player presses the portal key.
3. Temporary external threshold appears nearby.
4. Paired Backroom threshold appears at the player's configured Backroom slot.
5. Player walks through.
6. Player uses storage/crafting/machines.
7. Player walks back through.
8. Player presses the key again.
9. Both endpoints disappear.

### 13.2 Lifetime

The portal remains active until explicitly closed.

No automatic timeout by default.

### 13.3 One active session per player

Recommended MVP rule:

> Each player may have at most one active temporary portal session.

This reduces ambiguity and prevents the temporary feature from becoming a teleportation network.

### 13.4 Backroom slot

Each player can designate a Backroom-side temporary portal slot.

A slot is a stored:

```text
dimension = Backroom
position
facing
```

The external side changes as the player explores.

The Backroom side remains stable until the player intentionally rebinds it.

### 13.5 External placement

When opening a temporary portal:

1. Search for a valid position near/in front of the player.
2. Prefer direct line-of-sight placement.
3. Ensure portal volume is collision-safe.
4. Ensure the player cannot be trapped.
5. Fall back to a freestanding frame if configured.

### 13.6 Closing behavior

When the session is closed:

- remove both temporary portal endpoint blocks/entities,
- release portal mirror chunk tickets,
- persist no world blocks other than optional anchor metadata,
- retain the configured Backroom slot.

### 13.7 Restart behavior

On server restart:

- active temporary sessions should either be restored safely or closed deterministically.
- MVP recommendation: persist session metadata and restore only if both endpoint dimensions/locations remain valid.
- Otherwise close the session cleanly.

No duplicated portal entities should survive reconciliation.

---

## 14. Portal Traversal

### 14.1 Trigger

Traversal occurs when the player's movement crosses the portal plane.

Do not teleport on:

- right-click,
- standing nearby,
- opening a GUI,
- looking at the portal.

### 14.2 Orientation

Player orientation should be preserved relative to the portal pair.

For simple axis-aligned portals, compute a constrained transform from source local coordinates to destination local coordinates.

### 14.3 Position mapping

Map player's local coordinates inside the source portal rectangle to the corresponding destination portal rectangle.

Preserve:

- relative horizontal offset,
- relative vertical offset,
- look direction transformed by endpoint facing.

### 14.4 Velocity

Preserve reasonable player velocity.

Avoid launching the player or zeroing movement unnecessarily.

MVP may preserve only forward/lateral walking velocity if cross-dimensional velocity handling proves unstable.

### 14.5 Cooldown / bounce protection

After transfer, prevent immediate accidental transfer back due to overlapping collision volumes.

Use a very short traversal guard based on:

```text
player UUID
portal ID
server tick
```

The guard should be long enough to prevent bouncing but imperceptible during normal movement.

---

## 15. Portal Preview Rendering

### 15.1 Purpose

The portal preview creates the illusion that the destination is directly behind the threshold.

It does NOT make the portal physically seamless.

The actual dimension transfer still occurs when crossing the portal plane.

### 15.2 Rendering model

For each visible portal:

1. Determine the player's camera pose relative to the source endpoint.
2. Transform that pose into destination-relative coordinates.
3. Render the destination from the transformed secondary camera.
4. Render into an off-screen framebuffer / render target.
5. Draw that texture onto the portal surface.

Conceptually:

```text
Main Camera
    |
    +--> normal scene
    |
    +--> visible portal?
            |
            +--> transformed destination camera
                    |
                    +--> reduced destination render
                            |
                            +--> framebuffer
                                    |
                                    +--> portal surface
```

### 15.3 Perspective correctness

The destination camera MUST change as the main player camera moves relative to the threshold.

Without this, the portal looks like a flat monitor.

Parallax is required.

### 15.4 No recursion

During a portal render pass:

```text
renderPortalPreviews = false
```

Any portals visible in the destination render must use a non-recursive placeholder representation.

This is a hard architectural invariant.

### 15.5 Backroom-specific visibility optimization

The Backroom room grid provides natural occlusion boundaries.

Initial portal rendering should begin with only the destination cell/chunk.

If the camera can see through an open cell boundary, the renderer may include neighboring cells.

Recommended traversal algorithm:

1. Start with the destination chunk.
2. Test portal camera frustum against each cell boundary.
3. If boundary is closed, stop.
4. If boundary contains a visible opening or is absent, enqueue adjacent chunk.
5. Continue breadth-first.
6. Stop after configurable maximum depth.

Suggested default:

```text
portalPreviewMaxCellDepth = 2
```

This means the renderer commonly renders:

- one chunk for closed rooms,
- a few chunks for large open halls.

### 15.6 External-dimension preview

When viewing from the Backroom into an external dimension, the Backroom grid optimization does not apply.

Use a small configurable destination render radius.

Suggested default:

```text
externalPortalPreviewRadiusChunks = 1
```

This should be configurable independently.

### 15.7 Reduced rendering quality

Portal previews should use reduced quality by default.

Possible optimizations:

- lower render target resolution,
- reduced view distance,
- no clouds,
- reduced particles,
- reduced entity distance,
- optional BlockEntity rendering distance cap,
- no second-order portal rendering,
- no distant terrain systems,
- no expensive shader integration in MVP.

### 15.8 Render scale

Suggested configuration:

```text
portalRenderScale = 0.75
```

Possible values:

```text
0.5
0.75
1.0
```

### 15.9 Render update rate

Optional optimization:

- update portal preview every frame when close,
- reduce update frequency for distant/small portals.

Example:

```text
distance < 8 blocks     every frame
distance < 24 blocks    every 2 frames
otherwise               every 4 frames
```

Not required for MVP but desirable.

### 15.10 Fallback mode

Portal preview rendering MUST be optional.

If disabled or unsupported:

- portal traversal still works,
- portal chunkloading still works,
- portal surface uses a fallback visual,
- no gameplay capability is lost.

This is essential for compatibility.

---

## 16. Chunk Loading

### 16.1 Design goal

The mod does not provide a general-purpose chunkloading system.

It only loads the minimum required counterpart area for portal responsiveness.

### 16.2 Core rule

If portal endpoint A is located in a chunk that is **normally loaded**, the paired endpoint B must be kept loaded via a portal mirror ticket.

### 16.3 Non-propagation invariant

A chunk loaded only because of a portal mirror ticket MUST NOT cause other portal endpoints in that chunk to propagate additional mirror loads.

This prevents load cascades.

Example:

```text
Overworld A
    |
    | normal load
    v
Backroom A
    |
    | portal mirror load only
    X
Backroom portal B must NOT cause Nether B to load
```

### 16.4 Load cause tracking

Portal manager must distinguish at minimum:

```text
NORMAL
PORTAL_MIRROR
```

A chunk may have multiple load causes.

Portal propagation only occurs if a non-mirror cause keeps the source endpoint relevant.

### 16.5 Ticket radius

Recommended default:

- endpoint chunk only

If portal preview requires adjacent chunks, they may be loaded client/server transiently as needed, but the persistent mirror rule should remain minimal.

### 16.6 Interaction with other chunkloader mods

Do not attempt to replace or override other mods.

If an external mod keeps the source chunk normally loaded, the portal counterpart should remain loaded.

If the external mod unloads it, the portal mirror load should eventually be released unless another normal cause exists.

---

## 17. Portal Preview Chunk Availability

Rendering requires destination chunk data.

When a portal becomes visible/relevant:

1. Server ensures the destination endpoint chunk is loaded.
2. Required chunk data is sent to the client normally.
3. Preview rendering begins when sufficient client data is available.
4. Before data is ready, render the fallback portal surface.

Do not block the client render thread waiting for destination chunk data.

---

## 18. Player Spawn and First Entry

The mod must define a predictable first-use flow.

Recommended:

1. First permanent or temporary Backroom connection triggers creation of a player/team Backroom start position.
2. Choose a deterministic safe cell near the Backroom origin allocation strategy.
3. Ensure valid floor and sufficient open space.
4. Place the Backroom endpoint.
5. Player may then explore naturally from that location.

Avoid a special "entrance hall".

The first entry should open directly into the generated Backroom environment.

---

## 19. Multiplayer Model

### 19.1 Ownership

Permanent portals should support ownership.

Recommended ownership abstractions:

```text
PLAYER
TEAM
PUBLIC
```

MVP may start with:

```text
PLAYER
PUBLIC
```

### 19.2 Backroom usage model

Two possible architectures exist:

#### Shared Backroom world

All players inhabit the same Backroom dimension and can physically encounter each other.

Advantages:

- simple,
- naturally multiplayer,
- no dynamic dimensions,
- emergent shared industrial world.

#### Allocated player/team regions

Each player/team receives a distant region in the same dimension.

Advantages:

- reduced accidental interference,
- more predictable private bases.

Recommended design:

Use one dimension with configurable allocation mode.

Default:

- per-team/per-player start regions spaced far apart,
- dimension still physically continuous and infinite.

Example:

```text
regionStride = 8192 blocks
```

This does not create hard borders.

It only selects initial spawn/portal neighborhoods.

### 19.3 Permissions

Server must validate:

- who may create a permanent threshold,
- who may destroy one,
- who may rebind one,
- who may use one,
- who may edit structural partitions.

Do not trust client packets.

---

## 20. Data Model

### 20.1 Global portal registry

Use dimension `SavedData` or equivalent server-persistent storage.

Conceptual model:

```text
PortalRegistry
    portals: Map<UUID, PortalPair>
```

### 20.2 Portal pair

```text
PortalPair
    id: UUID
    type: PERMANENT | TEMPORARY
    endpointA: PortalEndpoint
    endpointB: PortalEndpoint
    owner: Ownership
    enabled: boolean
```

### 20.3 Portal endpoint

```text
PortalEndpoint
    id: UUID
    dimension: ResourceKey<Level>
    position: BlockPos
    facing: Direction
    width: int
    height: int
```

### 20.4 Temporary session

```text
TemporaryPortalSession
    playerId: UUID
    portalPairId: UUID
    backroomSlot: PortalEndpointDefinition
    externalOrigin: PortalEndpointDefinition
    active: boolean
```

### 20.5 Player preferences

Optional persisted data:

```text
PlayerBackroomData
    temporarySlotPosition
    temporarySlotFacing
    portalPreviewEnabled
```

Client-only preferences such as render scale should remain client configuration rather than server player data.

---

## 21. Portal Endpoint Representation

Prefer a dedicated portal frame/entity system over trying to mutate arbitrary vanilla doors.

Recommended implementation options:

### Option A — portal frame blocks

A small rectangular frame built from dedicated blocks.

Pros:

- simple ownership,
- simple validation,
- easy rendering anchor,
- predictable collision.

### Option B — portal controller block + invisible plane entity

One controller block stores metadata and spawns/manages the portal plane.

Pros:

- less world metadata duplication,
- easier lifecycle management.

Recommended MVP:

**Controller block + frame blocks + logical portal plane.**

The visible "open doorway" can still look minimal.

---

## 22. Temporary Portal Placement Algorithm

Conceptual algorithm:

```text
candidateAnchors = [
    directlyAhead,
    slightlyAheadLeft,
    slightlyAheadRight,
    left,
    right,
    behind
]

for candidate in candidateAnchors:
    if canFitPortal(candidate):
        return candidate

if allowFreestandingFallback:
    return findNearestSafeFreestandingPosition()

fail
```

`canFitPortal` checks:

- replaceability,
- collision,
- floor support if required,
- world border,
- build height,
- protected blocks/events,
- server permissions.

Do not destroy arbitrary blocks automatically.

---

## 23. Structural Editing Algorithms

### 23.1 Identifying a wall panel

Given a targeted structural block:

1. Determine nearest cell/chunk.
2. Determine whether block belongs to north/south/east/west partition.
3. Resolve canonical shared edge.
4. Compute exact 16-block panel bounds.
5. Validate all affected blocks against structural tags.
6. Perform operation.

### 23.2 Connected run

Given one wall panel:

1. Determine plane orientation.
2. Walk in negative panel-axis direction.
3. Stop at first break/intersection/nonmatching state.
4. Walk in positive direction.
5. Bound by maximum panel count.
6. Apply operation to resulting run.

Do not perform arbitrary 3D flood-fill.

### 23.3 Ceiling panel

Resolve from cell coordinates.

Target volume:

```text
x = chunkMinX .. chunkMinX + 15
z = chunkMinZ .. chunkMinZ + 15
y = ceilingY or configured ceiling band
```

### 23.4 Restoration collision policy

Default:

```text
if target block is AIR or replaceable structural material:
    restore
else:
    skip/fail
```

Prefer atomic panel restoration:

- either whole panel restores,
- or operation reports obstruction.

This prevents partial ugly walls.

---

## 24. Portal Rendering Architecture

### 24.1 Separation of concerns

The rendering subsystem must be optional and isolated from portal gameplay.

Suggested modules:

```text
portal-core
portal-render-client
backroom-world
structure-tools
network
config
```

Portal traversal must not depend on rendering classes.

### 24.2 Render context guard

Use an explicit render context stack or guard:

```text
PortalRenderContext
    isPortalPass
    recursionDepth
```

Hard rule:

```text
if recursionDepth > 0:
    do not render portal previews
```

### 24.3 Camera transform

For source portal local frame:

- origin,
- right vector,
- up vector,
- normal.

Convert main camera world position to source-local coordinates.

Map source-local coordinates to destination-local coordinates.

Apply orientation transform.

No scaling.

### 24.4 Clipping

Portal preview ideally clips geometry behind the destination portal plane.

MVP may use stencil/depth masking and limited near-plane tricks.

Avoid deep renderer rewrites unless required.

The desired effect is convincing, not mathematically universal.

### 24.5 Client safety

If any portal render exception occurs:

- log once/rate-limit,
- disable preview for that portal/frame,
- fall back to static surface,
- never crash the game solely because optional portal preview failed where recoverable.

---

## 25. Networking

### 25.1 Server authoritative actions

Server must authorize:

- create permanent portal,
- destroy portal,
- create temporary portal,
- close temporary portal,
- bind Backroom slot,
- structural bulk edit,
- traversal.

### 25.2 Client packets

Client may request actions but cannot declare success.

### 25.3 Portal synchronization

Clients need enough portal metadata for:

- rendering,
- local plane detection assistance,
- destination preview location.

Server remains authoritative for actual transfer.

### 25.4 Avoid giant payloads

Never send chunks or world snapshots through custom portal packets.

Use normal Minecraft chunk synchronization.

---

## 26. Safety and Edge Cases

### 26.1 Destination obstructed

Before traversal:

- verify destination space remains safe,
- if obstructed, refuse traversal or resolve to safe offset.

Do not suffocate the player.

### 26.2 Portal destroyed while player approaches

Server traversal validation checks portal still exists and pair remains valid.

### 26.3 Dimension unavailable

If a modded destination dimension disappears:

- mark portal broken,
- do not crash world load,
- display inactive frame,
- allow portal removal.

### 26.4 Portal data mismatch

On load:

- reconcile endpoint blocks/controllers with registry,
- remove orphan temporary endpoints,
- mark incomplete permanent pairs invalid rather than duplicating them.

### 26.5 World border

Respect destination world border.

### 26.6 Build height

Portal placement must fit fully within world build bounds.

### 26.7 Death

Temporary session should remain or close according to config.

Recommended default:

- close temporary portal session on player death.

### 26.8 Logout

Recommended default:

- temporary portal remains persisted for a short logical session or closes immediately.

Safer MVP:

- close on logout.

Make configurable later.

### 26.9 Server restart

Portal registry must persist.

Permanent portals restore normally.

Temporary portals reconcile safely.

### 26.10 Multiple players crossing simultaneously

Portal traversal must work independently per entity.

No global portal cooldown.

---

## 27. Configuration

### 27.1 Server configuration

Suggested options:

```text
backroom.floorY
backroom.defaultRoomHeight
backroom.disableHostileSpawns
backroom.disableWeather
backroom.playerRegionStride

generation.fullWallChance
generation.wallWithOpeningChance
generation.openChance
generation.macroRegionSize
generation.doorwayWidthMin
generation.doorwayWidthMax
generation.doorwayHeight

portals.perPlayerTemporaryLimit
portals.allowPermanent
portals.allowTemporary
portals.closeTemporaryOnLogout
portals.closeTemporaryOnDeath

structure.maxPanelsPerOperation
structure.allowRestore

chunkloading.enablePortalMirrorLoading
```

### 27.2 Client configuration

Suggested options:

```text
render.portalPreviewEnabled
render.portalRenderScale
render.backroomPreviewMaxCellDepth
render.externalPreviewRadiusChunks
render.portalUpdateRateMode
render.renderEntities
render.renderBlockEntities
render.renderParticles
```

---

## 28. Visual Design

### 28.1 Style

Desired tone:

- industrial,
- liminal,
- clean,
- repetitive,
- calm,
- functional.

Avoid:

- horror gameplay,
- jump scares,
- hostile ambience,
- excessive visual clutter,
- strong yellow Backrooms meme styling unless provided as an optional theme.

### 28.2 Floor

Preferred:

- dark/neutral industrial slabs or panels,
- subtle chunk-border lines,
- distinct chunk-corner accents,
- optional embedded strip lights.

### 28.3 Walls

Preferred:

- modular panel texture,
- visible structural seams,
- neutral enough to coexist with arbitrary technology mods.

### 28.4 Ceiling

Preferred:

- industrial panels,
- integrated lighting,
- removable as logical cell-sized panels.

### 28.5 Lighting

Prefer structural full-bright or integrated fixtures.

Avoid thousands of ticking light entities.

Use static block lighting.

---

## 29. Audio

Optional but desirable:

- quiet industrial ambient loop,
- subtle ventilation hum,
- no threatening sounds.

Portal threshold may use:

- low-volume spatial hum,
- subtle transition sound.

Do not add loud teleport effects.

Traversal should feel like walking into another room.

---

## 30. Progression and Recipes

The mod is a playstyle enabler, not a late-game reward.

Access should be available early.

Recommended philosophy:

- permanent threshold: early-game affordable,
- structural tool: early-game affordable,
- temporary portal keybind capability: early to early-mid game,
- no End-game gating.

Exact recipes should remain pack-configurable.

Default recipe ingredients may use:

- iron,
- redstone,
- quartz,
- ender pearl if a mild progression gate is desired.

Avoid requiring:

- Nether Star,
- End progression,
- large quantities of rare materials.

---

## 31. Compatibility Philosophy

### 31.1 Do not relocate modded machines

The entire architecture intentionally avoids block relocation.

This eliminates a large class of compatibility problems involving:

- BlockEntity NBT,
- capabilities,
- machine networks,
- multiblocks,
- cached coordinates,
- scheduled ticks,
- mod-specific saved data.

### 31.2 Do not bridge arbitrary mod networks through portals

No automatic cross-portal:

- AE2 cables,
- Mekanism pipes,
- Forge/NeoForge energy,
- fluid handlers,
- item handlers.

Players can use dedicated cross-dimensional transport mods if desired.

### 31.3 Treat rendering as optional

Shader/performance compatibility must never determine whether the core mod works.

---

## 32. MVP Definition

The first playable MVP should include only:

### World

- Backroom dimension.
- Infinite chunk-aligned room generation.
- Floor markers.
- Random missing walls/openings.
- Connectivity guarantee.
- Removable walls.
- Removable ceilings.

### Structure tool

- panel remove,
- panel restore,
- ceiling remove,
- ceiling restore.

### Portal core

- one permanent portal pair type,
- exactly one Backroom endpoint,
- cross-dimensional player traversal,
- orientation mapping,
- persistence.

### Temporary portal

- keybind create,
- one active session per player,
- fixed Backroom-side slot,
- keybind close,
- exact return via paired external endpoint.

### Chunk loading

- direct counterpart chunk mirror loading,
- no propagation from mirror-loaded chunks.

### Portal preview

- basic render-to-texture,
- camera parallax,
- one destination chunk minimum,
- no recursive rendering,
- fallback visual.

---

## 33. Phase 2

After MVP stability:

- connected wall-run tool,
- smarter Backroom visibility graph for portal rendering,
- multi-chunk open-hall preview,
- permanent portal ownership UI,
- portal visual themes,
- improved temporary portal placement,
- preview render quality scaling,
- multiplayer team regions,
- portal sounds,
- structure coordinate labels,
- client diagnostics.

---

## 34. Phase 3 / Optional Features

Only after the core system is stable:

- entities visible through previews,
- dropped-item traversal,
- minecart traversal,
- configurable portal sizes,
- portal placement on floor/ceiling,
- multiple temporary portal profiles,
- server API for pack integration,
- KubeJS hooks,
- data-driven Backroom themes,
- API for third-party portal-frame skins.

These must not complicate the MVP architecture prematurely.

---

## 35. Acceptance Criteria

### 35.1 Backroom generation

- Every generated room cell aligns exactly to one 16×16 chunk.
- Floor chunk boundaries are visually readable.
- The world can generate indefinitely.
- Initial room layout is deterministic for a fixed seed.
- Generated layout is practically traversable.
- Some walls are absent.
- Some walls have openings.
- Some walls are fully closed.
- Ceiling exists by default.

### 35.2 Structural editing

- Player can remove exactly one 16×16 wall panel with one tool interaction.
- Player can restore it.
- Player can remove exactly one 16×16 ceiling panel.
- Player can restore it.
- Machine blocks adjacent to structural blocks are never bulk-removed.
- Restoration never silently deletes player machines.

### 35.3 Permanent portal

- External ↔ Backroom pair can be created.
- Same-dimension pair creation is rejected.
- External ↔ external pair creation is rejected.
- Backroom ↔ Backroom pair creation is rejected.
- Portal persists through restart.
- Walking through transfers the player.
- Returning through the same portal returns to the paired endpoint.

### 35.4 Temporary portal

- Keybind opens portal.
- Portal remains until closed.
- Player can travel in both directions repeatedly.
- Keybind closes both endpoints.
- Only one session per player exists in MVP.
- Temporary portal cannot become a general destination selector.

### 35.5 Portal rendering

- Player can see a live preview of the destination.
- Camera motion produces visible parallax.
- Portal rendering never recursively renders another portal.
- Disabling preview does not disable traversal.
- Closed Backroom room normally requires only destination chunk rendering.
- Open adjacent rooms may render a bounded number of additional chunks.

### 35.6 Chunkloading

- Normally loaded endpoint keeps direct paired endpoint loaded.
- Mirror-loaded endpoint does not propagate further mirror loads.
- Closing/destroying portal releases no-longer-needed portal tickets.
- Existing third-party chunkloaders continue to function independently.

### 35.7 Stability

- Restart does not duplicate portals.
- Invalid portal data does not crash world load.
- Missing modded dimension target deactivates portal safely.
- Optional preview-render failure does not corrupt portal state.

---

## 36. Non-Goals as Tests

The following behaviors should explicitly NOT work:

```text
Create Overworld -> Nether direct portal
Create Backroom -> Backroom shortcut
Use portal system as arbitrary waypoint network
Render recursive portal-inside-portal scenes
Transmit AE2 network directly through portal
Keep an entire chain of dimensions loaded via portal recursion
Move a factory by serializing all machines
Require an entrance hub
Require the player to use a GUI to enter the Backroom
Automatically close temporary portal after a short timeout
```

If future code accidentally enables these behaviors, treat that as architectural drift.

---

## 37. Suggested Package Structure

Illustrative only:

```text
com.example.backroomindustry
├── BackroomIndustry.java
├── config
│   ├── ClientConfig.java
│   └── ServerConfig.java
├── world
│   ├── BackroomDimension.java
│   ├── BackroomChunkGenerator.java
│   ├── BackroomLayout.java
│   ├── MacroRegionLayout.java
│   └── StructuralPalette.java
├── structure
│   ├── PartitionToolItem.java
│   ├── PartitionResolver.java
│   ├── PartitionOperation.java
│   └── CeilingOperation.java
├── portal
│   ├── PortalManager.java
│   ├── PortalPair.java
│   ├── PortalEndpoint.java
│   ├── PortalSavedData.java
│   ├── PortalControllerBlock.java
│   ├── PortalControllerBlockEntity.java
│   ├── PortalTraversalService.java
│   ├── TemporaryPortalService.java
│   └── PortalChunkTicketManager.java
├── client
│   ├── PortalRenderer.java
│   ├── PortalRenderTarget.java
│   ├── PortalCameraTransform.java
│   ├── BackroomVisibilityResolver.java
│   └── ClientPortalState.java
├── network
│   ├── CreateTemporaryPortalPacket.java
│   ├── CloseTemporaryPortalPacket.java
│   ├── StructuralEditPacket.java
│   └── PortalSyncPacket.java
└── registry
    ├── ModBlocks.java
    ├── ModItems.java
    ├── ModDimensions.java
    └── ModDataComponents.java
```

Do not treat this exact package layout as mandatory.

---

## 38. Implementation Order for Codex

Recommended implementation sequence:

### Step 1 — Project skeleton

- NeoForge 1.21.1 project.
- Registries.
- Config.
- Basic test commands.

### Step 2 — Backroom dimension

- Register dimension type.
- Implement custom chunk generator.
- Generate floor, walls and ceiling.
- Add deterministic wall/opening algorithm.
- Add connectivity guarantee.

### Step 3 — Structural tool

- Register structural blocks/tags.
- Implement panel resolution.
- Implement wall remove/restore.
- Implement ceiling remove/restore.
- Add operation limits.

### Step 4 — Portal persistence

- Implement portal registry.
- Implement endpoint controllers.
- Enforce topology invariant.
- Add placement/removal lifecycle.

### Step 5 — Traversal

- Detect crossing.
- Transfer player.
- Preserve local position/orientation.
- Add bounce guard.
- Validate destination safety.

### Step 6 — Temporary portal

- Keybind.
- Placement algorithm.
- One session/player.
- Backroom slot.
- Close lifecycle.

### Step 7 — Portal mirror chunkloading

- Track normal endpoint relevance.
- Add counterpart tickets.
- Add non-propagation rule.
- Release tickets correctly.

### Step 8 — Basic preview renderer

- Secondary camera.
- Render target.
- Portal surface.
- Destination pose transform.
- No recursion.
- Fallback mode.

### Step 9 — Backroom render optimization

- Start from destination cell.
- Traverse visible open boundaries.
- Bound traversal depth.

### Step 10 — Hardening

- Multiplayer.
- restart reconciliation,
- invalid dimensions,
- ownership,
- profiling,
- render compatibility,
- server validation.

---

## 39. Testing Strategy

### 39.1 Unit-testable logic

Where possible, isolate pure logic for tests:

- canonical edge hashing,
- wall-state generation,
- macro-region connectivity,
- portal topology validation,
- camera coordinate transforms,
- partition bounds,
- connected-run bounds,
- chunkload propagation rules.

### 39.2 Game tests

Use Minecraft/NeoForge game-test facilities where practical for:

- portal creation,
- invalid portal combinations,
- wall removal,
- wall restoration,
- traversal,
- chunkloading lifecycle,
- server restart serialization.

### 39.3 Manual compatibility test matrix

At minimum:

- vanilla client/server,
- dedicated server,
- several major tech mods installed,
- third-party chunkloader installed,
- common performance mod stack,
- shaders disabled,
- shaders enabled if supported.

### 39.4 Stress test

Create a Backroom base with:

- hundreds of BlockEntities,
- multiple loaded chunks,
- multiple permanent portals,
- two or more players,
- repeated temporary portal opening/closing.

Measure:

- server tick time,
- chunk ticket leaks,
- client render cost,
- memory usage,
- persistence correctness.

---

## 40. Performance Budgets

These are target guidelines, not strict guarantees.

### Portal preview

With one nearby portal in a closed Backroom cell:

- ideally render only one destination chunk,
- no recursive passes,
- one additional framebuffer pass,
- configurable render scale.

### Portal count

Only render portals:

- inside camera frustum,
- above a minimum projected screen size,
- within configured preview distance.

### Chunk tickets

Portal mirror loading must be reference-counted or otherwise reconciled so destroyed portals never leak tickets.

### Structural edits

Avoid unbounded synchronous edits.

---

## 41. Logging and Diagnostics

Provide debug logging categories for:

```text
portal lifecycle
portal traversal
portal chunk tickets
temporary sessions
world generation
partition operations
portal renderer
```

Optional debug command examples:

```text
/backroom portal list
/backroom portal inspect
/backroom portal repair
/backroom tickets
/backroom cell
/backroom temp close <player>
```

These commands are especially valuable during development.

---

## 42. UX Summary

The intended player experience is:

### At home

The player builds a permanent open threshold into a wall.

The factory is visibly present behind it.

The player simply walks through.

### While exploring

The player presses one key.

A temporary open threshold appears nearby.

The player walks into the Backroom, uses the base, walks back and closes the portal.

### Inside the Backroom

The player initially explores a strange but functional maze of industrial rooms.

Each room aligns to a chunk.

The floor makes chunk boundaries obvious.

Walls and ceilings provide initial structure.

As the base grows, the player removes exactly the walls and ceilings that are inconvenient.

Eventually the randomly generated Backroom becomes a customized industrial campus without requiring conventional building work.

---

## 43. Final Architectural Invariants

These should be treated as non-negotiable unless the design itself is intentionally revised:

1. The Backroom is a real dedicated dimension.
2. The Backroom is horizontally infinite.
3. Horizontal room cells are exactly 16×16 and chunk-aligned.
4. Default rooms have removable walls and removable ceilings.
5. Initial partitions are partially randomized but deterministic.
6. Generated space remains practically traversable.
7. Structural edits operate on logical panels, not uncontrolled flood-fills.
8. Portal traversal is independent from portal rendering.
9. Portal previews use render-to-texture, not a general immersive portal engine.
10. Portal rendering is non-recursive.
11. Every portal pair has exactly one Backroom endpoint.
12. Same-dimension portal connections are forbidden.
13. External-to-external direct connections are forbidden.
14. There is no mandatory entrance hall.
15. Permanent thresholds are always visually open.
16. Temporary portals remain active until explicitly closed.
17. Temporary portals are limited to one active session per player in the MVP.
18. Portal mirror chunkloading never propagates recursively.
19. The mod does not relocate machines or serialize bases.
20. The mod does not attempt to replace storage, logistics, energy transport or general chunkloader mods.
21. Optional rendering failures must never make the core portal system unusable.
22. The entire design exists to minimize friction between exploration and large permanent modded infrastructure.

---

## 44. Codex Handoff Instruction

When implementing this document:

- Preserve the architectural invariants above.
- Prefer simple, isolated systems over clever generalized abstractions.
- Keep server state authoritative.
- Treat portal rendering as optional client functionality.
- Do not expand scope into a general-purpose portal engine.
- Do not implement same-dimension portals.
- Do not implement recursive rendering.
- Do not serialize or move arbitrary modded factories.
- Use data-driven configuration where sensible.
- Build the MVP in the implementation order described above.
- After each major subsystem, ensure the project compiles and add focused tests before continuing.
- Prefer correctness, crash resistance and mod compatibility over visual perfection.
