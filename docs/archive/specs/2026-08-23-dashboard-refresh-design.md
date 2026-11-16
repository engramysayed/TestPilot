# P4 — Dashboard refresh

**Date:** 2026-08-23  
**Status:** Approved (2026-08-23)  
**Roadmap:** [`2026-08-23-keel-product-roadmap.md`](./2026-08-23-keel-product-roadmap.md) phase **P4**  
**Depends on:** P2 Projects hub, P3 Nav IA + Automate shell  

---

## 1. Goal

Refresh `/dashboard` into a **modern landing overview** that orients users to Keel’s three product verbs while surfacing real conversion health: projects, jobs, pass/TODO totals, scores-over-time, and recent packages — with tasteful motion, clear empty states, and deep links into Projects, Automate, Generate, and Execute.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| Layout priority | **Balanced hero** — compact stat strip + three verb action cards in one row, then chart + recent activity below |
| Metrics | **Keep** existing stat cards + Chart.js scores-over-time (passed vs TODO by day) |
| Verb cards | **Generate** → `/generate`, **Execute** → `/execute`, **Automate** → `/automate`; Gen/Exec show “Coming soon” badge (reuse P3 pattern) |
| Primary CTA | Remove lone “New conversion → `/upload`”; Automate card is the conversion entry point |
| Animation | **Subtle CSS only** — staggered card reveal on load, hover lift on verb cards; no websockets, no count-up libraries |
| Running jobs | Add **Running** stat (jobs in `RUNNING` / `PENDING`); badge on recent rows when in progress |
| Archived projects | **Exclude** from active project count and project filter options; **include** their historical jobs in job totals/chart |
| Data source | Extend existing `DashboardService` + `GET /api/dashboard/stats`; server-render on first paint (Thymeleaf), filters stay client-side |
| Empty state | First-time user hero: welcome copy + “Create project” CTA; no jobs → point to Automate, not Upload |

---

## 3. Scope

### In scope

- Redesign `dashboard.html` layout (hero, verb cards, chart, quick glance, packages table)
- CSS in `portal.css`: `.dash-hero`, `.verb-cards`, `.dash-reveal` animation, responsive breakpoints
- Extend `DashboardService.statsFor`:
  - `activeProjectCount` (non-archived)
  - `runningJobs` count
  - `hasProjects` / `hasJobs` booleans for empty-state branching
  - Filter `projectOptions` to non-archived only
- Fix stale copy/links (`/upload` → `/automate` or `/projects` as appropriate)
- `DashboardMvcTest` — page renders 200, contains verb cards, no stale top-level Upload CTA
- Optional: extend `AuthOwnershipTest` or add API test for new stat fields

### Out of scope

- P5 Guide motion polish
- P6 Generate / P7 Execute implementation (cards link to coming-soon pages only)
- Real-time websockets / live job polling on dashboard
- Per-project health cards grid (defer unless trivial after hero ships)
- Dashboard personalization / saved filters
- Commit unless user asks

---

## 4. Page layout

### 4.1 Hero row (above the fold)

```text
┌─────────────────────────────────────────────────────────────────┐
│ Dashboard                                    [optional subtitle]  │
├──────────┬──────────┬──────────┬──────────┬─────────────────────┤
│ Projects │ Jobs     │ Running  │ Passed   │ TODO                │  ← compact stat strip (5 cards)
├──────────┴──────────┴──────────┴──────────┴─────────────────────┤
│ [Generate TCs]    [Execute & verify]    [Automate]                │  ← verb cards (reuse automate-card style)
│  coming soon         coming soon          live hub                │
└─────────────────────────────────────────────────────────────────┘
```

- Stat labels: **Projects** uses `activeProjectCount`; **Jobs** = all owned jobs; **Running** = non-terminal in-flight; **Passed** / **TODO** = sums across all jobs (unchanged semantics).
- Verb cards reuse `.automate-card` styling; Generate/Execute get `.coming-soon-badge` overlay.

### 4.2 Main grid (below hero)

Unchanged structure, refreshed styling:

