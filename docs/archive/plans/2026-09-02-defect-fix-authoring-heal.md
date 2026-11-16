# Defect Fix Pass — Authoring / Preview / Heal Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every blocking defect from the post–recovery-era review so leave-empty Execute, heal recovery, Automate proven trail, and TC preview editing cannot silently fail.

**Architecture:** In-place fixes in three waves (P0 Execute → P1 contract/Automate → P2 UX). No heal rewrite. Recovery always runs through `tryRecoveryHeal` then retries the current intent; auto-fill respects leave-empty; recovery steps join the proven trail.

**Tech Stack:** Java 17 / Spring Boot portal, TestNG, Selenium, `tools/cursor-heal/heal.mjs`, Thymeleaf `generate.html`.

**Spec:** `docs/superpowers/specs/2026-09-02-defect-fix-authoring-heal-design.md`  
**Parent:** `docs/superpowers/specs/2026-09-02-authoring-heal-recovery-design.md`

## Global Constraints

- Do not commit unless the user explicitly asks.
- Do not weaken invent DOM validation or raise recovery max above 3.
- Recovery actions remain allowlisted: `clear`, `click`, `type`, `select` only.
- Retry **current intent only** (no `resumeFromIntentIndex` jump).
- Prefer TDD: failing test → minimal fix → green.
- Match existing package style (`delivery.heal`, `delivery.authoring`, `delivery.job`, `delivery.excel`).

## File map

| Area | Files |
|------|--------|
| Recovery escalate / proven | `src/main/java/delivery/job/ProvePhase.java` |
| Auto-fill leave-empty | `src/main/java/delivery/authoring/RequiredControlFiller.java` |
| Leave-empty bind | `src/main/java/delivery/authoring/StepIntentBinder.java` |
| Recovery parse | `src/main/java/delivery/heal/RecoveryPlanParser.java` |
| Invent budget | `src/main/java/delivery/heal/HealCascade.java` |
| Clear harden | `src/main/java/delivery/job/TcExecutionService.java` |
| Vague assert / phrasing | `src/main/java/delivery/excel/GenerateAuthoringRules.java` |
| Gate on Excel load | Automate/Execute upload or workbook load service (locate at task time) |
| Prompts | `tools/cursor-heal/heal.mjs` |
| UI grid | `src/main/resources/templates/generate.html` (+ CSS if present) |
| Coverage notes persist | `GeneratedWorkbookService` / meta + Generate API |
| Tests | Matching `src/test/java/...` |

---

### Task 1: P0 — Recovery on Cursor escalate path (D1, D9)

**Files:**
- Modify: `src/main/java/delivery/job/ProvePhase.java` (branch `ollamaUsed && !cursorUsed` ~692–734)
- Test: `src/test/java/delivery/job/ProvePhaseRecoveryEscalateTest.java` (create) **or** extend existing HealCascade/Prove helper tests if ProvePhase is hard to unit-test — prefer extracting a package-visible helper if needed

**Interfaces:**
- Consumes: `tryRecoveryHeal(HealResult, ManualTestCase, TcExecutionService, Path, boolean)`
- Produces: escalate path returns `RECOVERY_RETRY` / `RECOVERY_FAILED` / continues with classic heal only when `NOT_RECOVERY`

- [ ] **Step 1: Write failing test** — when `HealResult.tierUsed()=="recovery"`, escalate path must not treat recovery steps as intent success; must invoke recovery then signal retry.

- [ ] **Step 2: Run test — expect FAIL**

Run: `mvn -q -Dtest=ProvePhaseRecoveryEscalateTest test`

- [ ] **Step 3: Implement** — before classic `cursorHeal.ok() && execute`, call `tryRecoveryHeal`; on `RECOVERY_RETRY` set flags + `continue`; on `RECOVERY_FAILED` set reason + `continue`; skip `refusesSubmitNavigation` for recovery tier.

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Commit only if user asks**

---

### Task 2: P0 — Auto-fill skips leave-empty fields (D2)

**Files:**
- Modify: `src/main/java/delivery/authoring/RequiredControlFiller.java`
- Test: `src/test/java/delivery/authoring/RequiredControlFillerLeaveEmptyTest.java` (create)

