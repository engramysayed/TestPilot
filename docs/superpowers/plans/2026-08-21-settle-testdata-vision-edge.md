# Settle wait + TestData + Vision prompt + Edge prefs — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do **not** commit unless the user asks.

**Goal:** Make post-action settle + fresh DOM the default, keep customer TestData through heal/auto-fill, upgrade Qwen vision prompt contract, and suppress Edge password bubbles like Chrome.

**Architecture:** Four small workstreams on existing code. Prove path sleeps then re-snapshots between intents. Inventor / heal / RequiredControlFiller always prefer `IntentLine.testData()`. Vision keeps bbox→DOM grounding; only prompt input/logging change. Edge mirrors Chrome prefs.

**Tech Stack:** Java 21, TestNG, Selenium Chrome/Edge options, existing `delivery.vision` + `delivery.heal` + `delivery.authoring`.

## Global Constraints

- Default post-action wait: **5000** ms via `DELIVERY_POST_ACTION_WAIT_MS`
- Compile: `"-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21"`
- No site hardcoding; no `moveByOffset` as default execution
- Vision: gap-fill only — do not rebuild grounding pipeline
- Do not commit unless the user asks
- Spec: `docs/superpowers/specs/2026-08-21-settle-testdata-vision-edge-design.md`

## File map

| File | Responsibility |
|---|---|
| `drivers/EdgeFactory.java` | Password-manager prefs + `buildOptions()` |
| `src/test/java/drivers/EdgeFactoryOptionsTest.java` | Prefs assertions |
| `delivery/authoring/DummyValueInventor.java` | Column concrete always beats invent |
| `delivery/authoring/RequiredControlFiller.java` | Accept TestData when auto-filling |
| `delivery/authoring/AuthoringService.java` / heal callers | Ensure `intent.testData()` flows |
| `delivery/excel/ExcelTcReader.java` | Preserve / pad TestData blank lines vs Steps |
| `delivery/job/ProvePhase.java` or `TcExecutionService.java` | Post-action wait + re-snapshot choke point |
| `delivery/job/PostActionSettle.java` (new, small) | Read wait ms from env/property |
| `delivery/vision/VisionPromptTemplate.java` (new) | Versioned system + few-shot text |
| `delivery/vision/QwenVisionProvider.java` | Structured user prompt from template |
| Tests under `src/test/java/delivery/...` | TDD for each workstream |

---

### Task 1: Edge password-manager prefs

**Files:**
- Modify: `src/main/java/drivers/EdgeFactory.java`
- Create: `src/test/java/drivers/EdgeFactoryOptionsTest.java`

- [x] Write `EdgeFactoryOptionsTest` mirroring `ChromeFactoryOptionsTest` (credentials / password_manager / leak / autofill false; keep disable-notifications).
- [x] Run test — expect RED (no prefs).
- [x] Rename `getOptions` → `buildOptions()`, add same `setExperimentalOption("prefs", Map.of(...))` as Chrome.
- [x] Run test — expect GREEN.
- [x] (Commit only if user asks.)

---

### Task 2: TestData wins on invent + heal (unit)

**Files:**
- Modify: `src/main/java/delivery/authoring/DummyValueInventor.java` (if priority wrong)
- Modify: `src/main/java/delivery/authoring/AuthoringService.java` (if heal drops testData)
- Create/extend: `src/test/java/delivery/authoring/DummyValueInventorFakerTest.java` or new `TestDataHealPriorityTest.java`

- [x] Write failing test: `fromStepOrInvent("Enter in the First Name field", "John", ...)` returns `"John"` (not Faker).
- [x] Write failing test: `AuthoringService.stepsPreferringCandidate` for TYPE_FIELD with `IntentLine(..., "John")` produces ProvenStep value `John`.
- [x] Fix inventor / heal path until GREEN.
- [x] (Commit only if user asks.)

---

### Task 3: RequiredControlFiller respects TestData

**Files:**
- Modify: `src/main/java/delivery/authoring/RequiredControlFiller.java`
- Modify: prove call sites that call `planFillsBeforeClick`
- Create: `src/test/java/delivery/authoring/RequiredControlFillerTestDataTest.java`

