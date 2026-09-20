# Elsebase project-page draft

Prepared for the owner's CurseForge project page. English is the proposed primary language; German is provided as an alternative. Nothing has been published. The mod artifact is unchanged.

## Files and placement

- [English page](../curseforge-description.md): complete copy with local image placements.
- [German page](project-page-de.md): equivalent localized copy.
- `images/elsebase-hero.png`: opening promotional banner.
- `images/elsebase-room-ideas.png`: optional inspiration banner after the theme section; omit it for a shorter page.
- `images/ingame-arcane-archive.png` and `images/ingame-deepstone-halls.png`: unmodified actual client menu screenshots from the existing approved theme gallery.
- [Image prompts and provenance](image-generation.json): exact prompts and visual review for the two built-in ImageGen outputs.

The Markdown image paths work within this package. For publication, upload the selected image files and insert them in the page editor, or replace the local references with their actual hosted image URLs. No hosted URLs have been invented. Keep the copy usable without image text: the headline and relevant benefits also appear as normal text.

Suggested short project summary:

> A permanent workspace behind a summonable doorway. Shape rooms, choose themes and bring your next base idea to life.

German alternative:

> Eine dauerhafte Basis hinter einer herbeirufbaren Tür. Gestalte Räume, wähle Themes und schaffe Platz für deine nächste Bauidee.

Optional room-ideas banner caption:

> Build inspiration — AI-generated illustration, not an in-game screenshot. Furnishings shown are creative examples, not automatically generated rooms or included furniture.

German caption:

> Bauideen — KI-generierte Illustration, keine Spielaufnahme. Die Einrichtung zeigt kreative Beispiele, keine automatisch eingerichteten Räume oder mitgelieferten Möbel.

Alt text for the optional banner: “Three illustrative voxel rooms suggest a workshop, a magical study and a storage room.”

The hero caption in both page drafts should remain attached to the image. Its oversized decorative portal surround, atmospheric lighting and furnishing are illustrative. It does not demonstrate the exact 1×2 runtime portal or shader rendering. The lettering is part of the illustration, not a replacement for the selected doorway logo.

## Editorial rationale and evidence

The page first answers what Elsebase gives the player, then how to shape and personalize it, then how return travel works. The closing invitation suggests a first build rather than pushing urgency or promising results. Intended readers are Minecraft builders and technology/magic modpack players considering a workspace dimension; this is inferred from the confirmed product scope, not audience research.

| Statement | Repository evidence |
| --- | --- |
| Summonable portal from the start, fixed outside return, expiry and online anchor chunk | `docs/player-guide.md`, `docs/start-points-and-portals.md` |
| Sixteen levels and structural tools | `docs/stacked-rooms-and-anchors.md`, `docs/player-guide.md` |
| Seven complete themes, personal defaults, scanning and server authority | `docs/surface-templates.md`, `docs/current-theme-gallery.md` |
| Limited live previews and active Iris fallback | `docs/portal-rendering.md` |
| Minecraft, NeoForge, Java and release scope | `gradle.properties`, `CHANGELOG.md`, `docs/release-readiness.md` |

Do not describe Elsebase as a protected private dimension, unlimited chunk loader, furnished-room generator, or universally compatible shader/machine renderer. Do not promise performance improvements, established popularity or measured conversion gains. The page does not make those claims.

Applied marketing method: `plan-website-experience` for page structure, benefit-first copy, evidence placement and an appropriate download invitation. `produce-channel-content` was consulted and routes website copy to that skill. Built-in `imagegen` produced the two illustrative assets. The marketing runtime was inspected read-only and is not initialized in this software repository (`.marketing-os/client.json` is absent). These are reversible repository drafts following the existing project workflow, with no invented marketing status, approval or publication record.

Self-check: product scope and qualifiers checked against the listed local release documentation; both generated images visually inspected; screenshot provenance retained; local image links and package contents checked. This is an editorial draft for the owner, not an independent marketing review or a published page. No current platform limits, SEO measurements or policy claims are assumed.
