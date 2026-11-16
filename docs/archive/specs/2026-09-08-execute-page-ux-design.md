# Execute page: recent-run expand, hard delete, quieter start form

Date: 2026-09-08  
Status: Approved in chat

## Goals

1. Hard-delete execute runs from Recent list (DB + on-disk store).
2. Remove Score column (`1P / 0T`).
3. Expand a recent run in place (status + results together) — option A.
4. Simplify Start execute form; place it directly under the project dropdown.

## Page order

1. Project select  
2. Start an execute run (simplified)  
3. Recent execute runs  

## Recent runs

- Columns: Job | Status | Progress | actions (Open / Delete). No Score.
- Expanding a row (Open or row click) loads status + TC results into a detail panel under that row.
- Delete → confirm → `DELETE /api/execute-runs/{jobId}` removes job (owner-only) and `execute-runs/<jobId>` (and related job artifacts for that id).

## Start form

- One panel; optional design mockups in collapsed `<details>`.
- Credential + workbook source + TC checklist + optional Cursor review + Start button.
- Less nested blue chrome.

## Non-goals

Automate jobs list changes; retention sweeper behavior.