- [x] Write failing test: HTML with empty first-name / last-name / postal; call fill API with TestData map or IntentLines containing John/Doe/12345; assert typed values are those, not invented.
- [x] Extend `planFills` / `planFillsBeforeClick` to accept optional TestData (or list of IntentLine); match fields by label/name/placeholder tokens; invent only if no concrete line.
- [x] Wire ProvePhase auto-fill call to pass body intents’ testData.
- [x] Run tests — GREEN.
- [x] (Commit only if user asks.)

---

### Task 4: Excel TestData line alignment

**Files:**
- Modify: `src/main/java/delivery/excel/ExcelTcReader.java`
- Extend: `src/test/java/delivery/excel/ExcelTcReaderTest.java`

- [x] Write failing test: Steps with N lines + TestData with leading blank and trailing blanks → reader preserves line count alignment for `parseIntents` (John on First Name line).
- [x] Fix `cellKeepNewlines` / pad TestData to Steps line count with empty strings so trailing blanks are not lost to `stripTrailing` alone.
- [x] Run ExcelTcReaderTest — GREEN.
- [x] (Commit only if user asks.)

---

### Task 5: Post-action settle (5s default)

**Files:**
- Create: `src/main/java/delivery/job/PostActionSettle.java`
- Create: `src/test/java/delivery/job/PostActionSettleTest.java`
- Modify: `src/main/java/delivery/job/ProvePhase.java` and/or `TcExecutionService.java` (one choke point after successful action)

- [x] Write failing test: default wait ms == 5000; env/property override e.g. 0 or 1000 honored.
- [x] Implement `PostActionSettle.waitMs()` reading `DELIVERY_POST_ACTION_WAIT_MS` then default 5000; clamp negative → 0.
- [x] After each successful execute in the intent loop, call settle sleep then ensure next bind uses **fresh** `HtmlSlimmer.slim(PageSnapshot.html(...))` (already at top of attempt — confirm order is: execute → settle → next intent’s snapshot, not snapshot-before-settle of previous page only).
- [x] Log once: `POST_ACTION_SETTLE: ms=...`.
- [x] Unit test settle reader GREEN; avoid sleeping 5s in unit tests (override property to 0 in tests).
- [x] (Commit only if user asks.)

---

### Task 6: Vision prompt template + structured user message

**Files:**
- Create: `src/main/java/delivery/vision/VisionPromptTemplate.java` (v1 system prompt + optional few-shots from the approved grounding wording; locate-only; JSON schema; no XPath)
- Modify: `src/main/java/delivery/vision/QwenVisionProvider.java`
- Create: `src/test/java/delivery/vision/QwenVisionPromptContractTest.java`

- [x] Write failing test: analyze path builds user text containing `ACTION:`, `TARGET:`, and intent-derived target phrase; system prompt mentions “do not generate XPath”.
- [x] Load v1 template (classpath resource or constant version field `vision.prompt.version=v1`); keep parseResponse / guards unchanged.
- [x] Structure user message from `IntentLine` (kind → ACTION, text → TARGET); SEMANTIC CONSTRAINTS empty or light hints if cheap to derive — no full DOM.
- [x] Run prompt contract test — GREEN.
- [x] (Commit only if user asks.)

---

### Task 7: Smoke verification

**Files:** none (commands)

- [x] `mvn -o ... -Dtest=EdgeFactoryOptionsTest,ChromeFactoryOptionsTest,PostActionSettleTest,QwenVisionPromptContractTest,TestDataHealPriorityTest,RequiredControlFillerTestDataTest,ExcelTcReaderTest,DummyValueInventorFakerTest` — EXIT=0
- [ ] Optional live: SauceDemo Excel with `DELIVERY_POST_ACTION_WAIT_MS=5000` — confirm TestData John/Doe/12345 and cart→checkout without password bubble.
- [x] Report results to user; do not claim success without command output.

---

## Notes for implementers

- Prefer TDD: red → green per task.
- Chrome prefs already landed earlier this session; do not regress them.
- Vision upgrade must not disable `VISION: abort low-confidence` / implausible bbox guards.
- If wait makes local runs painful during debug, document `DELIVERY_POST_ACTION_WAIT_MS=0` without removing re-snapshot.
