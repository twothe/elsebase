# Changelog

## 1.1.0 — 2026-09-24

- Fixed rooms retaining gray structural textures when themes arrive during Sodium's initial mesh build, including direct Backdoor starts. Appearance rebuilds now remain pending until the section is ready.
- Fixed personal entry being blocked by construction on or above the spawn anchor. Entry, initial starts and respawns first find safe nearby footing; if none exists, a bounded emergency repair clears the anchor column subject to protection permissions. Outside summons no longer require a clear anchor.

- Incoming attacks delay instant portal summons/recalls for three seconds by default, configurable from 0 (disabled) to 300 seconds. Fire, poison and wither ticks do not renew the delay; existing portal travel remains usable.
- Optional first-join starts at the player's personal Backdoor anchor, with a respawn point there. Later logins retain their location; missing respawn points recover to the Backdoor, while valid chosen respawns remain respected.
- Independent option to refuse instant summons inside the Backdoor when no outside destination is known. Outside summons and remembered returns remain usable.
- Native config labels, help and new messages translated in all eight supported languages.
- Existing 1.0.0 worlds remain compatible; world format 4 and template format 2 are unchanged. Both modpack options default to off.

## 1.0.0 — 2026-09-21

First stable release of Elsebase for Minecraft 1.21.1 and NeoForge.

- Summon a personal doorway with **F** and enter your persistent workspace dimension from the start of the game.
- Separate player starting regions, movable spawn anchors, online anchor chunk loading and safe return handling.
- Sixteen stacked room levels, independent double walls, varied passages and a bedrock bottom.
- Construction and Removal tools with gaze-based surface previews.
- Seven complete built-in themes, plus Paint and Scan tools for custom wall, floor and ceiling patterns.
- Shared server-authoritative themes, personal defaults, import/export and confirmed deletion of owned themes.
- Permanent craftable portal pairs and automatic cleanup of inactive personal portals while their owner is outside.
- Bounded live portal previews and immersive portal-only transitions. Active Iris shaderpacks use an animated fallback portal surface.
- In-game NeoForge configuration for essential gameplay, appearance and performance settings.
- Searchable default-theme selector in the native config UI, including local library designs and native undo/reset.
- Eight interface languages, translated biome and built-in theme names, localized theme search and automated catalog completeness checks.
- Hardened save validation, repaired appearance resynchronization and development-free release packaging.

### Requirements and notes

- Minecraft **1.21.1**, NeoForge **21.1.250 or newer within 21.1**, Java **21**.
- Install Elsebase on both the client and server. No additional gameplay mod is required.
- **F** also defaults to vanilla's swap-hands action; change one binding in Controls.
- Live previews omit entities and special block-entity renderers. Shader compatibility uses the fallback described above.
- Existing final-development saves using world format **4** and theme format **2** remain compatible. Back up worlds before updating; older incompatible development formats are not migrated.
- Distribution metadata remains **All Rights Reserved**.
