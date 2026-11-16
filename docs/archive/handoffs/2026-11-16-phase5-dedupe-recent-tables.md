# Phase 5 — Dedupe recent job tables (2026-11-16)

## Shipped

- **Execute** — removed “Recent execute runs” table; compact **View execute runs in History →** CTA when a project is pinned. Active run UI (`job-panel`, results) unchanged; opens in `#run-detail-host` after start, session restore, or deep link.
- **Bug Hunter** — removed “Recent runs” table; **View hunts in History →** CTA. Triage “Pick a completed hunt” still loads from `/api/jobs?kind=HUNT`.
- **Unchanged (by design):** Generate **Recent bulk jobs**; Overview dashboard **Recent activity** (5 rows + History link).

## Known gaps

- Execute **delete run** modal remains in markup but is only reachable from the removed table — delete from **History** / job status flows.
- Orphan CSS for `#recent-execute-table` in `portal.css` / dark theme (harmless until a later cleanup pass).

## Verify

1. Hard refresh **Execute** — pin a project → no recent table; CTA visible; start a run → progress panel appears below form.
2. **Bug Hunter** — no recent table; triage dropdown still lists completed hunts when triage is enabled.
3. **Generate** — bulk jobs table still present.
4. **Overview** — recent activity capped at 5 with History link.

## Next

Phase 6 — Generate page section spacing (see `2026-11-16-phase6-generate-layout.md`).