1. **Scores over time** — Chart.js bar chart; project + date filters (non-archived projects only).
2. **Quick glance** — last 4 jobs with status badge (highlight `RUNNING`/`PENDING`), ZIP + Status links.
3. **Your packages** — full recent-jobs table with client-side filter sync to chart.

### 4.3 Empty states

| Condition | UI |
|-----------|-----|
| No projects | Hero shows welcome panel: “Create your first project” → `/projects`; verb cards muted/disabled except Automate → `/projects` hint |
| Projects but no jobs | Stat strip shows zeros; quick glance + table show empty copy → “Start in Automate” → `/automate` |
| Has jobs | Current behavior with updated links |

---

## 5. Data & API

### 5.1 `DashboardService` additions

| Field | Type | Rule |
|-------|------|------|
| `activeProjectCount` | int | `archived != true` |
| `runningJobs` | int | status in `RUNNING`, `PENDING`, `QUEUED` (match `JobRecord` terminal check inverse) |
| `hasProjects` | boolean | `activeProjectCount > 0` |
| `hasJobs` | boolean | `jobCount > 0` |
| `projectCount` | int | **Deprecated in UI** — keep in API for compat, UI uses `activeProjectCount` |

`projectOptions` lists only non-archived projects (name + projectId).

### 5.2 `GET /api/dashboard/stats`

Returns extended map; no breaking changes to existing keys.

---

## 6. Motion & accessibility

- `@keyframes dashReveal` — opacity + `translateY(8px)` → `0`, 280ms ease, stagger `animation-delay` on stat cards (50ms steps) and verb cards (100ms steps).
- `prefers-reduced-motion: reduce` — disable transforms/delays; instant show.
- Verb card hover: existing `.automate-card:hover` lift; no autoplay loops.

---

## 7. Navigation & deep links

| Element | Target |
|---------|--------|
| Generate card | `/generate` |
| Execute card | `/execute` |
| Automate card | `/automate` |
| Empty → create project | `/projects` |
| Empty → start conversion | `/automate` |
| Job status link | `/status?jobId=` |
| Job ZIP | `/api/jobs/{id}/download` |
| Project name in table (optional v1) | `/projects/{projectId}` if trivial; else defer |

Remove all “start from Upload” copy.

---

## 8. Testing

| Test | Assert |
|------|--------|
| `DashboardMvcTest.dashboard_rendersForAuthenticatedUser` | 200, contains “Generate”, “Automate”, “Execute” |
| `DashboardMvcTest.dashboard_noStaleUploadCta` | body does not contain `href="/upload"` as primary CTA |
| `DashboardServiceTest` (optional) | `activeProjectCount` excludes archived; `runningJobs` counts in-flight |
| Existing `AuthOwnershipTest` | `/api/dashboard/stats` still owner-scoped |

Run: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=DashboardMvcTest,AuthOwnershipTest" test`

---

## 9. Success criteria

1. Logged-in user lands on a dashboard that surfaces **active** project count, job health, and three verb entry points without scrolling on desktop.
2. Chart + table filters work as today; project dropdown excludes archived projects.
3. New user with zero projects sees a clear **create project** path, not a dead-end.
4. No references to top-level Upload as the primary dashboard action.
5. Animations respect `prefers-reduced-motion`.

---

## 10. Approaches considered

| Option | Summary | Why not chosen |
|--------|---------|----------------|
| **A — Metrics-first** | Stats + chart on top; verb CTAs secondary row | Under-emphasizes three-verb IA on the landing page |
| **B — Hub-first** | Large welcome + verb cards; metrics below fold | Returning users lose at-a-glance health |
| **C — Balanced** ✓ | Compact stats + verb cards in hero; detail below | Best fit for “one glance + orient to flows” |

---

## 11. Spec self-review

- [x] No TBD placeholders for locked decisions
- [x] Scope bounded; P5/P6/P7 explicitly out
- [x] Reuses P3 card/badge patterns
- [x] Archived project semantics explicit
- [x] Test plan included
- [x] No websockets / real-time scope creep
