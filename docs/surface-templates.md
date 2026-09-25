# Shared surface templates

Authorized after the September 2026 template discussion. This supersedes the earlier restriction to one global resource-pack style. Gameplay geometry, structural provenance and traversal remain unchanged.

## Player workflow

A **theme is one named package** containing complete wall, floor and ceiling patterns. Select **Arcane Archive**, not separate wall/floor/ceiling entries. Each wall can still have its own applied theme. Patterns retain individual material coordinates, allowing asymmetric columns, bands, checkerboards and inlays. The seven built-ins now use 14×7 wall grids and 16×16 floor/ceiling grids inspired by `docs/concepts`; they use available Minecraft materials rather than reproducing every detail of the concept artwork.

| Tool | Right-click | Shift-right-click |
| --- | --- | --- |
| Construction Tool | Build/restore the aimed surface with this tool's selected theme. | Browse and preview complete themes: **Select**, **Cancel**. |
| Paint Tool | Recolor the aimed room surface without placing/removing blocks. With no selected theme, open the selection menu instead. | Browse themes: **Select**, **Cancel**; separate confirmed **Use as default** and **Delete** actions below the preview. |
| Scan Tool | Copy the aimed room surface into the selected theme's private buffer. | Choose the theme to edit; inspect the buffer, save, import or export. |

Each tool keeps its own selection. Selecting a Paint theme does not alter the Construction selection. Browsing does not apply a theme: **Select** confirms the choice, **Cancel** closes without changing it. Paint's **Use as default** updates inherited appearances in the player's allocated region; explicitly painted or constructed surfaces keep their own theme bindings.

Paint can delete an owned server theme after confirmation. Built-in, pack and foreign definitions are protected. Deletion removes the installation-library file and current-world definition, clears its explicit bindings and defaults, and invalidates tool choices. Affected surfaces inherit the next valid region/global style. Private scanner contents survive as unpublished copies. Exported client copies and definitions retained in other worlds are not erased.

### Edit a theme with the scanner

