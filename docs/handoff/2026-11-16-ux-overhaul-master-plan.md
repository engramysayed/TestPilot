# UX overhaul master plan (Figma-aligned)

**Date:** 2026-11-16  
**Branch context:** `ux-overhaul` (portal templates under `src/main/resources/templates/`, sidebar in `fragments.html`, styles `keel-sidebar-roles.css` + `keel-reskin.css` + `portal.css`, page JS under `static/js/pages/`).  
**Design references:**

- [Sidebar Navigation Dashboard (Community)](https://www.figma.com/design/V2dIy0vzqdUC6b7TN1dRtC/Sidebar-Navigation-Dashboard--Community-?node-id=0-1)
- [Healthcare Dashboard (Community)](https://www.figma.com/design/trVxinl0fgMKGyThGxLv8X/Healthcare-Dashboard--Community-?node-id=0-1)

**Principle:** Ship in phases. After each phase: visual review on `localhost:8080`, short revision note in this file, then user sign-off before the next phase.

---

## Target information architecture (nav)

| Current | Target | Notes |
|---------|--------|--------|
| Generate with AI | **TCs Generation** | Parent group |
| Compare models | **Multi-model generate** | Child under TCs Generation |
| From case packs | **Generate from knowledge** | Child under TCs Generation (not top-level) |
| Record browser | **Playback** | Keep route `/record` unless redirect agreed |
| Structured plans | **TBD (user pick)** | Add `?` tooltip + rename options in glossary |
| Suites | **Test suites** | Child: Scheduling |
| Execute | **TCs execution** | Group header **Execute & automate** |
| Convert to code | **TCs automation** | Same group |
| Bug Hunter | Under **Execute & automate** | Not top-level Review |
| Runs & evidence | **History** | `/runs` |
| Run memory | **Run memory** (keep name) + glossary `?` | Only home for cross-page “recent jobs” |

**Section headers (Figma-style):** Workspace · Design tests · Execute & automate · History (optional) · Account.

---

## Cross-cutting workstreams

### A. Design tokens & sidebar shell (Phase 1)

- Extract spacing, radius, hover/active, subsection indent, chevron, label caps from Figma reference.
- Implement in `keel-sidebar-roles.css` + shared variables in `keel-reskin.css`.
- Hover: subtle background + left accent; children: smaller type + icon alignment.
- Persist open state per group (`localStorage` in `keel-sidebar-ia.js`).
- **Files:** `fragments.html`, `keel-sidebar-roles.css`, `keel-sidebar-ia.js`.

### B. Healthcare-style dashboard (Phase 2)

- Replace bar chart block in `dashboard.html` with stat cards row + attention list + calmer chart (area/line or card grid like Healthcare ref).
- Reuse existing APIs/data already wired to dashboard JS (grep `dashboard` in `static/js`).
- **Files:** `dashboard.html`, `portal.css` / `keel-reskin.css`, dashboard page script if any.

### G. Feature help — what every feature does (user request 2026-11-16)

**Intent:** Not LLM model names — **every product feature** (nav item, page, major section, confusing control) gets short help so users know *what it is* and *when to use it* without opening the full Guide first.

**Pattern (one system, reused everywhere):**

- Thymeleaf fragment `fragments :: featureHelp(featureId)` — small **`?`** control beside the label (keyboard-focusable, not hover-only).
- Popover: **one sentence** “what it does” + optional **“More in Guide →”** deep link (`/guide#…`).
- Copy in **`messages.properties`** (or `feature-help.properties`) keyed by stable ids, e.g. `feature.help.tcs-generation`, `feature.help.playback`, `feature.help.keep-watching`.
- Shared CSS (`keel-feature-help.css`) + tiny JS (`keel-feature-help.js`) for popover positioning / dismiss on Escape.

**Coverage map (roll out as we touch each phase):**

| Area | Examples needing `?` |
|------|----------------------|
| **Sidebar (Phase 1)** | Every link + section header (TCs Generation, Playback, Test suites, TCs execution, History, Run memory, Bug Hunter, …) |
| **Dashboard (Phase 2)** | Stat cards, “Needs attention”, chart filter |
| **Generate (Phase 6)** | Bulk vs story generate, paste external AI, copy prompt, double-check pass, All in one |
| **GFK / blueprints (Phase 3+)** | Packs, “Write test cases”, blueprint modes |
| **Schedule (Phase 7)** | Automatic runs, notify on fail, keep watching, credential for schedule |
| **Execute / Automate (Phase 8)** | Workbook vs library, tunnel, review before run, framework gate checkboxes |
| **Bug Hunter (Phase 9)** | Hunt strategies, scenario cap, review findings |
| **Run memory (Phase 9)** | Propose vs approved, locator hints |

**Phase 10 exit criteria:** Glossary complete — every renamed nav label has help text; Guide table of contents links back to the same ids (single source of truth).

**Out of scope for tooltips:** Raw dev strings (JVM properties, UUIDs) — remove or replace with user copy, not tooltip band-aids.

### C. Shared components (Phase 3) — highest ROI for bugs

| Component | Reference | Adopt on |
|-----------|-----------|----------|
| **Project picker** | Unified dropdown (fix compare + suites schedule + all `Could not load projects`) | `generate-compare.html`, `suites-schedule.html`, `fragments` or `keel-project-picker.js` |
| **TC multiselect** | `.tc-multiselect` on Execute/Automate (`upload.html`, `execute.js`) | `generate-from-knowledge.html`, Bug Hunter library pick |
| **Page header** | Breadcrumb + project chip (Figma top bar) | All job pages |

**Bug: Compare models — project dropdown empty:** trace `generate-compare` + `generate.js` `/api/projects` (same pattern as execute; likely missing init or wrong endpoint when `sidebarIa` flag).

**Bug: `/suites/schedule` — Could not load projects:** `suites-schedule` page JS vs `suites.js` project loader — align with working `/suites` or `/execute`.

### D. English-only UI & file inputs (Phase 4)

- Root cause of Arabic on file widgets: **browser/OS locale** on native `<input type="file">`, not i18n bundle (`messages.properties` only).
- Fixes: `lang="en"` on `<html>` (already), custom file button pattern (hidden input + visible English label), `accept` + `webkitdirectory` audit.
- Remove any leftover AR toggle in top bar (`fragments.html` header) if still present.
- Dates: keep text `yyyy-mm-dd` fields (guide + `keel-reskin.css` note).

### E. Remove duplicate “Recent *” blocks (Phase 5)

| Page | Action |
|------|--------|
| Execute | Remove or collapse “Recent execute runs” → link to **History** |
| Bug Hunter | Remove “Recent runs” table |
| Generate | Keep **Recent bulk jobs** only on Generate (not global) |
| Dashboard | Keep summary “Recent runs” or trim to top 5 + “View all” → History |
| Run memory | Canonical place for job chip + optional recent list |

### F. Page packs (Phases 6–11)

**Generate (`generate.html`):**

- Visual separation: primary **Story generate** card vs **Bulk CSV** card vs **Paste external AI** vs **Copy prompt** (spacing + `panel-divider` / secondary surface).
- Bulk jobs table stays below bulk card only.
- Use **feature help (G)** on section headers (bulk, paste, copy prompt). Optional later: short hint under **AI model** dropdown only if users still confuse Ollama tags (subset of G, not the main goal).

**Generate from knowledge:**

- Replace checkbox list with **tc-multiselect** component.
- Clearer primary/secondary buttons (Add pack vs Write test cases).

**Test suites — schedule (`suites-schedule.html`):**

- **Pick a suite:** show human names only (Smoke / Regression / Nightly); hide UUID pipe string behind “Details” or remove.
- **Automatic runs:** replace raw cron with **presets** (Daily 2am, Weekly, Custom) + optional advanced cron field + link to guide.
- **Login to use:** rename **Credential profile for scheduled runs** + link to Execute credentials.
- **Notify when fails:** single checkbox “Use my alert preferences from Account” (read `account` alert settings); remove duplicate dropdowns.
- **Keep watching after good run:** rename + FAQ tooltip (explain monitoring pack after green run).
- **One** sticky **Save schedule settings** for all three sections.

**Execute:**

- Remove or reword `Tunnel: off (set delivery.tunnel.enabled=true)` → user-facing: “Tunnel inactive” + link to Guide § tunnel (hide JVM property name).

**Run memory:**

- Fix table header/layout bug (`Approved & proposed` / `Id Status Summary` — CSS `table`/`thead` in `run-memory.html`).
- Add sidebar `?` tooltip: “Saved locators Keel reuses on later runs.”

**Bug Hunter:**

- Move under Execute & automate in nav.
- Remove recent runs + clarify or remove “Review findings” (or rename “Review last hunt report”).

**Structured plans:**

- Rename options + page title after user choice (Phase 0 question).
- Per-option `?` with one-line help.

---

## Suggested implementation phases

| Phase | Scope | Est. touch | Exit criteria |
|-------|--------|------------|----------------|
| **0** | Decisions (this doc + user options) | — | Signed IA + naming |
| **1** | Sidebar shell + IA rename/move + **nav `?` help** | `fragments.html`, CSS, IA JS, glossary keys | Matches Figma feel; every nav item has one-line help |
| **2** | Dashboard healthcare layout | `dashboard.html`, CSS | No regression on metrics |
| **3** | Project picker + TC multiselect shared | JS module + 4 pages | Compare + schedule + GFK fixed |
| **4** | English file inputs + header cleanup | templates + CSS | No AR chrome on EN session |
| **5** | Dedupe recent job tables | execute, bug-hunter, … | History is canonical |
| **6** | Generate page layout | `generate.html` | Clear visual sections |
| **7** | Suites schedule UX + single save | `suites-schedule.html`, JS | No raw UUID in primary UI |
| **8** | Execute / Automate copy + tunnel | `execute.html`, `upload.html` | No dev property strings |
| **9** | Run memory + Bug Hunter polish | templates + JS | Layout bugs fixed |
| **10** | Guide + tooltips sync | `guide.html` | Names match UI |
| **11** | QA pass + accessibility | — | Keyboard nav, contrast |

**After each phase:** run portal locally, screenshot diff, update “Revision log” below.

---

## Revision log

| Date | Phase | Notes |
|------|-------|-------|
| 2026-11-16 | 0 | Initial plan from user feedback + codebase inventory |
| 2026-11-16 | 1 | **Shipped:** Figma-style sidebar IA (TCs Generation / Execute & automate / History groups), nav renames, `?` feature help on all main nav items, friendly redirects (`/playback`, `/history`, `/test-blueprints`), nav-group persistence in `localStorage`. **Review:** `localhost:8080` — hover row, click `?`, toggle groups. **Next:** Phase 2 dashboard. |
| 2026-11-16 | 2 | **Shipped:** Overview dashboard refresh (`keel-dashboard.css`, area/line trends chart, KPI card polish, attention cards, 5 recent rows → History). **Next:** Phase 3 shared project picker + TC multiselect. |
| 2026-11-16 | 3 | **Shipped:** `keel-project-picker.js`, `keel-tc-multiselect.js`; compare + schedule project bugs; GFK multiselect. **Next:** Phase 4 English file inputs. |
| 2026-11-16 | 4 | **Shipped:** `keel-file-input.js`, Account top bar label, English file UX. **Next:** Phase 5 dedupe recent tables. |
| 2026-11-16 | 5 | **Shipped:** Execute + Bug Hunter recent tables removed; History CTAs; live execute run panel kept. **Review:** `localhost:8080` execute start + bug-hunter triage. **Next:** Phase 6 Generate layout. |
| 2026-11-16 | 6 | **Shipped:** Generate stacked cards + section `?` help; bulk jobs under bulk card only. **Next:** Phase 7 suites schedule. |
| 2026-11-16 | 7 | **Shipped:** `suites-schedule` presets, Account alerts, unified save, suite picker labels. **Next:** Phase 8 Execute tunnel copy. |

---

## Open technical notes (not blockers)

- URLs can stay stable (`/generate-from-knowledge`, `/automate`) with new labels; optional redirects later.
- `feature.*` permissions in sidebar `data-feature-perm` must stay aligned after moves.
- Figma MCP was not authenticated in agent session; implement from community file inspection in browser + your screenshots.

---

## Phase 0 — decisions (locked 2026-11-16)

| Decision | Choice |
|----------|--------|
| **First implementation phase** | **Phase 1** — Sidebar + nav renames (Figma-style) |
| **Structured plans** | Rename page to **Test blueprints** (+ `?` tooltips on modes) |
| **Run memory** | Under **History / advanced** (with Runs → History) |
| **URLs** | **Friendly aliases + redirects** (e.g. `/playback` → `/record`, keep old paths working) |

## Phase 1 — implementation checklist (next)

1. Restructure `fragments.html` nav groups per IA table (including Bug Hunter under Execute & automate).
2. Elevate sidebar CSS: section labels, hover, active rail, child indent (Figma reference).
3. Register Spring redirect aliases for renamed routes (document in `guide.html`).
4. Rename visible strings only; update `navActive` values in controllers where needed.
5. Smoke: every sidebar link 200 OK on local portal.
6. Wire **`featureHelp`** on each nav label (popover + `messages.properties`); stubs OK for pages fixed in later phases.