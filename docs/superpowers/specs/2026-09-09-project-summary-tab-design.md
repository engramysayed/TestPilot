# Project Summary tab — design

**Date:** 2026-09-09  
**Status:** Approved for implementation

## Goal

Opening a project lands on a **Summary** tab: a mini strip of project facts plus an activity feed of recent jobs. Settings / Automation / Test cases stay as-is.

## Tabs & default

| Order | Tab | Role |
|-------|-----|------|
| 1 | Summary | Default landing (`/projects/{id}` or `?tab=summary`) |
| 2 | Automation | Packages, pages/tests, last Automate prove results |
| 3 | Test cases | Editable generated library |
| 4 | Settings | Name, URL, hooks, credentials, danger zone |

Deep links (`?tab=automation` etc.) unchanged.

## Mini strip

Facts for this project only (no charts):

- Library case count (from generated workbook; `0` if none)
- Latest package label (`vN` or `none`)
- Last Automate prove snapshot: counts by status from project `ir/` drafts (passed / partial+todo / reused) + last-run label when available
- `jobRunning`: true if any QUEUED/RUNNING job for this project

## Activity feed

- Last **10** jobs owned by the user for this `projectId` (newest first)
- Kinds: CONVERT (Automate), EXECUTE, GENERATE_BATCH, GENERATE_COMPARE
- Columns: createdAt, kind (display label), status, message (truncated), passedCount, todoCount
- Empty state: short copy + links to Automate / Execute / Generate

## Click behavior (kind-aware)

| Kind | Click |
|------|--------|
| CONVERT / GENERATE_* | Navigate to `/status?jobId=…` |
| EXECUTE | Expand inline on Summary: case list with status; link to Execute page for full detail |

## API

`GET /api/projects/{projectId}/summary` → `{ strip, recentJobs }`

## Out of scope (v1)

- Editing from Summary
- Automate step accordion on Summary (stays under Automation)
- Charts / pass-rate widgets
- Persisting which Summary row was expanded
