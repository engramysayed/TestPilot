# Keel visual reskin — implementation plan

**Goal:** Match the production Thymeleaf portal to the Keel design preview (`docs/design/keel-preview/index.html`) while keeping all functional parity from Phases 0–8.

**Reference:** Mock tokens, layout, and components in `index.html`. Verification checklist: [`docs/design/keel-preview/VISUAL-RESKIN-CHECKLIST.md`](../design/keel-preview/VISUAL-RESKIN-CHECKLIST.md).

**Out of scope:** Mobile N5/N6 (devices page), mock-only data, API changes.

---

## Phase 1 — Foundation (tokens + app shell)

**Files:** `keel-reskin.css` (overlay on `portal.css`), `fragments.html`, `PortalUiController.java`, all `app-main` templates (topbar insert).

**Note:** Production Automate route `/automate` renders **`upload.html`**, not `automate.html` (legacy shell removed 2026-09-24).

| Task | Detail |
|------|--------|
| 1.1 | Replace `:root` with mock palette (`--ink`, `--paper`, `--teal`, `--nav`, …) and alias legacy vars |
| 1.2 | Flat `--paper` body background; remove heavy gradients on app pages |
| 1.3 | Sidebar: `#112a35` nav, keel SVG brand, workspace pill, `WORKSPACE` navtitle, active rail |
| 1.4 | Sidebar foot: sidehelp tagline, account row with avatar initials |
| 1.5 | Topbar: crumb, user avatar; wire into every authenticated page |
| 1.6 | Rename nav label **Dashboard → Overview** (route stays `/dashboard`) |

**Gate:** `KeelVisualReskinPhase1Test` + existing portal MVC tests pass. Manual: sidebar + topbar match mock on Overview.

---

## Phase 2 — Auth surfaces

**Files:** `login.html`, `request-access.html`, `invite.html`, login CSS in `portal.css`.

| Task | Detail |
|------|--------|
| 2.1 | Replace `login-split` with mock `loginpage` grid (dark brand left, form right) |
| 2.2 | Copy lines: “From test intent to trusted evidence”, WEB AUTOMATION footer |
| 2.3 | Align request-access + invite to same auth chrome |

**Gate:** `LoginMvcTest` asserts `loginpage`, `loginbrand`, mock copy. Manual: side-by-side with mock sign-in preview.

---

## Phase 3 — Overview (dashboard)

**Files:** `dashboard.html`, dashboard CSS.

| Task | Detail |
|------|--------|
| 3.1 | Page chrome: `.eyebrow`, `.heading`, mock stat strip (4-up bar) |
| 3.2 | Attention list → mock `.attention` rows |
| 3.3 | Chart panel + recent runs → mock `.panel` / table styling |
| 3.4 | Title “Overview” in page H1 |

**Gate:** `DashboardMvcTest` updated for Overview label + new class hooks. Manual parity vs mock `overview()`.

---

## Phase 4 — Projects

**Files:** `projects.html`, `project-detail.html`, project CSS.

| Task | Detail |
|------|--------|
| 4.1 | Project cards → mock `.projectcards` / `.projectcard` |
| 4.2 | Project detail tabs → mock `.tabs` styling |
| 4.3 | Summary strip on project summary tab |

**Gate:** Projects MVC smoke + manual open Commerce-style project.

---

## Phase 5 — Workflow pages

**Files:** `generate.html`, `execute.html`, `upload.html` (served at `/automate`), workspace launcher CSS.

| Task | Detail |
|------|--------|
| 5.1 | Panels, formgrid, field labels match mock |
| 5.2 | Workspace launcher + pinned bar styled as mock IA banners |
| 5.3 | Buttons: `.primary`, `.small`, badge chips |

**Gate:** `GenerateMvcTest`, `ExecuteMvcTest`, `AutomateShellMvcTest` pass; launcher visible.

---

## Phase 6 — Runs cluster

**Files:** `status.html`, `evidence.html`, `jobs.html`.

| Task | Detail |
|------|--------|
| 6.1 | Status → mock progress track, status-dl, job-chip styling |
| 6.2 | Evidence → mock evidence-tabs, IR block, triage box |
| 6.3 | Runs table → mock tablewrap / badges |

**Gate:** `StatusPageMvcTest`, `EvidenceMvcTest`, `JobsPageMvcTest`.

---

## Phase 7 — Secondary pages

**Files:** `packages.html`, `tc-guide.html`, `account.html`, `admin-*.html`, `bug-hunter.html`.

| Task | Detail |
|------|--------|
| 7.1 | Packages table + modals |
| 7.2 | TC guide layout (guide-toc, guide-section) |
| 7.3 | Account / admin panels |

**Gate:** `PackagesMvcTest`, `GuideMotionMvcTest`, `Phase6SettingsAndHuntMvcTest`.

---

## Phase 8 — Closeout

| Task | Detail |
|------|--------|
| 8.1 | Responsive breakpoints from mock `@media` |
| 8.2 | Full `mvn test` portal suite |
| 8.3 | Update `PARITY-CHECKLIST.md` visual column |
| 8.4 | Side-by-side review: mock vs prod screenshots for login, overview, evidence |

**Gate:** All checklist rows ✅ for shipped pages; no regressions in functional parity tests.

---

## Execution order

Phases are sequential. Do not start phase N+1 until phase N gate passes.
