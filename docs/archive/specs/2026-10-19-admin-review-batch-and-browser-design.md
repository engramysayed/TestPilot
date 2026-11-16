# Admin review batch size + browser type

**Date:** 2026-10-19  
**Status:** Implemented (rebuild jar + Ctrl+F5 to pick up)  
**Plan:** `docs/superpowers/plans/2026-10-19-admin-review-batch-and-browser.md`

## Problem

Review before Execute/Automate sends the whole selected suite in **one** LLM request and refuses more than 50 cases. There is no Owner control for how many TCs go in each review call, and no remaining/batch counter. Operators who select 20 cases cannot get 7+7+6.

The live browser for Execute, Automate, Record, and Bug Hunter is `BROWSER_TYPE` + `BROWSER_HEADLESS` from `webapp.properties` / JVM properties (Owner config default is EDGE, headed). There is no Admin control for Chrome, Chrome headless, or Edge.

## Goal

1. **Review batches.** Owner sets how many test cases each review request may carry. When the user selects **more than 5** cases, Keel splits them into chunks of that size (last chunk may be smaller). Combined proposed cases and findings appear **after the last batch**. Accept still waits until every batch finished, then saves the library and starts the job.
2. **Browser.** Owner picks **Chrome**, **Chrome headless**, or **Edge** on the same Admin settings page. The next job that opens a browser uses that choice.

## Non-goals

- Making batch size or browser a project or member setting.
- Changing OutcomeKind quote-wins-first, leftover Expected filler rules, or nested-hook uniqueness.
- Firefox, remote Selenium grid, or per-job browser override on Execute.
- Editing `api-keys.local.bat`.
- Review learning from live screenshots.
- Changing credit prices (review stays uncharged).

## Review batches

### Owner setting

On **Admin → LLM & API keys** (Owner only, same gate as today’s LLM keys):

- **Review cases per batch** — integer `N`, inclusive range **1–50**. Default **50** (today’s one-request behaviour for suites up to 50).
- Stored in `delivery-store/llm-settings.json` next to the existing LLM fields. Not a secret.

### When splitting happens

| Selected TCs | What happens |
|--------------|----------------|
| 1–5 | **One** review request with all of them, even if that count is greater than `N`. |
| 6 or more | Split into chunks of size `N`. Remainder is the last chunk. Example: 20 selected, `N=7` → **7, 7, 6**. |

`MAX_CASES` on a single HTTP review call becomes: **5** when the posted suite is ≤5; **`N`** when the caller is sending a batch of a larger suite. The server **refuses** a body larger than that. The browser must chunk; a raw POST of 20 when `N=7` is rejected.

Generate’s authoring-review uses the same service and the same rule.

### UI (Execute and Automate)

- While batches run: status such as **Reviewing batch 2 of 3**.
- After the last successful batch: **one** proposed-case list and **one** findings list (concatenated, original selection order).
- Any batch failure: fail closed. Do not enable Accept. Show which batch failed. Do not save the library. Do not start the job.
- **Accept & run** still saves the **full** combined suite once, then starts the reviewed `TC_ID`s.

### Data flow

```text
selected TCs
    │
    ├─ count ≤ 5  → one POST /pre-run-authoring-review
    │
    └─ count > 5  → POST chunk 1..k of size N (last remainder)
                      each response quoted leftover Expected as today
                      UI holds results until k done
                      then one combined preview → Accept
```

## Browser type

### Owner setting

Same Admin page, same Owner gate:

- **Browser** — one of `chrome`, `chromeheadless`, `edge` (labels: Chrome, Chrome headless, Edge).
- Default: keep today’s runtime if already set via env; else **Edge headed** (`BROWSER_TYPE=EDGE`, `BROWSER_HEADLESS=false`) to match `AppConfig` defaults.

| Admin value | `BROWSER_TYPE` | `BROWSER_HEADLESS` |
|-------------|----------------|--------------------|
| `chrome` | `CHROME` | `false` |
| `chromeheadless` | `CHROME` | `true` |
| `edge` | `EDGE` | `false` |

### Apply

- Persist in `llm-settings.json`. On Save and on portal start, publish `System` properties **only when those env vars are not already set** (same env-wins rule as API keys).
- **Next** `WebDriverFactory` construction (Execute, Automate, Record, Bug Hunter) reads the live properties. A job already in a browser keeps that session.
- `WebDriverFactory` must **not** cache `browserType` in a `static` field captured at class load — that would ignore Admin Save until a full JVM restart.

## Surfaces

| Surface | Change |
|---------|--------|
| Admin → LLM & API keys | Two fields: review batch size, browser |
| Execute / Automate review | Client chunks when selected > 5; combined preview |
| `AuthoringReviewService` | Cap per request from stored `N` (and the ≤5 exception) |
| `WebDriverFactory` / Chrome / Edge factories | Read type + headless at driver create |

## Tests (intent)

- Selected 5 + `N=7` → one request of 5.
- Selected 20 + `N=7` → three requests sized 7, 7, 6; preview lists 20 cases after the third.
- POST of 20 in one body when `N=7` → refused.
- Batch 2 fails → Accept disabled; library unchanged.
- Admin Save `chromeheadless` with no env → next factory is Chrome `--headless=new`.
- Env `BROWSER_TYPE` already set → Admin Save does not override.

## Known limits

- Nested preferred-hook uniqueness (duplicate Ant menu copies keep xpath) is **out of this spec**.
- Guide copy for batching and browser is updated in the implementation plan, not before.
