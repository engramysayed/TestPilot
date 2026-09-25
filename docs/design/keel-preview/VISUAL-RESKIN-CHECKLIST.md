# Keel visual reskin — phase verification

Reference mock: `index.html` served at [http://127.0.0.1:8769/](http://127.0.0.1:8769/)  
Production: [http://localhost:8080/](http://localhost:8080/)

Styles ship in **`portal.css`** + overlay **`keel-reskin.css`** (always in that order). Templates load both via **`fragments :: portalStyles`** (`pageHead` / `authPageHead`). See **`RELEASE-READINESS.md`** for deploy checklist.

**Admin invites:** `/admin/invites` → redirect `/admin/users` (no standalone invites template).

## Still out of scope (mock-only or deferred)

| Item | Notes |
|------|--------|
| `/devices`, Android project type | N5/N6 epic — filter tabs on Projects show **Web/Android disabled** |
| Full repo `mvn test` | Gate uses **portal MVC batch** (see plan) |

## Shipped after reskin fix pass (2026-09-24)

| Item | Prod | Tests / tooling |
|------|------|-----------------|
| Global topbar search | `GET /api/portal/search` + `#globalSearch` | `PortalNavSearchMvcTest` |
| Project card KPIs | `libraryCaseCount`, `provenPassRatePercent`, progress bar | `ProjectListKpiMvcTest` |
| Visual screenshot sign-off | `docs/design/keel-preview/visual-signoff.ps1` | `KeelVisualSignoffScriptTest` (+ run script when portal/mock up) |

---

## Phase 1 — Foundation

| Check | Mock | Prod | Done |
|-------|------|------|------|
| Body background `#f5f7f9` paper | ✅ | ✅ | ✅ |
| Sidebar `#112a35` dark nav | ✅ | ✅ | ✅ |
| Workspace pill under brand | ✅ | ✅ | ✅ |
| Nav active teal rail indicator | ✅ | ✅ | ✅ |
| Sidehelp “Designed for clear evidence” | ✅ | ✅ | ✅ |
| Account row + avatar initials | ✅ | ✅ | ✅ |
| Topbar with breadcrumb | ✅ | ✅ | ✅ |
| Nav says “Overview” not Dashboard | ✅ | ✅ | ✅ |
| Automated: `KeelVisualReskinPhase1Test` | — | ✅ | ✅ |

## Phase 2 — Auth

| Check | Mock | Prod | Done |
|-------|------|------|------|
| `loginpage` two-column layout | ✅ | ✅ | ✅ |
| Dark brand panel + tagline | ✅ | ✅ | ✅ |
| Form uses `.field` labels | ✅ | ✅ | ✅ |
| request-access matches | ✅ | ✅ | ✅ |
| invite matches | ✅ | ✅ | ✅ |
| Automated: `LoginMvcTest` (+ invite) | — | ✅ | ✅ |

## Phase 3 — Overview

| Check | Mock | Prod | Done |
|-------|------|------|------|
| Hero “A clearer view of every run” | ✅ | ✅ | ✅ |
| 4-up stats bar | ✅ | ✅ | ✅ |
| Attention rows styling | ✅ | ✅ | ✅ |
| Chart + recent runs panels | ✅ | ✅ | ✅ |
| Automated: `KeelVisualReskinPhase3Test` (+ attention markup) | — | ✅ | ✅ |

## Phase 4 — Projects

| Check | Mock | Prod | Done |
|-------|------|------|------|
| Project card grid | ✅ | ✅ | ✅ |
| Tab strip on project detail | ✅ | ✅ | ✅ |
| Summary strip on project summary tab | ✅ | ✅ | ✅ |
| Filter tabs (All active; Web/Android planned) | ✅ | ✅ | ✅ |
| Automated: `KeelVisualReskinPhasesTest` phase4 | — | ✅ | ✅ |

## Phase 5 — Workflow

| Check | Mock | Prod | Done |
|-------|------|------|------|
| Generate / Execute / Automate panels | ✅ | ✅ | ✅ |
| Workspace launcher styling | ✅ | ✅ | ✅ |
| Automated: `GenerateMvcTest`, `ExecuteMvcTest`, `AutomateShellMvcTest` | — | ✅ | ✅ |

## Phase 6 — Runs

| Check | Mock | Prod | Done |
|-------|------|------|------|
| Status progress + metadata | ✅ | ✅ | ✅ |
| Evidence tabs + IR | ✅ | ✅ | ✅ |
| Runs table badges (`badge--pass` / `--run` / …) | ✅ | ✅ | ✅ |
| Automated: `StatusPageMvcTest`, `EvidenceMvcTest`, `JobsPageMvcTest`, `KeelVisualReskinPhasesTest` status | — | ✅ | ✅ |

## Phase 7 — Secondary

| Check | Mock | Prod | Done |
|-------|------|------|------|
| Packages, guide, account, admin | ✅ | ✅ | ✅ |
| TC guide `heading` / `eyebrow` hero | ✅ | ✅ | ✅ |
| Automated: `PackagesMvcTest`, `GuideMotionMvcTest`, `Phase6SettingsAndHuntMvcTest` | — | ✅ | ✅ |

## Phase 8 — Closeout

| Check | Done |
|-------|------|
| Responsive 1100px / 720px | ✅ |
| Portal MVC regression batch green | ✅ |
| PARITY-CHECKLIST visual note | ✅ |
| Automated: `KeelVisualReskinPhasesTest` | ✅ |

## Fix pass (2026-09-24)

See [`docs/superpowers/plans/2026-09-24-keel-reskin-fixes.md`](../../superpowers/plans/2026-09-24-keel-reskin-fixes.md).
