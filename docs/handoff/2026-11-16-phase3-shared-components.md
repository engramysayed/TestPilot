# Phase 3 — Shared project picker + TC multiselect (2026-11-16)

## Shipped

- **`keel-project-picker.js`** — unified `/api/projects` load, launcher, pin, session storage.
- **`keel-tc-multiselect.js`** — dropdown multi-select for library cases (search, select all, chips).
- **Multi-model generate** — `generate.js` no longer crashes on missing bulk/import DOM; project list loads via shared picker.
- **Suites / Scheduling** — shared picker + safe `projectSel` guards (fixes “Could not load projects” when script aborted).
- **Generate from knowledge** — library ingest uses TC multiselect UI (same pattern as Execute).

## Verify

1. `/generate/compare` — project dropdown populates; Continue pins project.
2. `/suites/schedule` — project loads; no error under pinned header.
3. `/generate-from-knowledge` — pin project → **Cases for this pack** multiselect.

## Next

Phase 4 — English file inputs; Phase 5 — dedupe recent job tables.