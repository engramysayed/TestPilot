# Record straight into the framework

> **For agentic workers:** The browser prove phase is skipped. EmitPhase (the existing framework builder) writes the ZIP from locators captured while recording.

**Status:** Built 2026-10-15. Browser check confirmed the Record page, the in-Chrome bar, and a save. A framework job needs Automate credits (25). An account with 0 credits keeps the test case and does not start the job.

**Goal:** A recording keeps a locator with each step, Check this adds a real assertion, and Build writes the project framework without opening Chrome again.

**Architecture:** The page hook stores a stable locator beside each action. Save still writes the English test case. When Build is on, the portal starts a normal Automate job whose worker calls `ConversionJobRunner.runRecorded`, which writes IR drafts and runs `EmitPhase` only. ProvePhase never starts.

**Tech stack:** Java 21, Spring Boot portal, existing CodeWriter / EmitPhase, the in-page record hook.

## Global constraints

- One Java page class per page. Page names come from the URL via `PageClusterer`.
- Passwords are never stored. A password step is a secret locator only.
- Product credits stay on. Build uses the same CONVERT job, so the existing Automate charge applies.
- Do not open a second browser. Do not call ProvePhase.
- A generated id is not a locator. That step stays English and is left out of the framework.
- An existing framework is updated and its other drafts are kept, so this build does not wipe earlier tests.

## Tasks

1. Hook stores `{s,v}` locators and a Check this bar (text or this page).
2. `RecordingLog.Capture` keeps kind, label, value, secret, strategy, locator, page URL across navigations.
3. `RecordedDrafts` maps captures to `ProvenStep` / `TcDraft` (click, type, select, textContains, urlContains).
4. `ConversionJobRunner.runRecorded` copies the template, writes those drafts, calls `EmitPhase`.
5. Portal save option starts that job. The Record page shows how many controls were remembered.
6. Tests for the draft mapping and the hook. Browser check of the Record page.
