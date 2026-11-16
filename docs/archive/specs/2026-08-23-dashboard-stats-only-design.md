# Dashboard stats-only refresh (v2)

**Date:** 2026-08-23  
**Status:** Approved — implementing  
**Supersedes:** P4 verb-card hero (navigation moves to sidebar only)  
**Related:** [`2026-08-23-dashboard-refresh-design.md`](./2026-08-23-dashboard-refresh-design.md)

---

## 1. Goal

`/dashboard` becomes **statistics and charts only** — no verb cards, no “Open Generate/Execute/Automate” CTAs. Navigation stays in the sidebar. Layout inspired by operational dashboards: **KPI tiles**, **status pie**, **scores bar chart**, **read-only activity table**.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| Verb cards | **Remove** from dashboard |
| Empty states | Muted copy only — no section deep links |
| KPI tiles | Projects, Conversions, Execute runs, Running, Completed, Failed, Passed, TODO, Pass rate % |
| Charts | Bar: scores over time (existing); Doughnut: job status mix; Doughnut: passed vs TODO totals |
| Job table | Keep as **read-only activity** — Status/ZIP links OK (job actions, not section nav) |
| Data | `DashboardService` extended; conversion + execute job kinds both counted where relevant |
| Welcome CTA | Single line hint “Create a project in Projects” — no hero button |

---

## 3. Bug report reference (product doc)

See §4 in this spec — documented for authors using Execute.

---

## 4. Bug report format & rules

**Endpoints:** `GET /api/execute-runs/{jobId}/bug-report` (JSON), `.../bug-report.csv`

**CSV columns:**

`tcId`, `title`, `qaStatus`, `failureReason`, `blockerStepIndex`, `blockerIntent`, `visualAssertStatus`, `designCompareStatus`, `screenshotUrls`

**A row is included when:**

1. **`qaStatus == FAIL`** — TC did not pass Execute (step blocked, assertion failed, dry-run stub, etc.)
2. **`designCompareStatus == MISMATCH`** — design reference compare failed even if QA passed

**Not included:** PASS with MATCH/SKIPPED design compare, UNCERTAIN-only (unless also FAIL).

**Design references (Execute):** PNG mockup per `TC_ID` uploaded in Project Settings or Execute — used by design-vs-actual compare during execute runs.

---

## 5. Out of scope

- Real-time websocket updates
- Fleet-map / geospatial widgets
- Clickable KPI tiles that navigate to sections
