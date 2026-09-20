# Design discussion and decision record

## Status

The user authorized the NeoForge development foundation and requested brainstorming before gameplay implementation. The original [design specification](backroom-industry-design.md) is input to that discussion, not approval to implement its proposed feature set.

Only decisions explicitly recorded as accepted in the decision log are agreed. Other recommendations remain proposals. Consolidate the specification before implementation is authorized.

## Product understanding

The base or workshop stays permanently in one dedicated dimension. Players move its entrances as they explore or relocate. The doorway should make the workspace feel adjacent, while the real transfer remains a dimension change. Chunk-aligned rooms provide organization; safe, reversible panel editing removes tedious construction work. The user explicitly broadened the design to technology, magic and other modpacks; an industrial appearance is one optional theme, not a gameplay requirement.

The strongest boundaries in the proposal are: exactly one Backroom endpoint per portal pair; no arbitrary waypoint network; no factory serialization; no cross-portal machine networks; no recursive rendering; traversal works independently of previews.

## Technical questions to settle before implementation

### D01 — Live preview feasibility and release scope

The proposal assumes that normally synchronized chunk data can supply a second dimension to the client (sections 17 and 25.4). A framebuffer and a second camera solve only rendering; they do not establish a second client world, dimension-specific chunk routing, light updates or BlockEntity lifecycle.

Verified against the resolved Minecraft 1.21.1 / NeoForge 21.1.250 sources: `ClientboundLevelChunkWithLightPacket` carries X/Z, chunk data and light data, but no dimension key. `ClientPacketListener.handleLevelChunkWithLight` and `updateLevelChunk` apply data to its current `ClientLevel`; `handleRespawn` replaces that world on a dimension change. Normal packet delivery therefore does not by itself populate a second preview world. A dimension-aware routing/synchronization design is required. Section 25.4 must be clarified before live preview implementation; a framebuffer alone cannot satisfy it. This finding does not prove that live preview is impossible.

Accepted on 2026-09-19: the first test version may use a static portal surface. Live preview does not gate that version. This scope decision does not authorize gameplay implementation.

Proposed later technical experiment after approval: one portal pair, one destination chunk, live block updates, camera parallax, separate client/server processes and fallback.

### D02 — Mirror loading semantics

The desired rule is about load causes, not simply whether a chunk exists. A mirror ticket may coexist with a player or another mod's ticket, and neighboring chunks may inherit load levels. A loaded/unloaded event alone is insufficient evidence that a portal may propagate another load.

Verified against NeoForge 21.1.250: `TicketController`, registered through `RegisterTicketControllersEvent`, supports owner-specific force/unforce operations and a restart validation callback. `ForcedChunkManager` delegates to region tickets with distance 2; “one endpoint ticket” must not be confused with a guarantee that exactly one chunk is affected. `ChunkEvent.Load` supplies the chunk and whether it is new, not its load cause. Minecraft's `DistanceManager.getTickets` is private in the resolved sources. These APIs do not provide a ready-made universal NORMAL/MIRROR classification.

Agree which normal causes count, whether the destination must tick machines or merely be ready for arrival, how neighboring ticket influence is treated, and what support can be promised for third-party loaders. The prohibition on recursive mirror propagation remains a requirement. No access transformer, mixin or compatibility dependency has been added; the implementation approach remains open.

### D03 — Chunk cell versus clear interior

A 16×16 chunk cell with walls occupying block columns cannot also guarantee a full 16×16 unobstructed interior. Choose wall ownership, thickness, corners and clear floor area explicitly. Shared-edge hashing defines whether a wall exists; it does not define which chunk owns its physical blocks.

Proposal: preserve exact chunk cells and deterministic canonical ownership. State the resulting clear dimensions honestly and avoid duplicate walls at boundaries. Use floor markings after walls are removed. Verify negative coordinates as well as positive coordinates.

### D04 — Height and structural editing

Clarify whether “floor surface Y=64” means the block coordinate or the top face. Define a single floor/interior/ceiling convention before generation. Decide usable volume below the floor and above the ceiling, floor thickness and void protection. Deliberate structural mining is permitted by the 2026-09-20 clarification; do not use explosion protection to make the floor generally unmineable.

After removing a wall there is no wall block to right-click for restoration. Choose a targeting method, for example selecting the canonical boundary via the floor seam with a placement preview. Define whether restore means a solid wall or the original generated wall including its doorway. Tags identify material, not whether a player placed a structural block; choose how player-built structural materials behave under the tool.

Proposal: whole-panel collision validation before restoration, no machine replacement, no block drops for bulk removal if restoration is free. Agree the material/drop policy to avoid infinite resource production.

### D05 — Temporary session lifetime and getting stranded

