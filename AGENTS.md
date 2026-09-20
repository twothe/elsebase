# Elsebase

## Behaviour

- Current phase: technical bootstrap and design discussion. Implement gameplay only after the user's explicit approval. The design document is a proposal, not that approval.
- Communicate in German. Code, identifiers and technical documentation use English, following the original specification.
- Preserve `docs/backroom-industry-design.md` as the original input. Record open questions and agreed changes separately until the design is approved.
- Target Minecraft 1.21.1, NeoForge and Java 21. Use the checked-in Gradle wrapper and pinned versions.
- Keep common/server code independent of client classes. Portal traversal must remain independent of optional previews.
- Verify version-sensitive assumptions against the resolved Minecraft/NeoForge sources before implementing them.
- Do not add speculative registries, configuration, dependencies or gameplay scaffolding before the relevant design is agreed.
- Treat industrial, magical and other appearances as themes over common gameplay. Each player has one instant pair; initial references use world seed plus UUID. Claims mods provide territory protection.
- One visual style per modpack is sufficient; do not introduce simultaneous per-player or per-area theme architecture.
- All seven gallery styles are requested for inclusion where feasible; the default style remains undecided.
- Protect the Backdoor's generated/restored structural walls, floors and ceilings from accidental destruction, including explosions. Player-placed blocks follow normal rules. Mining by players, machines, drills, block breakers and fake players is allowed subject to normal permissions.
- Include a darkness option: no ambient brightening or generated light sources, independently combinable with natural mob spawning. Player-provided light follows normal rules.
- Generation-affecting config changes do not require retroactive migration: users need a new world for consistent results or accept old/new generation artifacts. Preserve existing player construction and validate changes that would corrupt saved state; this policy does not authorize remapping allocations.
- The user selected Elsebase as the mod name and the first logo direction, The doorway beyond (Die Tür ins Anderswo). The raster concept is the design reference; production assets remain unfinished. Naming/design approval does not authorize gameplay implementation or technical identifier migration.

## Project Overview

Elsebase (formerly the working title Backroom Industry) proposes a permanent workspace dimension with chunk-aligned rooms, reversible structural editing and constrained cross-dimensional doorways. Appearance should fit technology, magic and other modpacks. The current code is a minimal loadable mod entry point only; technical identifiers still use the bootstrap name.

## Documentation Index

- [Original design](docs/backroom-industry-design.md): supplied design and technical specification; not yet approved for implementation.
- [Development setup](docs/development.md): toolchain provenance, build/run instructions and verification status.
- [Design discussion](docs/design-discussion.md): technical findings, unresolved contracts and proposed discussion order.
- [Start points and portals](docs/start-points-and-portals.md): UUID allocation math, coordinate precision, latest portal/anchor requirements and placement trade-offs.
- [Name, story and visuals](docs/name-story-and-visual-directions.md): three unselected creative candidates and an illustrative concept board.
- [Themes and configuration](docs/themes-and-configuration.md): resource-pack versus datapack options, seven requested styles, and a minimal configuration proposal.
- [Theme gallery](docs/theme-gallery.md): seven accepted visual directions requested for inclusion and their generation provenance; default style undecided.
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
