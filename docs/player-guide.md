# Elsebase player guide

## Enter and return

Press **F** on solid ground with free space in front of you. Touch the animated portal surface within the copper-and-turquoise doorway to enter the Backdoor workspace. Access requires no item. F is also vanilla's default for swapping hands: rebind that action or the portal in Controls. Existing installations retain their saved keys; reset the Elsebase portal binding to adopt F.

Each player has one personal pair in total. F outside replaces the old pair and points to the home reference. F inside recalls only the inner doorway: the external return and reference stay fixed. If an inner doorway cannot be placed, F returns you directly outside instead. Only the owner can traverse a personal pair. Outside the Backdoor, an unused instant pair closes after 1,200 server ticks (one minute at normal tick rate). Summoning or successfully using a portal renews that minute. Both instant surfaces disappear; permanent portals remain. Inside the Backdoor the return never expires, including while logged out there. Offline outside owners still time out while the server runs; a stopped/paused world pauses its clock. Timer and residence survive saves/restarts.

Your reference carpet exists from your first login and keeps its chunk ticking while you are online, even outside the Backdoor. Every personal-portal entry lands on the current carpet, including after recalling the inner portal or moving the anchor. Moving it transfers the loading; logout releases it. Entry checks its support and restores missing floor. If protection or an obstruction prevents repair, portal entry is refused rather than placing you over a hole. Permanent portals retain their fixed inner destinations. Leaving never requires a healthy anchor or a complete outside portal. A blocked/missing outside doorway uses a safe landing nearby, preserving any construction at its former location.

Creating a doorway needs 1×2 clear blocks, approach space, floor support and a footprint contained in one chunk. An existing doorway triggers on body contact with its inner surface, including when falling after its floor was mined. Your body need not fit completely inside the opening. If the inner frame cannot be placed because its floor is missing, personal entry still lands on the repaired anchor pedestal without creating that frame. F inside remains available for recall or direct escape. Vanilla-replaceable plants and snow are cleared from the frame and its approaches. Fluids, solid terrain, machines and block entities are preserved; normal claim permissions apply. Under heavy simultaneous traffic, step back and retry if the server reports busy.

The last outside entry point is saved separately from portal blocks. If no personal pair exists, F uses that remembered return; if no valid return remains, it finds a safe landing near the overworld spawn. In a void spawn area it can add a small stone safety platform in empty space. It does not clear buildings or machines. Operators can still use `/elsebase rescue` for exceptional recovery. New portal placement and anchor repair respect claims; retirement of your own stale instant-portal surfaces cannot block renewal.

## Tools

| Item | Use |
| --- | --- |
| Spawn Anchor Tool / Spawn-Ankerwerkzeug | Use on a Backdoor floor. Creates a non-colliding marker and selects a clear doorway position one block behind it. The previous marker disappears. Changes every subsequent personal entry destination, not the outside return. |
| Portal Generator / Portalgenerator | Select a Backdoor floor, then an outside floor. Completion consumes one Threshold Core. Sneak-use a portal to remove both endpoints and recover its core. Only the owner/operator can remove it; permanent traversal is public. |
| Removal Tool / Löschwerkzeug | Look at the highlighted surface and right-click to remove generated structure. Walkway borders remain. |
| Creation Tool / Herstellwerkzeug | Look at the highlighted surface and right-click to build a solid wall or restore a floor/ceiling, including missing perimeter blocks. |
| Threshold Core / Portalkern | Cost of one permanent pair; default limit four pairs per owner. |

Tools have short localized tooltips. Structural tools select by your gaze: a translucent green preview shows creation, orange shows removal. Right-click works even when aiming into empty air; no mode switching or run mode remains. Shift does not change the operation. The current room is determined by your feet position and level. You can edit its floor, ceiling and four wall halves. Removal can also reach the immediately adjoining neighbor wall through an opening or after removing your own half; farther and diagonal surfaces are excluded. Creation first offers the virtual wall of your current room, even if its floor/ceiling border has been removed. The preview shows the surface footprint, not a guarantee that permissions or obstructions will permit the edit.