Update 2026-09-19: the user wants summon/recall behavior with a stable external return point and a separately stored reference point, and confirmed one instant pair per player in total. The old open/close toggle is superseded as the intended direction; see [the current state proposal](start-points-and-portals.md#4-instant-portal-state-and-interaction). Logout/death/restart behavior is still open.

The original sections 13.2 and 43 say temporary portals remain until explicitly closed; section 26 recommends closing on death/logout, while section 13.7 recommends restoring valid sessions after restart. These remain conflicting lifecycle proposals.

Choose a separate close action and behavior for disconnect, death, restart, occupied return location and unavailable external dimension. If the only exit closes, define a safe recovery route. Recall while inside must not relocate the external endpoint; explicit recall from outside is covered by the new proposal.

### D06 — First entry, pairing and moving entrances

Update 2026-09-19: instant access is available from the start. The initial reference is selected using the UUID and world seed; a reusable tool places/replaces a non-colliding floor anchor to change it. Permanent portals use vanilla crafting resources. See [allocation calculations and placement findings](start-points-and-portals.md). The one-million-block radius is a tested hypothesis, not an agreed default.

Choose exact first-use placement, tool/permanent-portal recipes, how permanent endpoints are paired and the anchor tool interaction. Instant access itself has no crafting/unlock gate. Decide whether moving a permanent external doorway preserves its old Backroom endpoint, and what happens to the old pair during relinking.

Proposal: one shared dimension with allocated start areas, no mandatory lobby, one simple initial doorway size. A start-area allocator must avoid occupied locations; merely hashing player IDs or applying a stride is not a complete allocation strategy.

### D07 — Multiplayer and permissions

Confirmed 2026-09-19: every player receives their own starting area; claims mods provide territory protection. This mod still validates ownership of personal anchors/instant pairs and must respect the chosen protection integrations for edits and placement. Separated starts do not create hard borders. Public/permanent portal access and guests returning through another player's instant session still need explicit behavior. Do not claim compatibility with all claims mods without integration tests.

### D08 — Presentation, progression and MVP boundaries

The atmosphere remains calm and functional rather than horror. The user broadened appearance to optional themes for technology and magic packs. [The earlier name/story/visual candidates](name-story-and-visual-directions.md) remain unselected industrial explorations. [Theme and configuration proposals](themes-and-configuration.md) now compare resource-pack skins, datapack palettes and per-area customization, including fantasy directions. Confirm brightness, room variety and whether exploration of the initial labyrinth is a goal or merely atmosphere.

The tool's connected-run mode is “required” in section 11 but deferred to phase 2 in section 33 and excluded from the narrow MVP list in section 32. Proposal: defer it until single-panel editing is reliable. Recipes, exact doorway sizes and expensive compatibility work should follow the agreed first playable scope.

### D09 — Structural block protection

Corrected by the user on 2026-09-20: protection covers the Backdoor's own structural walls, floors and ceilings, so explosions and other accidental destruction cannot damage the room shell. Player-placed blocks, including machines and third-party blocks, follow their normal rules. The earlier interpretation protecting every block in the dimension is superseded. Explosions themselves and entity damage are not disabled by this requirement.

Deliberate mining by players, machines, block breakers, drills and all fake players is explicitly allowed; the user regards this as intended player action. Do not reject mining merely because its actor is a fake player. Normal block rules, ownership and claim permissions still apply. The special tool retains its structural/portal/anchor scope; this requirement does not authorize bulk removal of arbitrary machines or inventories.

The protection contract distinguishes generated structure (including panels restored by the structural tool) from ordinary player construction. A position or material tag alone must not accidentally protect a machine replacing a wall or an arbitrary player block. If the same structural material is obtainable and placeable by players, preserve that distinction through an explicit origin/state contract; do not assume every occurrence of its block ID is generated structure. The representation remains an implementation decision. Define transfer behavior for movable structural blocks before adding such support.

Source inspection: NeoForge 21.1.250 `ExplosionEvent.Detonate.getAffectedBlocks()` exposes the affected positions for modification independently of the entity list. A candidate intervention is to exclude only protected structural positions in this dimension. Do not cancel whole explosions or clear all affected blocks. Increasing blast resistance alone is not evidence of immunity to every supported explosion path. Protection must not introduce blanket bans on fire, fluid behavior, harvesting or machine updates for player construction.

Before implementation, verify structure removal paths and the origin contract. Acceptance checks must cover a mixed explosion target (structure survives, susceptible player blocks retain normal damage), restored panels, player replacements at structural positions, manual and automated mining, fake-player claim checks, and unchanged behavior in other dimensions. Modded explosions and direct-edit drills require representative integration checks; no universal compatibility guarantee is established yet. No protection code or integration tests exist.

### D10 — Optional darkness

Accepted feature on 2026-09-20: add an option for an unlit Backdoor dimension, without generated light sources, combinable with enabled natural mob spawning for a more dangerous experience. The normal calm presentation remains the proposed default. Player-placed lights follow normal rules, consistent with the clarified player-construction policy.

Proposal: one `world.darkness` setting, independent of `world.allowNaturalMobSpawning`, covering ambient brightness, skylight and automatically generated lighting across every skin. Do not turn off only the visual ambient lift while leaving luminous ceiling blocks, or only remove lamps while leaving a bright lightmap. Monster spawning still follows difficulty, biome spawn entries and normal conditions. The source-verified distinction between ambient rendering and actual spawn light is recorded in [themes and configuration](themes-and-configuration.md#6-evidence-and-remaining-verification).

Accepted lifecycle: no retroactive regeneration/migration is required for lighting or other generation-affecting options. Users need a new world for a consistent result or accept artifacts, including old fixtures remaining beside newly unlit rooms. Document whether applying a setting needs a restart; preserve player construction and saved allocation invariants. Test both lighting modes against spawning on/off, player light placement/removal, ceiling removal, restoration, chunk reload, dedicated-server/client agreement and each bundled style, including the documented mixed-generation behavior after config changes.

## Suggested discussion order

1. Essential experience: live view through doorways, first entry and basic exploration loop.
2. Space: room dimensions, vertical freedom and the look of the factory.
3. Control: restoring structures, moving entrances, temporary sessions and safe return.
4. Multiplayer: shared space, privacy and permission model.
5. Approved first milestone and acceptance criteria.

## Decision log

| Date | Topic | Status / decision |
| --- | --- | --- |
| 2026-09-19 | Foundation | Authorized: obtain and configure the NeoForge 1.21.1 MDK; inspect real APIs and verify the build. |
| 2026-09-19 | Gameplay | Not authorized yet; brainstorm, record details, then wait for explicit implementation approval. |
| 2026-09-19 | First test version | Accepted by the user: a static portal surface is sufficient initially. Live preview is deferred; implementation still awaits explicit approval. |
| 2026-09-19 | Technical identifiers | Provisional setup: `backroom_industry`, `dev.backroomindustry`, version `0.1.0-SNAPSHOT`; no publishing identity or distribution license selected. |
| 2026-09-19 | Initial reference | User requires UUID-based personal start selection, sized for 100 players with negligible accidental proximity and restrained coordinate magnitude. Radius/minimum-distance/allocator recommendations await selection. |
| 2026-09-19 | Seed input | User confirms combining the world seed with the UUID. Persist allocations; seed mixing does not replace collision handling. Radius/minimum-distance/allocator recommendations remain open. |
| 2026-09-19 | Instant access | User requires availability from the start, a stable external return point while inside, and a reference point independent of portal recall. Confirmed: one instant pair per player total. |
| 2026-09-19 | Personal areas and protection | User confirms separate player starting areas; claims mods provide territory protection. No new private dimensions or built-in territory claim system. |
| 2026-09-19 | Anchor | User requires a carpet-like non-colliding reference marker, placed by a reusable tool; placing a new anchor removes the old one. |
| 2026-09-19 | Permanent portals | User requires vanilla-resource costs, optional intermediate recipe item, fixed placement until removal by a suitable tool. Fixed Backroom sockets versus free placement is under evaluation. |
| 2026-09-19 | Naming and visuals | User requests marketing/design-assisted options and optional sample images. Three candidates are documented; none is selected. |
| 2026-09-19 | Theme scope | Accepted: one visual style per modpack is sufficient. Simultaneous per-player/per-area styles are not required. Technology/magic suitability and third-party skins remain requirements; resource-pack delivery is recommended. No individual visual style is selected yet. |
| 2026-09-19 | Configuration scope | User requires essential performance/modpack options with a small, focused config. The documented keys/defaults are proposals, not selected values or implemented features. |
| 2026-09-20 | Included styles | User accepts all seven gallery concepts and requests inclusion of all seven where feasible. One style per pack remains the scope; default style and delivery sequencing remain undecided. This is a design decision, not gameplay implementation approval. |
| 2026-09-20 | Block protection (corrected) | Protect the Backdoor's structural walls, floors and ceilings against accidental destruction. Player-placed blocks follow normal rules. Supersedes the earlier all-blocks interpretation; no blanket explosion cancellation. |
| 2026-09-20 | Automated mining | Machines, block breakers, drills and all fake players count as intended mining and are permitted, subject to normal permissions and block rules. |
| 2026-09-20 | Darkness | User requests an option for no ambient illumination or generated light sources, combinable with enabled natural mob spawning. Exact key/default remain proposals; generation-change lifecycle is accepted below. |
| 2026-09-20 | Generation-affecting config changes | No costly partial/full regeneration or automatic migration is required. Users start a new world for consistent results or accept artifacts in existing worlds. Preserve save integrity, player construction and existing allocation reservations. |
| 2026-09-20 | Naming sequence | User rejects Hingespace as awkward, proposes Otherspace/Elsebase and requests 3–5 names first. No further logo creation until a name is agreed. Current shortlist is documented separately; no name or rebranding is approved. |
| 2026-09-20 | Selected name | User chooses **Elsebase**, also appreciating its perceived “El-space” sound association, and requests three logo proposals. This satisfies the name-before-logo condition. No logo, technical rename or gameplay implementation is approved by this choice. |
| 2026-09-20 | Selected logo direction | User prefers the first Elsebase logo, **The doorway beyond (Die Tür ins Anderswo)**. Adopt its doorway, offset teal room and orange threshold as the design reference. Vector masters and production icon exports remain unfinished. |
