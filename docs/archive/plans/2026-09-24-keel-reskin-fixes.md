# Keel reskin fixes — phased plan (2026-09-24)

**Goal:** Close confirmed bugs, tighten visual parity vs mock (without new APIs), and align docs/tests with actual implementation (`keel-reskin.css` overlay).

---

## Phase 1 — Confirmed bugs / regressions

| Step | Work |
|------|------|
| 1.1 | Fix Overview attention DOM to use `.attention-item` / `.attention-mark` / `.attention-title` |
| 1.2 | Fix `portal.css` `--border` → `--line` on attention rows |
| 1.3 | Remove orphan `automate.html`; document `/automate` → `upload.html` in controller + reskin plan |
| 1.4 | Remove orphan `admin-invites.html`; `/admin/invites` redirects to `/admin/users` |
| 1.5 | `fragments :: portalStyles` — single CSS load path; `KeelStaticAssetsTest`, `ProjectsMvcTest` |
| 1.4 | Remove misleading “Web” badge on project cards (keep Archived only) |

**Validate 1:** `DashboardMvcTest`, `KeelVisualReskinPhase3Test` + assert `attention-item` in dashboard script source.

---

## Phase 2 — Visual parity (no new backend)

| Step | Work |
|------|------|
| 2.1 | `/runs` table: map status badges to `badge--pass/fail/block/run` like Overview |
| 2.2 | TC guide hero: `eyebrow` + align with Overview heading pattern |
| 2.3 | Projects: mock-style filter tab strip — **All** active; Web/Android disabled (“planned”) |
| 2.4 | `keel-reskin.css`: attention rows on overview + disabled tab styling |

**Validate 2:** `JobsPageMvcTest`, `GuideMotionMvcTest`, `KeelVisualReskinPhasesTest` (projects tabs, runs badges).

---

## Phase 3 — Ambiguities / docs / tests

| Step | Work |
|------|------|
| 3.1 | Update `2026-09-23-keel-visual-reskin.md` — CSS overlay, automate template note |
| 3.2 | Honest `VISUAL-RESKIN-CHECKLIST.md` (validation sources, out-of-scope mock items) |
| 3.3 | `LoginMvcTest.invite_matchesLoginChrome` |
| 3.4 | Extend `KeelVisualReskinPhasesTest` — `/status?jobId=` smoke when job exists |

**Validate 3:** `LoginMvcTest`, `KeelVisualReskinPhasesTest`, `StatusPageMvcTest`.

---

## Phase 4 — Closeout

| Step | Work |
|------|------|
| 4.1 | Full portal MVC reskin regression batch (same set as 2026-09-23 gate) |

**Validate 4:** `mvn test` on listed portal tests — all green.

---

## Execution log

| Phase | Status |
|-------|--------|
| 1 — Bugs | ✅ Implemented + Validate 1 |
| 2 — Visual parity | ✅ Implemented + Validate 2 |
| 3 — Docs / tests | ✅ Implemented + Validate 3 |
| 4 — Closeout | ✅ Portal MVC batch |
| 5 — Global search, card KPIs, visual sign-off script | ✅ Implemented + tests |

---

## Phase 5 — Former “out of scope” mock gaps

| Step | Work |
|------|------|
| 5.1 | Topbar `#globalSearch` + `GET /api/portal/search` + `portal-nav.js` |
| 5.2 | `ProjectSummaryService.cardMetrics` on `GET /api/projects`; card UI progress + case count |
| 5.3 | `visual-signoff.ps1` / `visual-signoff.mjs` + `KeelVisualSignoffScriptTest` |

**Validate 5:** `PortalNavSearchMvcTest`, `ProjectListKpiMvcTest`, `KeelVisualSignoffScriptTest`.
