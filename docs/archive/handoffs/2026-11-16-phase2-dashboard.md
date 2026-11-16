# Phase 2 — Overview dashboard (2026-11-16)

## Shipped

- **Execution trends:** Chart.js line chart — proven passes as filled area, blocked as dashed line; chronological x-axis.
- **KPI row:** Left accent bar, softer shadow (`keel-dashboard.css`).
- **Trend summary** mini-stats above chart (pass rate, execute jobs, proven passes).
- **Attention** items as rounded cards.
- **Recent activity:** 5 rows max; CTA → `/history`.
- Feature **`?`** on overview header and each main panel.

## Verify

`/dashboard` — hard refresh; toggle theme dark/light; Apply filters still updates chart + table.

## Next

Phase 3 — `keel-project-picker.js` + TC multiselect on GFK, compare models project bug, suites schedule projects.