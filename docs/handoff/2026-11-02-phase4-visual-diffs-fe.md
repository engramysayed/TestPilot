# Phase 4B — Visual diffs FE (approve baseline UX) — 2026-11-02

**Status:** GREEN (Execute panel wired; stub-tolerant if API 404; portal restart owned by Maestro — already live on tip at apply time)  
**Bot:** Frontend (live-apply on Ramy)  
**Tip SHA before apply:** `8c486cc785f4ba247ff0a9a3075dface5f536b73`  
**Tip SHA after apply (working tree dirty OK):** `8c486cc785f4ba247ff0a9a3075dface5f536b73`  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\backup-pre-phase4b-visual-20261102-180127`  
**Time (Africa/Cairo):** 2026-11-02 ~18:01+

**Portal:** Do NOT restart from FE. Controllers on disk at tip 8c486cc; stub-tolerant until Maestro rebuilds jar for that tip. Ctrl+F5 after jar.


## IA / placement

- **Primary:** additive **Visual diffs** panel on **Execute** (`execute.html`), id `#visual-diffs-panel`, after Recent execute runs / before Run progress.
- Shown when a project is selected (same hook as recent runs: `onProjectChange` → `loadVisualDiffs`).
- **Evidence:** short **Visual diffs** ghost button → `/execute?projectId=…#visual-diffs-panel` (no duplicate full UI).
- Classic Generate / GFK / Plain cases / Automate **not** edited.

## UX

1. Pending list from `GET /api/projects/{projectId}/visual-diffs?pending=true`
2. Row click → side-by-side **Baseline | Actual | Diff** (`<img src>` same-origin)
3. **Approve baseline** → `POST …/visual-baselines/{caseKey}/{viewport}/approve` body `{"runId":"…"}` → refresh list
4. Optional **Replace upload** → multipart `POST …/visual-baselines/{caseKey}/{viewport}` field `file`
5. `changedFraction` shown muted; empty / waiting / error states

## Stub behavior

- HTTP **404/501** or network error on list → muted **“Waiting on Backend visual-diffs.”** (panel still visible once project selected)
- Empty array → **“No pending visual diffs.”**
- Diff image 404 → hide img, show **“No diff overlay.”**
- Does not break workbook / excel / recent-run flows (additive panel + one `onProjectChange` call)

## Endpoints wired

| Action | Method + path |
|--------|----------------|
| Pending list | `GET /api/projects/{id}/visual-diffs?pending=true` |
| Baseline image | `GET …/visual-baselines/{caseKey}/{viewport}/image` |
| Actual | `GET …/visual-diffs/{runId}/{caseKey}/{viewport}/actual` |
| Diff | `GET …/visual-diffs/{runId}/{caseKey}/{viewport}/diff` |
| Approve | `POST …/visual-baselines/{caseKey}/{viewport}/approve` JSON `runId` |
| Replace (optional) | `POST …/visual-baselines/{caseKey}/{viewport}` multipart `file` |

## Files touched

1. `src/main/resources/templates/execute.html` — panel + scoped CSS + JS
2. `src/main/resources/templates/evidence.html` — short link only
3. **NEW** `docs/handoff/2026-11-02-phase4-visual-diffs-fe.md` (this file)
4. `docs/handoff/per-bot-status.md` — one FE line
5. `docs/handoff/2026-11-02-phase4-plain-english-fe.md` — Next B → done / pointer

**Not touched:** `generate.html`, `generate-from-knowledge.html`, `plain-cases.html`, Automate/`upload.html`, sidebar IA, portal Java, no push, **no portal restart** (Maestro owns :8080).

## Verify

- Grep approve path present in `execute.html`
- generate / GFK / plain-cases / automate untouched
- After Backend jar already on portal: **Ctrl+F5** on `/execute`, select project → Visual diffs panel
- No portal restart from this bot

## READY path

1. Open `/execute`, pin/select a project.
2. Panel **Visual diffs** loads pending rows (or waiting/empty stub copy).
3. Click a row → Baseline | Actual | Diff; **Approve baseline** promotes actual → baseline.
4. From Evidence, use **Visual diffs** to jump back to Execute panel.


## Sidebar Visual diffs deep-link — 2026-11-02 ~18:35 Africa/Cairo

**Status:** GREEN (sidebar link + hash scroll; no new route; no portal restart; no push)  
**Bot:** Frontend (executor)  
**Tip SHA (dirty tree OK):** `83b9077`  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\backup-pre-gfk-ingest-vd-sidebar-20261102-183236` (shared with GFK ingest strip apply)

### IA
- Features nav **after Execute**: **Visual diffs** → `href="/execute#visual-diffs-panel"`
- `data-feature-perm="feature.execute"`
- `th:classappend` when `navActive == 'execute'` (same as Execute; no new PortalUiController route)
- `keel-sidebar-ia.js`: no map change (pathname remains `/execute`)

### Execute hash behavior
- Added `focusVisualDiffsPanelFromHash()` — scrolls/focuses `#visual-diffs-panel` when hash matches and panel is visible
- Called from `loadVisualDiffs` `finally`, `hashchange`, and after project change listener bind
- Complements Evidence ghost link already pointing at the same hash

### Files this slice
1. `src/main/resources/templates/fragments.html` — sidebar link only
2. `src/main/resources/templates/execute.html` — hash scroll/focus helper
3. `docs/handoff/2026-11-02-phase4-visual-diffs-fe.md` — this section
4. `docs/handoff/per-bot-status.md` — one FE line

**Not touched:** `generate.html`, GFK body beyond sibling ingest task, plain-cases, automate, portal Java. No push. No portal restart (Maestro owns).