Tools are reusable without durability cost. Unfinished linking selections expire on logout/restart and consume nothing.

Panel operations preserve player construction. Restoration fails atomically when occupied, and creates solid walls even where the generator left an opening. Each click edits one surface. The server rechecks room proximity before applying queued work. Bulk editing yields no resources.

## Crafting

Patterns use a normal crafting table. I = iron ingot, C = copper ingot, R = redstone, S = stick, · = empty.

| Item | Pattern, top to bottom |
| --- | --- |
| Threshold Core | `ICI / CRC / ICI` |
| Portal Generator | `·C· / ·IR / I··` |
| Spawn Anchor Tool | `··C / ·I· / S··` |
| Removal Tool | `III / ·C· / ·S·` |
| Creation Tool | `·C· / ICI / ·S·` |

## Rooms and protection

Cells align to 16×16 chunks, with 14×14 clear interior and their own border on all four sides. Neighboring walls are two blocks thick. Target the facing half to remove it; the neighboring half remains independently editable. Floor/ceiling removal edits only the interior and preserves the entire supporting perimeter. Creation restores the full 16x16 slab, including manually removed border blocks. The dimension spans Y=0–127 with 16 levels on an eight-block grid. Shared floors/ceilings leave seven clear blocks, except the top level with six beneath its roof at Y=127. The initial reference is at Y=65. Bedrock at Y=0 remains unbreakable through the editing tools.

Every generated room has at least one exit. Generated passages are 2×2 with varying offsets; portals remain 1×2, and one-exit dead ends remain possible. All levels of a chunk are generated together; stairs and shafts are player-built. Removing a shared ceiling also opens the floor above. The removal tool refuses the entire panel if it supports any reference carpet, including when used from below.

Generated/restored structure survives explosions. Player construction, including player-placed copies of Elsebase blocks, follows normal rules. Machines, drills and fake players may deliberately mine structure subject to ordinary protection. Structural states cannot be moved by pistons and drop no resources. Claims mods provide territory protection.

Default initial references are at least 8,192 blocks apart within a 131,072-block radius. All 795 reservations persist even when anchors move. Separation does not prevent visiting others.

Beds and respawn anchors do not provide a Backdoor respawn point and retain their disabled-dimension behavior.

## Updating an existing world

Before release 1.0, development builds do not promise compatibility with older test worlds. Create a new test world after incompatible changes; automatic migration is not provided. This build requires save version 4. Worlds from the preceding double-wall build remain usable; older development formats require a new test world. Existing worlds are never automatically deleted or reset.

Existing room chunks are not regenerated: old openings and the old bottom layer remain. A new world gives consistent geometry and continuous bedrock everywhere. Creation uses the current slab geometry and builds solid walls.

## Themes and settings

Quiet Workshop is the default. Enable **one** optional Elsebase resource pack: Arcane Archive, Verdant Cloister, Astral Observatory, Deepstone Halls, Porcelain Sanctuary or Service Layer. Distribute the selected pack to all clients for a consistent modpack appearance.

Open **Mods → Elsebase → Config** from the main menu. Settings are stored in `config/elsebase-common.toml` and apply to this installation. Distribute that file as modpack defaults. On multiplayer servers, the server file controls gameplay and sends its brightness setting to clients; editing your local file cannot change server policy.

The dimension looks uniformly bright by default without emitting blocks in floors or ceilings. Enable `world.darkness` for normal dark lighting; player lights still work. This visual toggle applies without regeneration (server synchronization within about one second). Independently enable `world.allowNaturalMobSpawning` for monsters under vanilla spawning/difficulty rules: visual brightness does not supply block light or prevent spawning. Theme lamps are decorative. Generation changes still require a new world for consistent results or acceptance of mixed old/new rooms; builds are never regenerated automatically.

See [implementation details](implementation.md) for settings, skin customization and limitations.
