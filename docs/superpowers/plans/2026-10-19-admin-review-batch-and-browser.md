# Admin review batch size + browser type Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Owner sets review TCs per batch and Chrome / Chrome headless / Edge on Admin LLM settings; suites of 6+ split into those batches; next WebDriver uses the saved browser when env is unset.

**Architecture:** `ReviewBatchPolicy` is the cap/chunk math. `LlmSettingsService` stores `reviewCasesPerBatch` and `browser`. `AuthoringReviewService.reviewCases` refuses oversized POSTs. Execute/Automate JS chunks `tcIds`. Generate `review()` loops chunks server-side. `WebDriverFactory` and `AppConfigProvider` read live System properties after Admin Save.

**Tech Stack:** Java 21, Spring MVC, TestNG, Thymeleaf, existing `llm-settings.json`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-19-admin-review-batch-and-browser-design.md`
- TDD. `cmd.exe`. Author `rsayed <rsayed@axisapp.com>`. No Co-authored-by. Do not push.
- Do not change leftover quoting, OutcomeKind, or nested-hook uniqueness.
- Env wins for `BROWSER_TYPE` / `BROWSER_HEADLESS`. Default N=50, browser edge headed.
- N clamp 1–50. Split only when selected > 5.

## File map

| File | Role |
|------|------|
| `delivery.portal.service.ReviewBatchPolicy` | Cap and chunk math |
| `LlmSettingsService` | Persist N + browser; publish BROWSER_* if env blank |
| `AuthoringReviewService` | Refuse POST larger than cap; Generate loops chunks |
| `AdminLlmSettingsController` + `admin-llm-settings.html` | Owner fields |
| `execute.html` / `upload.html` | Client chunk `tcIds`; status Reviewing batch i of k |
| `WebDriverFactory` / `AppConfigProvider` | Live browser at driver create |
| PRODUCT, Guide, CHANGELOG, handoff | Finish-job |

---

### Task 1: ReviewBatchPolicy

**Files:**
- Create: `src/main/java/delivery/portal/service/ReviewBatchPolicy.java`
- Test: `src/test/java/delivery/portal/service/ReviewBatchPolicyTest.java`

- [ ] Failing tests: 5+N=7 → cap 5; 20+N=7 → chunks 7,7,6; posted 20+N=7 exceeds; clamp 0→50, 99→50
- [ ] Implement
- [ ] Tests pass

### Task 2: Persist settings + refuse oversized review POST

**Files:** `LlmSettingsService`, `AuthoringReviewService`, tests

- [ ] Default N=50, browser `edge`. Save N=7. POST 20 refused. POST 5 allowed. POST 7 allowed when N=7.
- [ ] Generate `review()` of 8 cases with N=7 → two LLM calls, combined cases.

### Task 3: Admin UI + Execute/Automate chunking + live browser

**Files:** admin-llm-settings.html, execute.html, upload.html, PortalUiController, WebDriverFactory, AppConfigProvider, ChromeFactory already uses BrowserExecution

- [ ] Owner fields. Execute `data-review-batch-size`. Chunk tcIds when selected > 5. Fail closed on batch error.
- [ ] Save chromeheadless with no env → next factory Chrome headless. Static `WebDriverFactory.browser` removed.

### Task 4: Docs

PRODUCT, Guide, CHANGELOG, ENGINEERING_HANDOFF, handoff, RESUME, spec status Implemented.