**Interfaces:**
- Consumes: `List<StepIntentBinder.IntentLine> typeIntents` already passed to `planFillsBeforeClick`
- Produces: no `type`/`select` steps targeting controls matched to leave/keep-empty intents

- [ ] **Step 1: Failing test** — HTML with empty `input[name=email]` + password; intents include “Leave the Email or phone field empty” (blank data) + password type; `planFillsBeforeClick(..., "Click Log in", intents)` must **not** type into email.

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement** — before adding a fill, if any typeIntent is leave/keep-empty and token-overlaps the control, `continue`. Optionally extend `shouldSkip` for email only when such an intent exists (prefer intent-driven skip over global email skip).

- [ ] **Step 4: Run — PASS** (keep existing `RequiredControlFillerTest` green)

---

### Task 3: P0 — Leave-empty clear on TYPE_USER / TYPE_PASS (D3)

**Files:**
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` (preferred bind ~664 and needle bind ~694)
- Test: `src/test/java/delivery/authoring/StepIntentBinderTest.java`

- [ ] **Step 1: Failing tests** — TYPE_USER / TYPE_PASS with “Leave the email field empty” / “Leave the password field empty” → action `clear`, empty value.

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement** — mirror TYPE_FIELD leave-empty branch before invent/type.

- [ ] **Step 4: Run — PASS**

---

### Task 4: P1 — All-or-nothing recovery parse (D4)

**Files:**
- Modify: `src/main/java/delivery/heal/RecoveryPlanParser.java`
- Test: `src/test/java/delivery/heal/RecoveryPlanParserTest.java`

- [ ] **Step 1: Failing test** — one valid + one invalid recovery step → `Optional.empty()` (not 1-step plan).

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement** — if any item fails `validateStep`, reject entire plan; still ignore non-objects only if you treat them as invalid (prefer reject).

- [ ] **Step 4: Run — PASS** (update tests that expected partial accept)

---

### Task 5: P1 — Proven recovery trail (D5)

**Files:**
- Modify: `src/main/java/delivery/job/ProvePhase.java` (`tryRecoveryHeal` + callers)
- Test: unit test or package-visible helper verifying recovered steps are retained on RETRY path

- [ ] **Step 1: Failing test** — after successful recovery execute, proven/partial accumulation includes recovery `ProvenStep`s with rationale `heal:recovery`.

- [ ] **Step 2: Implement** — e.g. `List<ProvenStep> recoveredActions` parallel to `recoveredNav`; on RETRY append; merge into success path / final proven the same way as navigate recovery.

- [ ] **Step 3: Confirm `heal-recovery.json` still written with notes + thought**

- [ ] **Step 4: Run focused tests — PASS**

---

### Task 6: P1 — Invent budget after successful parse (D6)

**Files:**
- Modify: `src/main/java/delivery/heal/HealCascade.java` (`claimInventBudget` call sites)
- Test: `src/test/java/delivery/heal/HealCascadeTest.java`

- [ ] **Step 1: Failing test** — invalid invent/recovery JSON does not increment invent attempts; second valid invent still allowed within max.

- [ ] **Step 2: Implement** — parse first; claim only on present `HealResult`.

- [ ] **Step 3: Run — PASS**

---

### Task 7: P1 — Hardened clear (D7)

**Files:**
- Modify: `src/main/java/delivery/job/TcExecutionService.java` (main clear + `retryActionViaContext`)
- Test: prefer a small package-visible `clearElement(WebElement)` helper tested with mock/fake if Selenium-heavy; otherwise document manual + guard with unit test on helper JS sequence builder

- [ ] **Step 1: Implement clear helper** — `el.clear()`; if `getAttribute("value")` still non-blank, sendKeys Ctrl/Command+A + BACK_SPACE (platform-aware via Keys).

- [ ] **Step 2: Wire both clear paths**

- [ ] **Step 3: Run existing execution/heal tests — PASS**

---

### Task 8: P1 — Broader vague-assert + leave phrasing (D8, D15 partial)

**Files:**
- Modify: `src/main/java/delivery/excel/GenerateAuthoringRules.java`
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` (`isLeaveOrKeepEmptyStep`)
- Test: `GenerateAuthoringRulesTest`, `StepIntentBinderTest`

- [ ] **Step 1: Failing gate tests** for “verify an error appears”, “show an error message”, “user is notified” without quotes.

