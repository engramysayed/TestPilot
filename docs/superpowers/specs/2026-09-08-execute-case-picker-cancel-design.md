# Execute page UX polish — case picker, delete modal, cancel

Date: 2026-09-08  
Status: Approved (chat: go)

## Goals

1. Replace the tall library checkbox list with a compact multi-select (chips + dropdown checklist + search).
2. Replace `window.confirm` delete with an in-page modal matching Projects.
3. Surface **Cancel** on QUEUED/RUNNING recent-run rows; keep Cancel in the open detail panel; clarify cooperative cancel.

## Non-goals

- Checking cancel mid-Selenium step (still between TCs).
- New JS/CSS frameworks.

## Behavior

- Empty selection = run all library cases (unchanged).
- Delete modal: Cancel / Delete permanently; refuse RUNNING still shows API error in modal or err banner.
- Row Cancel posts `/api/jobs/{id}/cancel` and shows “Cancel requested — stops after current TC…”.
