# Job activity loader

**Date:** 2026-08-31  
**Status:** Draft — pending user review  
**Depends on:** existing Job status page, `GET /api/jobs`, Generate spinner CSS  

---

## 1. Goal

While a job is `QUEUED` or `RUNNING`, Keel must look busy on:

1. **Job status** (`/status`) — Generate batch, Execute, Convert, Compare (one shared page)
2. **Jobs** and **Dashboard** tables — running/queued rows
3. **Sidebar** on every authenticated app page — a chip linking to live status

Today Job status fills the bar at `1 / 1` with no motion, so a long generate looks stuck.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| Approach | Reuse existing Generate `.spinner` + `.progress-fill--indeterminate` |
| New API | **None.** Sidebar polls existing `GET /api/jobs` |
| Active statuses | `QUEUED` and `RUNNING` only |
| Job status bar | Indeterminate (moving) while active, even if progress is `1 / 1`. Determinate 100% only after a terminal status |
| Elapsed | Job status shows `Working for m:ss` while active; hide when done |
| Lists | Small spinner inside the status badge for `QUEUED` / `RUNNING` |
| Sidebar chip | Newest active job always links to `/status?jobId=…`. If more than one, label is `N jobs running` (still opens the newest job — `/jobs` hides Execute runs) |
| Chip hidden | No active jobs, poll error, or unauthenticated / no sidebar |
| Poll | Every **3s**; skip ticks while `document.hidden` |
| Generate page | Keep current queue spinner as-is (already exists) |
| Overlay / toasts | **Out** |

---

## 3. Scope

### In

- `status.html` live-progress header: spinner, `aria-busy`, elapsed line
- Toggle indeterminate class on the existing progress track while the polled status is active
- `jobs.html` and dashboard recent-jobs badges
- Sidebar fragment: chip + poll script in `fragments.html`
- CSS for chip and compact badge spinner
- MVC tests that the markup exists (status spinner/elapsed ids, sidebar chip id, jobs badge spinner class)

### Out

- New `/api/jobs/active`
- Full-screen overlay, sound, browser notifications
- Changing Execute’s own run panel (Execute jobs already use `/status`)
- Login / invite pages (no sidebar)

---

## 4. UX copy

| Place | Copy |
|-------|------|
| Job status elapsed | `Working for 0:42` (`m:ss`, minutes unbounded) |
| Chip, one generate batch | `Generating…` |
| Chip, one execute | `Executing…` |
| Chip, one compare | `Comparing…` |
| Chip, one convert | `Converting…` |
| Chip, many | `N jobs running` |
| Chip `aria-label` | Visible label plus ` Open job status` |

Kind mapping uses `jobKind`: `GENERATE_BATCH` → Generating, `EXECUTE` → Executing, `GENERATE_COMPARE` → Comparing, else Converting.

---

## 5. Behavior

### Job status

- On first paint, if server-rendered status is `QUEUED`/`RUNNING`, show spinner + indeterminate bar + elapsed starting at `0:00`.
- Each poll: if still active, keep busy UI; if terminal (`COMPLETED`, `COMPLETED_WITH_BLOCK`, `FAILED`, `CANCELLED`), stop spinner, stop elapsed, set bar to 100% for completed / leave as last percent for fail/cancel.
- `aria-busy="true"` on the live-progress section while active.

### Jobs / Dashboard

- Server-rendered: badge for `QUEUED`/`RUNNING` includes a `.spinner.spinner--badge` (decorative, `aria-hidden`).
- No extra polling on those tables in v1 (sidebar poll covers “something is running”).

### Sidebar chip

- Element `#sidebar-job-chip` starts `hidden`.
- Poll `GET /api/jobs` with `Accept: application/json` and existing `X-Keel-Requested-With: Keel` + credentials.
- Filter `status` in `{QUEUED, RUNNING}`; sort by `createdAt` descending (ISO string; missing dates last).
- **One job:** show chip, `href="/status?jobId={id}"`, label from kind table.
- **Several:** same href to the **newest** job, label `N jobs running`.
- **Zero / HTTP error / network error:** hide chip; do not write into the Job status fail banner.
- Click is a normal link (no JS navigation required).

---

## 6. Architecture

| Unit | Responsibility |
|------|----------------|
| `portal.css` | Reuse spinner; add `.spinner--badge`, `.sidebar-job-chip`, elapsed muted line |
| `status.html` | Markup + extend existing poll `render()` to toggle busy UI |
| `jobs.html` | Badge spinner in status cell |
| `dashboard.html` | Same for recent Automate jobs |
| `fragments.html` sidebar | Chip above `.sidebar-foot`; one IIFE poller (same pattern as automate-nav script) |

No Java service changes. No new endpoints.

---

## 7. Error handling

- Status page: existing poll error path unchanged.
- Sidebar: any non-OK or throw → hide chip until a later successful poll shows active jobs.
- Missing `jobId` on status: existing “Missing jobId” message; no spinner.

---

## 8. Testing

- `StatusPageMvcTest`: GET `/status` for a `RUNNING` generate-batch job contains `id="job-busy-spinner"` and `id="job-elapsed"` (not `hidden` when status is RUNNING).
- `AutomateShellMvcTest`: dashboard or automate HTML contains `id="sidebar-job-chip"`.
- `JobsPageMvcTest`: seed a `RUNNING` conversion job, GET `/jobs`, assert that row’s status cell includes `spinner--badge`.

Browser check after implement: open a generate job on `/status` and confirm spinner + moving bar + elapsed; confirm chip on Dashboard.

---

## 9. Non-goals / YAGNI

- Do not list every active job in the sidebar.
- Do not persist elapsed across refresh beyond restarting the timer (elapsed is session-local from page load).
- Do not add WebSockets.
