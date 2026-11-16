# Plain-English UI copy pass — kickoff (2026-11-02)

**Assigned by:** Maestro (user via Leader; parallel to Phase 3)
**Base tip:** `e9ee274` (or HEAD after P3 lands — stay current)
**Out of scope:** API paths, Java packages, DB columns; Sonic; Postman; no merge/force-push without user OK

## Goal

Simplify every complicated technical word in the Keel **portal UI** so anyone can understand it — everyday English, not jargon.

## Scope (UI strings only)

Nav, buttons, section titles, empty states, tooltips, form labels, help text, error messages, sidebar items — including Generate / From knowledge / Visual diffs / Evidence / Plain cases / Execute / Hunt / heal / suites / schedules / monitor, etc.

Direction examples (not a rigid glossary):
- locator → “how we find this on the page”
- heal → “fix broken steps”
- assert → “check”
- suite → “test pack”
- baseline → “saved good screenshot”
- flaky → “sometimes fails for no clear reason”
- knowledge pack → “test knowledge file” / “case pack”

Keep product feature names if needed, but always add a short plain phrase nearby.

## Owners

| Owner | Slice | Handoff |
|-------|--------|---------|
| **Keel Frontend** | All portal template/JS/sidebar copy; small slices + revise OK | `docs/history/handoffs/2026-11/2026-11-02-plain-english-ui.md` |
| **Keel Backend** | Only if a **user-facing** API error/label string lives server-side | note in same handoff or `…-plain-english-api-messages.md` |
| QA / AE / Codegen | Idle for this pass | — |

## Parallelism

Phase 3 (suites/schedules/alerts) continues. Prefer not to collide on the same HTML files in the same minute — if both touch a page, FE owns copy last-pass after P3 shell lands, or do copy on pages P3 is not editing yet.

## Done when

Portal reads plainly end-to-end; handoff lists files touched + before/after examples; Maestro refreshes portal jar.
