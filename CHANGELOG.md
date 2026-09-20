# Changelog

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
