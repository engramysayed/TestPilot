# Phase 6 — Generate page layout (2026-11-16)

## Shipped

- **`keel-generate-layout.css`** — stacked workflow cards (`generate-workflow-stack`), primary / secondary / tertiary surfaces.
- **`generate.html`** — four visible sections instead of one panel + nested `<details>`:
  1. Story generate (preview + review-with-AI stay in this card)
  2. Bulk CSV + recent bulk jobs table
  3. Paste from external AI
  4. Copy prompt for another AI
- **`messages.properties`** — `feature.help.generate-stories|bulk|import|copy-prompt` for section `?` buttons.

## Verify

1. Hard refresh **`/generate`** — pin a project → four separated cards with spacing.
2. Click **`?`** on each section header — popover copy + Guide link.
3. Queue bulk job → **Recent bulk jobs** appears only under bulk card.
4. Story generate, import, and copy prompt still work (no JS changes).

## Next

Phase 7 — Test suites schedule UX (see `2026-11-16-phase7-suites-schedule.md`).