# 2026-10-19 — Admin review batch size + browser type

**Status:** Spec approved for planning. Not implemented.

Spec: `docs/superpowers/specs/2026-10-19-admin-review-batch-and-browser-design.md`

## What was decided

- Owner-only **Admin → LLM & API keys**: **Review cases per batch** (`N`, 1–50, default 50) and **Browser** (`chrome` / `chromeheadless` / `edge`).
- Selected **≤ 5** TCs: one review request. Selected **> 5**: chunks of `N` (example 20 with `N=7` → 7, 7, 6). Combined preview after the last batch. Accept after all batches; fail closed if one batch fails.
- Server refuses a single POST larger than that rule. Env still wins for `BROWSER_TYPE` / `BROWSER_HEADLESS`.

## How to use (once built)

Set `N` and browser on LLM & API keys. Rebuild jar, `start-portal.cmd`, Ctrl+F5. Next Execute/Automate review follows the batch rule; next job that opens a browser follows the browser choice.

## Known defects (today, before the work)

- Review is still one request, hard cap 50.
- Browser still comes from `webapp.properties` / JVM (default Edge headed).
