# P3 — Nav IA + Automate shell

**Date:** 2026-08-23  
**Status:** Approved (2026-08-23)  
**Roadmap:** [`2026-08-23-keel-product-roadmap.md`](./2026-08-23-keel-product-roadmap.md) phase **P3**  
**Depends on:** P2 Projects hub (project picker + Upload wired to project config)  

---

## 1. Goal

Align the portal navigation with the roadmap’s **three product verbs** (Generate, Execute, Automate). **Automate** becomes the home for conversion: an `/automate` hub plus nested **Upload** and **Jobs** in the sidebar. **Generate** and **Execute** appear as visible nav entries with **coming-soon** land pages until P6/P7.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| Automate placement | **A** — sidebar parent; Upload + Jobs nested underneath |
| Sidebar behavior | **A + B** — expandable group **and** `/automate` land page |
| Automate land page | **A + B + C** — hub cards + recent jobs (last 5) + **project picker** |
| Other verbs in nav | **B** — Generate \| Execute \| Automate all visible; Gen/Exec = coming soon |
| URL stability | Keep `/upload`, `/jobs`, `/status` (no `/automate/upload` in P3) |

---

## 3. Scope

### In scope

- Sidebar restructure in `fragments.html` (expandable Automate group)
- New pages: `/automate`, `/generate`, `/execute`
- Automate hub: project picker, action cards, recent jobs widget
- Optional `GET /api/jobs?projectId=` filter (and client-side limit 5)
- `navActive` wiring for parent + children
- Breadcrumbs / back links on Upload (`← Automate`)
- Remove top-level Upload/Jobs from sidebar

### Out of scope

- P4 Dashboard refresh
- P6 Generate implementation
- P7 Execute implementation
- URL migration to `/automate/*`
- Real-time job websockets

---

## 4. Navigation & routes

### 4.1 Sidebar order (Workspace)

1. Dashboard  
2. Projects  
3. **Generate** → `/generate`  
4. **Execute** → `/execute`  
5. **Automate** → `/automate`  
   - **Upload** → `/upload`  
   - **Jobs** → `/jobs`  
6. Guide  

Account / Admin unchanged.

### 4.2 Expand/collapse

- **Automate** row toggles sub-links (Upload, Jobs)
- **Auto-expand** when `navActive` is `automate`, `automate-upload`, or `automate-jobs` (includes `/status`)
- Persist open state in `sessionStorage` key `keel.automateNavOpen` (default open on first visit)

### 4.3 `navActive` values

| Page | `navActive` |
|------|-------------|
| `/automate` | `automate` |
| `/upload` | `automate-upload` |
| `/jobs`, `/status` | `automate-jobs` |
| `/generate` | `generate` |
| `/execute` | `execute` |

Parent **Automate** styling applies when any `automate*` value is active.

### 4.4 Routes

| Path | Template | Controller |
|------|----------|------------|
| `/automate` | `automate.html` | `PortalUiController.automate()` |
| `/generate` | `generate.html` | `PortalUiController.generate()` |
| `/execute` | `execute.html` | `PortalUiController.execute()` |
| `/upload` | `upload.html` (existing) | `navActive` → `automate-upload` |
| `/jobs` | `jobs.html` (existing) | `navActive` → `automate-jobs` |

---

## 5. Page content

### 5.1 `/automate` hub

**Layout:**
1. Title + lede (“Turn manual test cases into automation packages”)
2. **Project** dropdown (non-archived projects from `GET /api/projects`)
3. Two action cards:
   - **New conversion** → `/upload?projectId={selected}`
   - **Job history** → `/jobs` (full list)
4. **Recent jobs** panel — last **5** for selected project

**Recent jobs table:** job id, status badge, progress, P/T score, link to `/status?jobId=`

**Empty state:** “No jobs for this project yet” + button to Upload with project pre-selected.

**Project picker persistence:** `sessionStorage` key `keel.automateProjectId`; fallback first project in list.

**Data:** `GET /api/jobs?projectId={id}` (new optional filter); sort newest first; slice to 5 in UI.

### 5.2 `/generate` (coming soon)

- Headline: **Generate test cases**
- Body: From user stories / PRD → Keel-format workbook (planned P6)
- CTAs: **TC Guide** (`/tc-guide#ai`), **Projects** (`/projects`)
- Visual: muted “Coming soon” badge — not disabled nav (page is reachable)

### 5.3 `/execute` (coming soon)

- Headline: **Execute & verify**
- Body: Run manual TCs with DOM + vision; bug-finding without codegen first (planned P7)
- CTA: **Projects** for URL/credentials setup

### 5.4 Cross-page updates

| File | Change |
|------|--------|
| `jobs.html` | Keep “New conversion” → `/upload`; optional muted “Automate” context |
| `upload.html` | Link “← Automate” to `/automate` |
| `project-detail.html` | “Upload / Update” link remains `/upload?projectId=` (valid) |
| `fragments.html` | Remove standalone Upload/Jobs top-level links |

---

## 6. API

### 6.1 Jobs list filter

Extend `GET /api/jobs`:

| Query | Behavior |
|-------|----------|
| (none) | All owned jobs, newest first (existing) |
| `projectId=prj_x` | Only jobs for that project (owner must own project; else empty list or 404 — prefer **empty list** if unknown project to avoid leaking ids) |

Response shape unchanged.

---

## 7. UX / CSS

- Sidebar group: indent sub-links; chevron on Automate parent
- Active state: parent Automate highlighted when child active
- Automate hub cards: match existing `panel` / dashboard card patterns in `portal.css`
- Coming-soon pages: simple hero + badge, no fake forms

---

## 8. Testing

| Test | Covers |
|------|--------|
| `AutomateShellMvcTest` (new) | Authenticated GET `/automate`, `/generate`, `/execute` → 200 |
| Extend `PortalApiTest` or new | `GET /api/jobs?projectId=` filters correctly |
| Manual | Sidebar expand; nav highlight on Upload/Jobs/Status; picker persists; recent jobs refresh on project change |

---

## 9. Success criteria

1. Sidebar shows **Generate**, **Execute**, **Automate** (with Upload/Jobs nested); no top-level Upload/Jobs.
2. `/automate` provides project context, action cards, and last 5 jobs.
3. `/generate` and `/execute` explain future flows and link to Guide/Projects.
4. `/upload` and `/jobs` URLs and flows unchanged for bookmarks and deep links.

---

## 10. Key files (implementation)

| Area | Paths |
|------|-------|
| Nav | `templates/fragments.html`, `static/css/portal.css` |
| Pages | `automate.html`, `generate.html`, `execute.html` |
| Controller | `PortalUiController.java` |
| API | `JobController.java` |
| Tests | `src/test/java/delivery/portal/web/AutomateShellMvcTest.java` |

---

## 11. Spec self-review

- [x] Locked decisions (A+B sidebar, A+B+C hub, B three verbs) encoded
- [x] P2 dependency noted (project picker reuses project list API)
- [x] No URL breaking changes
- [x] Coming-soon scope clear vs P6/P7
- [x] Out of scope separated from P4 dashboard
