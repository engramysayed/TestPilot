# 2026-10-19 — Admin review batch size + browser type

**Status:** Implemented on main. Rebuild the portal jar (`rebuild-portal.cmd`), `start-portal.cmd`, then Ctrl+F5.

Spec: `docs/superpowers/specs/2026-10-19-admin-review-batch-and-browser-design.md`  
Plan: `docs/superpowers/plans/2026-10-19-admin-review-batch-and-browser.md`

## What shipped

- Owner **Admin → LLM & API keys**: **Review cases per batch** (`N`, 1–50, default 50) and **Browser** (`chrome` / `chromeheadless` / `edge`).
- Selected **≤ 5** TCs: one review request. Selected **> 5**: chunks of `N` (20 with `N=7` → 7, 7, 6). Combined preview after the last batch. Accept after all batches; fail closed if one batch fails.
- A single POST larger than that rule is refused. Generate splits the workbook server-side with the same math.
- Env still wins for `BROWSER_TYPE` / `BROWSER_HEADLESS`. `WebDriverFactory` reads type at driver create (not a class-load static).

## How to use

1. Owner opens Admin → LLM & API keys, sets batch size and browser, Save.
2. `rebuild-portal.cmd`, `start-portal.cmd`, Ctrl+F5.
3. Execute/Automate: select more than 5 cases with Review on — status shows Reviewing batch i of k.
4. Next job that opens a browser uses Chrome / Chrome headless / Edge as saved, unless env already set those variables.

## Known defects

- Live jar not updated until rebuild + restart + Ctrl+F5.
- Excel upload without library `tcIds` is still one POST of the whole file; if that file has more cases than `N` (and more than 5), the server refuses. Use library selection to chunk.
- Generate review of a large workbook makes sequential LLM calls (can be slow).
- Admin UI was not verified on a rebuilt `:8080` in this pass.