- [ ] **Step 2: Allow quoted exact message** — assert with `"Exact text"` must pass.

- [ ] **Step 3: Leave phrasing** — “do not fill the email field”, “leave email blank”, “skip the password field” → leave-empty TestData rule + clear bind.

- [ ] **Step 4: Run — PASS**

---

### Task 9: P1 — Quality gate on Excel load for Execute/Automate (D10)

**Files:**
- Locate upload/load entry (e.g. Automate/Execute controllers or `ExcelTcReader` callers)
- Modify: call `GenerateQualityGate.validate` after read; surface errors to UI/API
- Test: service/API test with bad leave-empty workbook → 4xx / job fail with gate messages

- [ ] **Step 1: Find all Excel → run entry points**

- [ ] **Step 2: Failing test on one primary path**

- [ ] **Step 3: Implement fail-loud gate**

- [ ] **Step 4: Run — PASS**

---

### Task 10: P1 — Prompt/schema cleanup (D11)

**Files:**
- Modify: `tools/cursor-heal/heal.mjs`
- Grep invent prompts in Java for `resumeFromIntentIndex`

- [ ] **Step 1: Remove resumeFromIntentIndex from examples**; state “retry the same failed intent after recovery”.

- [ ] **Step 2: Keep recovery JSON contract documented**

- [ ] **Step 3: No Java behavior change required beyond docs alignment**

---

### Task 11: P2 — Step-grid TC editor (D12)

**Files:**
- Modify: `src/main/resources/templates/generate.html`
- CSS in same file or existing stylesheet

- [ ] **Step 1: On openTcEditor** — parse numbered steps + align TestData lines into editable table rows (Step #, Steps, TestData).

- [ ] **Step 2: On save** — reassemble numbered steps + multiline TestData; PUT existing case API.

- [ ] **Step 3: Keep other fields** (title, expected, preconditions, priority, tags, visual, keelPath).

- [ ] **Step 4: Manual checklist in notes** (portal hard-refresh)

---

### Task 12: P2 — Coverage notes edit + Escape (D13, D14)

**Files:**
- Modify: `generate.html`, `GeneratedWorkbookService` / meta API if notes not persisted yet

- [ ] **Step 1: Editable coverage notes control** bound to workbook meta

- [ ] **Step 2: Persist API** (extend describe/save if needed)

- [ ] **Step 3: Escape closes modal**; backdrop click already closes

- [ ] **Step 4: Manual verify**

---

### Task 13: P2 — Slim-HTML recovery presence fallback (D16)

**Files:**
- Modify: `RecoveryPlanParser` and/or caller to pass fuller HTML / candidate check
- Test: `RecoveryPlanParserTest`

- [ ] **Step 1: Failing test** — locator absent from slim but present in fullHtml → accept when fullHtml provided.

- [ ] **Step 2: Implement optional fullHtml / dual check**; log `HEAL_RECOVERY_SLIM_MISS`

- [ ] **Step 3: Run — PASS**

---

### Task 14: P2 — Document intentional limits (D17)

**Files:**
- Create/update: `docs/superpowers/plans/2026-09-02-defect-fix-authoring-heal-notes.md`

- [ ] **Step 1: Document** allowlist, no resume jump, no parallel invent fix, future “any obstacle” era out of scope.

- [ ] **Step 2: Manual smoke checklist** for P0–P2

---

### Task 15: Verification bundle

- [ ] **Step 1: Run focused Maven suite**

```text
mvn -q -Dtest=RecoveryPlanParserTest,HealCascadeTest,FreeInventHealerTest,RequiredControlFiller*Test,StepIntentBinderTest,GenerateAuthoringRulesTest,ProvePhase*Test test
```

- [ ] **Step 2: Fix any regressions**

- [ ] **Step 3: Update tasks.md checkboxes**

- [ ] **Step 4: Stop for user manual V2** (portal restart, Facebook empty-email, recovery evidence)

---

## Execution order

```text
T1 → T2 → T3          (P0 — ship before anything else)
T4 → T5 → T6 → T7     (P1 core)
T8 → T9 → T10         (P1 gate/prompts)
T11 → T12 → T13 → T14 (P2 UX/docs)
T15                   (verify)
```

## Done when

All Success criteria in the design spec are met and Task 15 suite is green.
