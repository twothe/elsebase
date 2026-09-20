# Stacked rooms, tools and anchor lifecycle

## Geometry

The dimension spans Y=0–127, with shared slabs at multiples of eight and a final roof at Y=127. This yields sixteen levels: normally seven clear blocks between slabs, and six on the top level. Two room levels fit in each 16-block-high chunk section. The initial reference remains at Y=65, leaving rooms above and below. Bedrock occupies Y=0. No automatic stairs or shafts are generated.

Each chunk owns all four borders; adjacent rooms have two separately editable wall halves. Both halves derive the same 2×2 passage from the canonical shared edge. Interiors are 14×14; floor/ceiling removal preserves the surrounding supports, while creation repairs the full slab; wall corner pillars remain outside wall panels. Portals remain 1×2.

Each cell chooses one mandatory horizontal exit using the world seed and its level. A boundary opens if either adjacent cell requires it; the choice is pure and independent of chunk generation order. Other sparse openings and variable door offsets remain. This guarantees at least one usable exit per room, including negative coordinates and every level, without promising one globally connected maze.

## Full-height versus dynamic levels

Generate the whole 128-block column when Minecraft first requests that horizontal chunk. This is still lazy horizontal generation, not pregeneration of the entire dimension. A column contains only eight chunk sections, compared with the old dimension's 24. Section palettes compress repeated states; empty and populated sections do not have identical cost.

A local production-layout sample of 64 columns averaged 10,308.5 solid blocks per column and took about 17 ms for geometry evaluation. The old 65-block solid foundation alone contained 16,640 blocks per column. The new generator evaluates 32,768 positions rather than the old 20,992; fewer block writes do not automatically mean lower total runtime. The sample excludes Minecraft lighting, serialization, disk I/O and machine tick costs, and is not a 100-player benchmark. No light sources are generated. Default brightness is a client lightmap policy; darkness restores normal lighting without changing geometry.

Dynamic vertical generation would require persistent per-level status, entry/edit interception, relighting and rules preventing later generation from overwriting player-built shafts or rooms. It would not allow independently unloading vertical sections: normal Minecraft chunk tickets address horizontal columns. At this height the extra state and destructive-overlap risks are not justified by the measured geometry cost. Revisit only with evidence from a real modpack profile.

## Anchor lifecycle and loading cost

- First login allocates the personal reference and places the marker on its arrival side. Every personal entry lands centered on the current marker, even after inner recall. Moving the marker uses the existing reusable tool and preserves the external return.
- Each online owner holds one region ticket at the marker's horizontal chunk, including while in another dimension. Movement transfers the ticket; logout and server stop remove it. No offline anchor tickets are persisted. Login reacquires the ticket.
- The ticket uses vanilla level 31 (entity-ticking center). Distance propagation can retain up to a 5×5 full-chunk neighborhood plus outer generation dependencies. **One anchor is not one total loaded chunk.** With 100 widely separated online anchors, up to 2,500 full chunks can be retained before player-view and portal loads. Overlap deduplicates engine work; actual memory and ticking cost depend on builds. This follows the pinned `DistanceManager.addRegionTicket` implementation (`FULL level 33 - distance 2`).
- Anchor tickets are independent roots for direct portal mirror loading. The existing mirror limit still bounds mirror endpoints; it does not cap the mandatory anchor roots or their engine neighbors.
- Every personal portal entry checks the entering player's marker and support before teleporting. Dimension-change events also check entry from other systems, but cannot retroactively intercept another mod's teleport. Missing replaceable support becomes a protected floor block through the normal claim-aware edit transaction. Obstructing nonreplaceable blocks and block entities are preserved; a failed repair refuses personal entry; it never refuses exit. Other portal destinations retain their collision and safe-footing checks.
- The removal tool refuses an entire panel containing support for any physical or saved anchor. This includes a ceiling directly underneath an upper-level marker. Ordinary manual/automated mining remains allowed; the next entry repairs missing support.

## Tools

Removal and creation are separate items with different models, names and recipes. Both now use gaze-based selection and work on air or blocks. Mode cycling, stored mode state and RUN were removed. The client previews the shared selection as a translucent surface; the server independently derives it from player pose. Creation uses the virtual current-room shell so fully missing walls remain reachable. Removal can additionally target the immediately adjoining exposed neighbor wall half, but no farther surfaces. Each click queues one panel, with proximity revalidated at execution.

Floor/ceiling removal covers the 14x14 interior; creation covers all 16x16 blocks to repair hand-mined border strips. Shared slabs remain an upper floor and lower ceiling. Wall creation builds solid halves rather than seeded openings. Edits remain atomic and preserve player blocks, entities, claims and marker supports. F still summons portals; no structural keybind is needed.

## Development compatibility

Use a new test world for independent four-sided room boundaries. Saved-data version is 3, with a required marker for each personal home. No migration is provided before 1.0. GameTests use `build/gametest-v4`; earlier test worlds were left intact.
