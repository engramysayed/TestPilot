# Portal UX Phase 10 — Guide + feature-help glossary sync

**Date:** 2026-11-16  
**Branch:** `ux-overhaul`

## Shipped

- **`messages.properties`** — every `feature.help.*.guide` URL points at a real Guide anchor (`#generate`, `#record`, `#execute`, `#evidence`, `#run-memory`, `#feature-glossary`, etc.). Overview/Projects titles no longer say “… help”.
- **`PortalUiController`** — `guideFeatureIds` list drives the glossary table (single ordered list for sidebar-aligned features).
- **`guide.html`** — **Feature glossary** section (`#feature-glossary`) table rendered from the same message keys as sidebar `?` popovers; TOC + overview cards use current nav names (TCs Generation, Playback, History, …); new **Test blueprints** (`#test-blueprints`) and **Run memory** (`#run-memory`) sections; section headings aligned with IA.
- **`portal.css`** — `.guide-glossary-table` readability.

## How to verify

1. `localhost:8080/guide` — open **Feature glossary**; each row’s link should jump to the matching section.
2. Any sidebar **`?`** — **More in Guide →** should land on the same anchor as the glossary row for that feature.
3. Spot-check renames: TOC **TCs Generation**, **Playback**, **TCs execution**, **History & evidence**.

## Known gaps (Phase 11)

- Full a11y/contrast pass not done here.
- Guide body still has legacy prose in deep sections (TC authoring); only nav-facing headings/TOC/glossary were synced in Phase 10.

## Files

- `src/main/resources/messages.properties`
- `src/main/java/delivery/portal/web/PortalUiController.java`
- `src/main/resources/templates/guide.html`
- `src/main/resources/static/css/portal.css`