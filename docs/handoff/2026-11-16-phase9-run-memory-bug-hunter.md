# Phase 9 — Run memory + Bug Hunter polish (2026-11-16)

## Shipped

- **`run-memory.html`** — `class="table table--run-memory"` inside `table-scroll` (was `table.status`, which applied the `dl.status` CSS grid and collapsed headers). Empty state, page lede, feature `?` on propose + list sections.
- **`keel-run-memory.css`** — table + panelhead polish.
- **`messages.properties`** — `feature.help.run-memory.short` = “Saved locators Keel reuses on later runs.” (sidebar already wired in `fragments.html`).
- **`bug-hunter.html`** — **Review last hunt report** (was “Review findings”), intro + `bug-hunter-triage` help.

## Verify

1. `/run-memory` — pin project → table columns: Id | Status | Summary | Actions (not one mashed header line).
2. Sidebar **Run memory** `?` — one-line saved-locators copy.
3. `/bug-hunter` with triage enabled — section title + History link in intro.

## Next

Phase 10 — Guide + tooltips sync; Phase 11 QA/a11y.