1. Hold the Scan Tool and Shift-right-click. Choose a complete theme and press **Select**, or use **New Theme**, enter a name and **Create**. A new private draft starts with plain stone on all surfaces; close the menu to scan into it. It is not published or counted against the theme quota until saved.
2. Look at a room wall, floor or ceiling and right-click. Its complete pattern replaces only that role in the private buffer. Source blocks stay intact. Walls use 14×7 cells (the topmost room's wall is shorter); slabs include their 16×16 border. Targeting follows the current room and exposed neighboring wall, like structural editing.
3. Repeat for other surfaces as desired. Red highlights identify unsupported blocks; invalid scans leave the previous buffer intact. Empty door openings retain the buffered pattern at those cells. A wholly empty surface is rejected.
4. Shift-right-click again to see the complete edited room. The dirty-status line identifies scanned roles. **Save changes** updates an owned theme everywhere it is used after confirmation. **Save as my theme** creates an owned copy of a built-in or another player's theme. Rename if the chosen name already exists in your own library.

Switching to another theme with unsaved scans requires confirmation. Closing the menu keeps the buffer. Choices and scan buffers are stored in world saved data and survive logout/restart; no block entity stores them. A clean buffer refreshes from its shared theme on reopening, while dirty edits remain private until saved. Imported/scanned theme definitions are mutable; existing uses update progressively through bounded synchronization. There is no user-visible revision history.

Creating another draft also requires confirmation if the current draft is unpublished or has unsaved scans. Invalid names preserve the existing draft. New drafts survive logout/restart just like edited copies.

The demo uses a fixed isometric view into the open room corner, with both walls behind the floor. There is no Rotate control; **Show ceiling** remains available. Deepstone Halls now uses the approved dark masonry/tuff direction. All built-in floor and ceiling cell arrangements are invariant under 90-degree rotations. Personal templates remain unrestricted. All six revised appearances from [the approved gallery](vanilla-theme-proposals-v3.md) are now implemented.

### Share and import

Library actions exist only in the Scan Tool menu. **Export** copies the selected theme (or its current scan buffer) to the personal library, with rename/overwrite confirmation. This allows copying another player's visible design without granting permission to overwrite their original. **Import** opens the personal library when no local source is selected; choose a file, then Import. To replace an owned server theme with a local file, keep the chosen local source and select your owned destination before Import; the overwrite dialog explicitly confirms replacement. Other owners' themes cannot be overwritten.

The scanner operates on Elsebase room elements. Its targeting recognizes all non-air source blocks at the room boundary, including unsuitable blocks for red highlighting; it only reaches the adjoining wall half through an empty portion of the near wall. Structural removal keeps its separate structure-only targeting. Build a sample design at the corresponding room surface using approved full blocks, then scan it. There is no two-corner mode or manual role selector. The role comes from the looked-at element, and the same UV mapping is used for scanning and rendering on all wall orientations. Ordinary player blocks remain ordinary blocks after painting; only Elsebase structural surfaces acquire the theme appearance.

## Library, config and administration

Library: **`<game-directory>/elsebase/templates/`**, shared across worlds within that installation. Launchers may give each pack a separate game directory. Dedicated servers use their server root. Local client files never automatically change multiplayer worlds.

Server-created filenames are `player_<owner-uuid>_<template-uuid>.json`. Pack authors can provide `my_theme.json`, addressed as `pack/my_theme`. Pack filenames use 1–80 lowercase ASCII letters, digits, underscores or hyphens. Symlink files are ignored. Used definitions and assignments are also saved internally in `data/elsebase_templates.dat`, preserving copied worlds when library files are absent. Removing a library file does not erase already-used definitions.

Operator commands (permission level 2):

- `/elsebase templates reload`: load library files and refresh defaults; invalid files are logged without replacing existing definitions.
- `/elsebase templates blocked`: list blocked UUIDs and incident IDs.
- `/elsebase templates unlock <uuid>`: durably unlock an account's imports.

The server-wide blocklist is `elsebase/import-blocklist.txt`, outside saves and character data. Corrupt/unreadable blocklists disable uploads until repaired and the server restarted. Failure to persist a new block also closes uploads. Existing template use, scanning and gameplay continue. Offline-mode servers cannot guarantee authenticated account identity.

COMMON config section `templates`:

| Key | Default | Meaning |
| --- | --- | --- |
| `allowPlayerImports` | true | Allows local-file uploads; server scans remain available. |
| `allowPersonalDefaults` | true | Allows and uses personal region defaults. |
| `templatesPerPlayer` | 64 | Owned definitions, including scans, in the current world registry. |
| `defaultStyle` | `quiet_workshop` | Built-in theme name or one complete theme ID. Reload templates after changes. |

Hard limits: 4,096 registered definitions including built-ins/retained definitions, 64 KiB per complete theme, up to 16×16 cells and 256 palette entries per surface, 64 pending uploads and one pending upload per player. At most two uploads are processed per tick. Requests are rate-limited.

## Portable format and materials

A portable JSON file has `format: 2`, `name`, `author`, and exactly three fields `wall`, `floor`, `ceiling`. Each surface holds a bounded Pattern object (`name`, `author`, `width`, `height`, `palette`, `cells`). Palette entries contain a block registry ID and its properties; cells are row-major material indices. Export a theme in game to obtain a complete example.

The 64 KiB limit applies to the **whole theme**, not each surface separately. Names/authors are limited to 64 characters. Unknown/duplicate keys, invalid indices/property values, excessive nesting and trailing content are rejected. No scripts, commands, NBT, URLs, inventories, paths or nested pattern references are accepted. Legacy single-surface files are explicitly rejected; they are not treated as incomplete themes.

Cells are bottom-up on walls; floors/ceilings use world X/Z. Wall block states are stored relative to the north wall and rotated with the destination wall using the block's rotation API. Logs preserve end-grain versus bark when moved between wall orientations. Scanning an existing Elsebase appearance copies its canonical state without double rotation. Smaller imported patterns tile and larger ones crop. A scan captures the complete element without flattening it to one material. Opposite shared slab faces stay independent despite the complete-theme library model.

Materials are open by default, including mod blocks. The optional server tag `elsebase:template_material_blacklist` is empty by default; datapacks can add exclusions. The old allowlist is removed. Opaque full collision, MODEL rendering, no fluids and no block entities are required. Source ticking or light emission does not disqualify a material: only appearance is copied, never behavior or light emission. Client models additionally reject biome-tinted, oversized or excessively complex faces; animated atlas textures are supported. Connected textures and models requiring external world/model data may fall back; arbitrary mod models are not certified. Failed models use structural fallback and retry after resource reload/restart; this never bans a player. A visually unattractive but technically supported model remains the player's choice.

Only identifiers/properties are shared. Third-party mods and textures must exist on the recipient installation. Resource packs and shaders may change final pixels, while material assignments remain server-authoritative.

## Authority and failure boundaries

The server checks ownership/quota, resolves every material and atomically replaces a file before publishing the definition. Client metadata never determines the owner or path. Ordinary validation failures reject without penalty. Unexpected runtime/linkage failures inside import processing persist a UUID block until admin unlock. Fatal VM errors are not swallowed. Filesystem failures outside processing and client-render failures do not punish players. Client error reports cannot ban another account.

Cosmetic edits derive their target from server gaze, require the Paint Tool and check build/claim break permission even on empty cells. Existing construction transactions and anchor protection remain. Styling never fills holes or edits ordinary player blocks. Structure drops no resources and gains no original-material recipes, beacon/redstone functions, light emission or other behavior.

## Regions, rendering and persistence

The frozen allocation grid defines stable chunk-aligned regions around reserved start slots: 8,192 blocks wide at default settings, approximately 4,096 blocks to each side. This is a visual region, not a claim. Current anchors, online status and generation triggers do not affect it.

Resolution: explicit surface → enabled personal region default → global default → Quiet Workshop. Personal defaults live in Elsebase saved data. Logout and vanilla character deletion leave them intact. Missing profiles fall back without remapping regions or loading chunks.

Shared slabs have independent top/bottom appearances. Corners/borders resolve by visible face; each wall half remains independently editable. Bedrock Y=0 remains vanilla. No second layer or height change. Geometry save format remains 4, template save format is 2 and network protocol is 6.

Rendering uses normal chunk-baked geometry: no per-block entity, ticking or separate world rendering. Shared immutable patterns and 96 references describe each loaded column's 16 levels. Quad/state caches reset on resource reload. All seven built-in themes can coexist. The library exposes seven complete entries; internal role references are hidden from players.

Only watched columns or authorized loaded portal snapshots receive appearance data; no extra chunk loads. Background delivery rotates fairly between viewers, prioritizes nearby columns on resync, and has a 128 KiB/tick cap, four column attempts and a cooperative 2 ms bound. Client rebuilds schedule at most four sections per tick, nearest first. Repeated updates coalesce. Shared-definition changes invalidate loaded uses; unseen rooms use the new definition when loaded. Large changes can become visible progressively.

The menu renders one demo; local library parsing is asynchronous and limited to 4,096 files. Server search is paginated. No periodic directory scans. Shader packs retain their own lighting/material policies.

## Verification commands

`build` includes production parser and persistent import-block tests. `runGameTestServer` exercises actual registries, material/loot policy, independent slab faces, offline/persistent defaults, nondestructive scans, ownership, import codec/storage and claims alongside the existing gameplay suite.

`tools/check-templates.ps1` creates an isolated client fixture under ignored `build/template-client`, checks actual red/green pixels before/after overwriting a shared pattern, slab face assignments, catalog/menu and free cursor, then exits. Screenshots and a timestamped report remain. Profiles `iris` and `iris-active` use separate fixtures prepared by `tools/prepare-render-compat.py`. The authored test shader renders full-bright textured terrain to separate material assertions from lighting policy; it does not certify arbitrary shader packs.

`tools/check-portals.ps1` covers preview/traversal regressions. Remote TCP latency, large production modpacks and 100 simultaneous human clients are not certified by these fixtures; bounded work is not a zero-overhead guarantee.

Verification on 2026-09-20: build/domain checks and all 20 required GameTests passed. Actual-client template fixtures passed with vanilla, Sodium 0.6.13/Iris 1.8.12 without shaders and with the authored active shader. The portal fixture passed custom floor appearance, destination pixels, model quarantine, resource reload, selective transition and cleanup. Test-only audio overrides mute every category without changing saved options; the client fixture additionally checks this and local export-name retention across confirmation-screen reinitialization.

The complete-theme/tool revision was reverified in all three client profiles and the portal fixture. GameTests verify asymmetric multi-block scans on all six orientations, private buffers, copy-on-save for built-ins, in-place ceiling-only updates preserving wall/floor patterns, persistent tool choices and paint without block mutation. All three tool menus were captured and visually inspected; the client fixture checks their separate action sets.

## Development save boundary

Open-material refinement: removed the allowlist and source ticking/emission exclusions, retained technical shape/data restrictions, and added the optional blacklist. Scanner targeting previously reused removal's structure-only test and could jump behind a player-built wall to its structural neighbor; the scanner now tests all non-air source cells while removal keeps its existing contract. Built-in Deepstone Halls uses the approved tuff/deepslate palette, and all built-in horizontal cell layouts have fourfold symmetry. The subsequent user approval authorized replacing the remaining six patterns.

Verified after this refinement: build/domain checks, all 23 required GameTests, all three template client profiles (vanilla, Iris/Sodium, active authored shader) and the vanilla/Fancy portal regression passed. Real baked-model checks confirm log bark/end textures on every wall orientation and acceptance of gilded blackstone, prismarine and sea lantern models. Deepstone room/ceiling screenshots were inspected. Arbitrary third-party block models and shaderpacks still require pack-specific testing; all automated clients stayed silent with the cursor free.

The subsequent menu refinement passed all 21 required GameTests and the vanilla actual-client fixture twice, including new-draft persistence/publication, cancellation protection and first-use Paint behavior. Final screenshots confirm the open-corner isometric preview fits below the controls. Shader profiles were not rerun for this menu-only refinement; their preceding complete-theme results above remain the latest shader evidence.

Template save format 2 replaces the development-only format 1. Existing format-1 saves are rejected clearly rather than reset or migrated. Use a new test world for this build. Old single-pattern library files are skipped with a log warning; supported full-theme files remain installation-wide. Geometry save format 4 is unchanged.

## Approved built-ins

All seven built-in entries now use the approved whole-block designs: timber/stone/copper workshop, dark-oak bookshelf archive, mossy timber cloister, quartz/amethyst observatory, calcite/ceramic sanctuary, copper service grid and the retained tuff/deepslate halls. Each theme has its own wall, floor and ceiling rules. Horizontal grids are built by rotating a quadrant together with log axes and glazed-terracotta facing; GameTests compare the result with Minecraft block-state rotation. No per-block entities or extra per-frame work were introduced.

The superseded common pattern, 21 legacy `skin_*` proxy blocks and six global resource-pack overlays are removed. Built-in IDs stay the same, so existing built-in assignments use the new patterns after restart. Personal copies and library files are not overwritten or deleted. A personal theme referencing removed `skin_*` materials needs rescanning with real materials; unsupported material references use the existing rendering fallback and fail import validation. Pre-1.0 worlds that actually contain removed blocks should be replaced for testing. Concept images remain historical design references, not in-game options.

## Appearance delivery during travel

Definition and column packets can arrive before a destination chunk. Chunk-load events requeue its cached styles for the existing budgeted section rebuilds, and the client dimension flag is set at level creation before initial compilation. New definitions also rebuild already-referenced columns. This prevents an early discarded rebuild from leaving structural fallback textures after travel. Portal snapshots use the same authoritative definitions and are invalidated on appearance updates.

Chunk installation is not mesh readiness. Sodium 0.6.13's `RenderSectionManager.scheduleRebuild` ignores a section while `isBuilt()` is false, including the interval between its initial mesh compilation and upload. Theme delivery in that interval previously consumed Elsebase's dirty entry even though the renderer ignored the rebuild; the uploaded mesh then retained gray structural faces despite correct client theme data. Rejoining rebuilt the mesh from the cached/server definitions.

Dirty entries now remain owned by the client queue until Minecraft's `LevelRenderer.isSectionCompiled` reports readiness (Sodium maps this to its own built state). Unbuilt sections move to a separate round-robin queue with at most 32 readiness checks per tick; they cannot monopolize the distance-prioritized rebuild queue. The existing maximum of four rebuild attempts per tick remains. Unloaded entries are discarded and reacquired through chunk-load events; disconnect clears both queues. No packet resends, fixed delays, terrain regeneration, production mixins or Sodium dependency are needed.

The actual-client fixture now enables first-join Backdoor starts before login. In Sodium profiles, a test-only mixin delivers the starter column immediately before its first mesh upload and processes the real rebuild queue while the section is still unbuilt. This deterministically reproduced the gray room (zero red fixture pixels) before the correction. The test requires actual themed pixels after the same ordering, then continues through live updates, dimension reentry and the existing tool checks. The fixture mixin and all check classes are excluded from the release JAR.
