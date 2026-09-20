# Elsebase

## Behaviour

- Tool usability, stacked rooms and anchor loading/safety are authorized and implemented; see `docs/stacked-rooms-and-anchors.md`.
- Current phase: shared surface templates, explicitly authorized by the user on 2026-09-20. Follow confirmed discussion decisions and `docs/surface-templates.md` over conflicting original proposals.
- Communicate in German. Code, identifiers and technical documentation use English, following the original specification.
- Preserve `docs/backroom-industry-design.md` as the original input. Record open questions and agreed changes separately until the design is approved.
- Target Minecraft 1.21.1, NeoForge and Java 21. Use the checked-in Gradle wrapper and pinned versions.
- Keep common/server code independent of client classes. Portal traversal must remain independent of optional previews.
- Verify version-sensitive assumptions against the resolved Minecraft/NeoForge sources before implementing them.
- Do not add speculative registries, configuration, dependencies or gameplay scaffolding before the relevant design is agreed.
- Treat industrial, magical and other appearances as themes over common gameplay. Each player has one instant pair; initial references use world seed plus UUID. Claims mods provide territory protection.
- Each library theme contains full wall/floor/ceiling patterns. Construction selects a build theme; Paint selects/recolors/sets defaults; Scan captures the gazed room panel into a saved private buffer and owns import/export. No player-facing role assembly or Inherit/Apply-look actions. Follow `docs/surface-templates.md`; this supersedes the original one-global-style restriction.
- Template materials are open by default with technical exclusions and the optional `template_material_blacklist` tag. No registration/allowlist for mod blocks. Scan targeting must recognize ordinary player-built room surfaces; wall material orientation is relative to the wall. Built-in floor/ceiling arrangements must have fourfold rotational symmetry; personal templates are unrestricted.
- Template imports permanently block the authenticated UUID only after unexpected processing failures. Expected validation, storage faults and client rendering faults never create player penalties. Keep filesystem publication outside the attributable processing boundary.
- All seven approved styles are complete vanilla-block patterns; Quiet Workshop is the default. Legacy global theme packs and skin proxy blocks were removed. Concept artwork remains a design reference, not runtime textures.
- Protect the Backdoor's generated/restored structural walls, floors and ceilings from accidental destruction, including explosions. Player-placed blocks follow normal rules. Mining by players, machines, drills, block breakers and fake players is allowed subject to normal permissions.
- No generated/emitting lamps. Default uniform visual brightness is a server-synchronized client lightmap policy; darkness keeps normal player lighting. Natural spawning is independent of visual brightness. Native main-menu configuration uses installation-wide COMMON config.
- Generation-affecting config changes do not require retroactive migration: users need a new world for consistent results or accept old/new generation artifacts. Preserve existing player construction and validate changes that would corrupt saved state; this policy does not authorize remapping allocations.
- The user selected Elsebase and the first logo direction, The doorway beyond (Die Tür ins Anderswo). Final logo exports remain unfinished. Subsequent implementation authorization covers the current Elsebase technical identity.
- Run `build` (includes domain tests) and `runGameTestServer` after gameplay changes. GameTests use isolated `build/gametest-themes-v2`; their flat-preset fixture is test-only. Vanilla's mock-server-player helper always reports creative; use a real server player fixture when testing survival costs.
- Automated graphical tests must leave the desktop cursor free and stay silent from startup. Test-only mixins provide runtime overrides without changing normal controls or saved audio settings.
- Preserve allocator reservations on load failures. Vanilla SavedData loading swallows exceptions; never silently replace a corrupt existing registry. Validate index mutations before touching the live map.
- Until release 1.0, backward compatibility with earlier development builds is not required. Prefer a new test world over migration code or compatibility layers. Reject unsupported save formats clearly; do not silently reset or delete worlds. Persistence within a supported build remains required.

## Project Overview

Elsebase implements a permanent workspace dimension with chunk-aligned rooms, reversible structural editing, persistent personal references and static cross-dimensional doorways. Namespace/package: `elsebase` / `dev.elsebase`. Seven complete block-pattern themes support technology and magic packs. Bounded block previews and portal-only transition images are implemented; final bespoke artwork/audio and general entity/machine previews remain follow-up work.

## Documentation Index

