# Bug Hunter navigation + JS actions

**Date:** 2026-09-10  
**Status:** Approved (chat) / implemented

## Problem

The planner saw the current URL via the page map but could not use browser history (back/forward), reload, or short in-page JavaScript probes. Abuse/session strategies that say “Back after success” had no matching action.

## Decision

Extend the hunt action allowlist:

| Action | Effect | Notes |
|--------|--------|--------|
| `back` | `driver.navigate().back()` | aliases: `navigate_back`, `go_back` |
| `forward` | `driver.navigate().forward()` | aliases: `navigate_forward`, `go_forward` |
| `refresh` | `driver.navigate().refresh()` | aliases: `reload` |
| `execute_js` | `JavascriptExecutor.executeScript(script)` | fields: `script` \| `code` \| `js`; aliases: `js`, `javascript` |

Constraints on `execute_js`:

- Max script length: 4000 chars (reject oversize)
- Result truncated to 500 chars in the action log / journal
- Prefer UI locators for normal probes; JS only for short page-state probes
- Runs only in the live hunt browser (user’s configured base URL / project)

Observation (unchanged): each cycle still puts `driver.getCurrentUrl()` in the page map `## URL` section — no separate getUrl action.

Oracle: treat `back` / `forward` / `refresh` / `execute_js` like navigate/click for blank-main style post-action checks.

Prompts updated in `OllamaHuntPlanner` and `tools/cursor-heal/heal.mjs`.
