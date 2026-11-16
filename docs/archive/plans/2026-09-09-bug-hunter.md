# Bug Hunter (HUNT) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ship Bug Hunter v1 — top-nav page, `HUNT` jobs, brief → capped observe/plan loop (dry-run complete; live planner hooks), hunter pack ZIP download.

**Architecture:** New `JobKind.HUNT` + `HuntWorker` writing under `hunt-runs/{jobId}/`. Request JSON on disk (via `excelPath`). Dry-run path builds brief + simulated cycles + pack without browser. Live path stubs planner interface for Ollama/Cursor and captures evidence when not dry-run.

**Tech Stack:** Java 21, Spring Boot, TestNG, Thymeleaf, ZipOutputStream

**Spec:** `docs/superpowers/specs/2026-09-09-bug-hunter-design.md`

## Global Constraints

- No Claude models
- Scenario invent hard-capped; cycle ceiling always enforced; AI may `finish` early
- No auto-merge into library workbook
- Network capture best-effort

---

### Task 1: Job kind + downloadability

**Files:**
- Modify: `JobRecord.java` — add `HUNT`; `isDownloadable` true for COMPLETED/COMPLETED_WITH_BLOCK/FAILED
- Modify: `ProjectSummaryService.kindLabel` — `"Bug Hunter"`
- Modify: `JobController` download — allow HUNT as well as CONVERT
- Modify: `jobs.html` / `PortalUiController` status labels for HUNT
- Modify: `JobsSchemaPatch` if column length needs bump (16 is enough for HUNT)

### Task 2: Hunt domain core (brief, planner JSON, pack)

**Files:**
- Create: `delivery/hunt/HuntRequest.java`
- Create: `delivery/hunt/HuntBriefBuilder.java`
- Create: `delivery/hunt/HuntPlannerDecision.java` + parser
- Create: `delivery/hunt/HuntPackWriter.java`
- Create: `delivery/hunt/DryRunHuntService.java`
- Test: `HuntBriefBuilderTest`, `HuntPackWriterTest`, `HuntPlannerDecisionTest`

### Task 3: API + worker

**Files:**
- Create: `HuntController`, `HuntWorker`
- Modify: `AsyncConfig` — `huntExecutor`
- Wire start: validate project, require ≥1 tcId, write request JSON, save JobRecord HUNT, submit worker

### Task 4: Portal UI

**Files:**
- Create: `templates/bug-hunter.html`
- Modify: `fragments.html` nav, `PortalUiController` GET `/bug-hunter`
- Status page: show Bug Hunter kind

### Task 5: Integration test

**Files:**
- Create: `HuntApiTest` — start hunt in dry-run, poll until COMPLETED, download ZIP contains brief.md + SUMMARY.md