- [Surface templates](docs/surface-templates.md): scanner, libraries, live styles, persistent import blocks, authority, rendering budgets and verification.
- [Current in-game themes](docs/current-theme-gallery.md): actual client screenshots of all seven approved block-pattern themes, including ceiling views.
- [Vanilla theme proposals](docs/vanilla-theme-proposals.md): seven new material/layout studies; runtime theme changes require the user's explicit go. Never compress proposal images into block textures.
- [Revised theme proposals](docs/vanilla-theme-proposals-v3.md): six approved and implemented directions alongside Deepstone Halls. Images are never runtime textures.
- [Portal rendering](docs/portal-rendering.md): delivered scene/transition contracts, runtime model quarantine, shader fallback and repeatable client compatibility tests.
- [Portal preview proposal](docs/portal-preview-plan.md): historical pinned-source investigation and alternatives; approved for implementation, actual scope is in the rendering document.
- [Stacked rooms and anchors](docs/stacked-rooms-and-anchors.md): 128-block geometry, mandatory exits, generation trade-offs, tools and online anchor lifecycle.
- [Original design](docs/backroom-industry-design.md): preserved historical specification; later decisions override conflicting proposals.
- [Implementation](docs/implementation.md): current architecture, lifecycle, defaults, performance bounds, customization and known limits.
- [Player guide](docs/player-guide.md): controls, crafting, portal/anchor/tool behavior and configuration.
- [Development setup](docs/development.md): toolchain provenance, build/run instructions and verification status.
- [Design discussion](docs/design-discussion.md): technical findings, unresolved contracts and proposed discussion order.
- [Start points and portals](docs/start-points-and-portals.md): UUID allocation math, coordinate precision, latest portal/anchor requirements and placement trade-offs.
- [Name, story and visuals](docs/name-story-and-visual-directions.md): three unselected creative candidates and an illustrative concept board.
- [Themes and configuration](docs/themes-and-configuration.md): resource-pack versus datapack options, seven requested styles, and a minimal configuration proposal.
- [Theme gallery](docs/theme-gallery.md): seven accepted visual concepts and generation provenance, distinct from current texture palettes.
- [Naming discussion](docs/naming-and-logo.md): selected Elsebase name, earlier shortlist, research and discarded directions.
- [Elsebase logo proposals](docs/elsebase-logo-proposals.md): selected doorway direction, alternative studies, images and production provenance.

## Glossary

- **Backroom:** the proposed industrial dimension.
- **Backdoor:** the user's alternate term for the same Backroom dimension. Elsebase is the selected mod name; in-game dimension terminology remains to be settled.
- **Reference point:** persistent personal Backroom destination; distinct from a temporarily recalled portal endpoint.
- **Cell:** a 16×16 horizontal area aligned to a Minecraft chunk; not necessarily 16×16 clear interior space.
- **Threshold:** a permanent paired doorway with exactly one Backroom endpoint.
- **Temporary session:** one player-owned temporary portal pair.
- **Mirror load:** a counterpart chunk load caused by a portal, which must not propagate recursively.

## Doorway refinement — 2026-09-20

- Default F summons the instant portal; structural tools use shared client/server gaze selection with translucent preview; no modes/RUN. No V binding. Removal and creation are separate tools.
- Portals are 1×2, generated passages 2×2; chunks own all four wall/border sides, leaving 14×14 interiors. Neighbor wall halves remain independently editable. Creation builds solid walls; floor/ceiling removal preserves the perimeter, creation repairs the full slab; each room on all 16 levels has at least one exit. Eight-block grid within Y=0–127, shared slabs, bedrock Y=0; new worlds required for height changes.
- Save version 4 is required; no old-save migration under the pre-1.0 policy. Chunk reconciliation maintains current portal/anchor consistency. No regeneration of existing rooms.
- `tools/preview-models.py` optionally renders actual model front elevations using Pillow and local Minecraft resources; output remains in ignored build/. It is not an in-game screenshot.

- Structural selection is limited to the current room and the exposed adjoining neighbor wall half. Missing borders must not prevent reconstruction. Preview footprint and authoritative edits share the same Panel bounds; never accept a client-selected coordinate.

- Personal portal entry always resolves the current anchor; outside return is independent of anchor health and physical frame completeness. F inside must offer direct safe escape if recall cannot be placed. Keep remembered returns separate from portal lifetime; reclaim own stale frames without clearing player construction or other registered pairs.

- Portal interior body contact must work while falling without source flooring. Missing inner frames are valid for personal anchor arrival. Instant pairs expire after 1200 world ticks outside, refresh on successful summon/use, and never expire for an inside owner (including offline). Persist timer/residence; expiry keeps return history and never generates chunks only for surface cleanup.

- Preview rendering never swaps the active client world or adds chunk tickets. Runtime model failures quarantine the affected block ID until restart/resource reload; fatal JVM errors must propagate. Active Iris shaders use the standard static surface. Cache reset must request fresh server snapshots, and tests must cover both Minecraft receiving-screen instances. Use `tools/check-portals.ps1` for isolated graphical regression; optional pinned Iris/Sodium fixtures stay in ignored build/.

- Paint deletion is owner-only, confirmed and removes server-library/current-world references; built-ins and foreign themes remain protected. Keep secondary default/delete actions below the preview, separate from Select/Cancel.
- Portal placement requires only the 1x2 body. Half cleanup must respect WorldEdits staging/rollback; never delete the counterpart unconditionally in onRemove. Initialize client appearance policy at level creation and requeue cached styles when destination chunks arrive.
