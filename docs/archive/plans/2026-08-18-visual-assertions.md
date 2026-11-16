# Visual Assertions + Heal Vision Hints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Opt-in Excel `VisualAssertion` after a TC proves, plus compact VLM attempt lines in Ollama/Cursor/invent heal prompts.

**Architecture:** Extend `VisionGroundingProvider` with `assertVisual` (separate JSON from bbox `analyze`). `VisualAssertionGate` runs once after all intents succeed. `VisionAttempt` traces are recorded during Layer 1.5 / heal 2.5 and injected into existing heal payloads. No dual-VLM, no Figma, no iframe walk-in, no `moveByOffset`.

**Tech Stack:** Java 21, TestNG, Apache POI, Ollama VLM (Qwen / UI-TARS), Node Cursor sidecar (`tools/cursor-heal/heal.mjs`).

## Global Constraints

- Security > maintainability > performance. No site hardcoding.
- Vision = perception only. Selenium executes locators. Visual assert never clicks.
- `UNCERTAIN` = visual fail. No locator invent. No bug invent.
- `referenceImage` always null in Phase 1.
- PNG bytes never in heal text. Current screenshot stays a separate attachment.
- Do not commit unless the user asks.

## File map

| File | Responsibility |
|---|---|
| `ManualTestCase.java` | Optional `visualAssertion`; hash includes it; 7-arg ctor keeps tests compiling |
| `ExcelTcReader.java` | Optional `VISUALASSERTION` column |
| `VisionAssertionResult.java` | PASS/FAIL/UNCERTAIN + confidence/observation/evidence/error |
| `VisionAssertionParser.java` | Assertion JSON only (not bbox parser) |
| `VisionGroundingProvider.java` | `assertVisual(...)` |
| `QwenVisionProvider.java` / `UiTarsVisionProvider.java` | Shared assertion prompt + parser |
| `VisionGroundingConfig.java` | `assertionsEnabled`, `healHintsEnabled`, `createAssertionProvider` |
| `VisualAssertionGate.java` | Skip vs evaluate |
| `VisualAssertionEvidence.java` | `visual-assert.png` + `.json` |
| `ProvePhase.java` | After intents succeed: screenshot now → gate → PARTIAL on fail |
| `VisionAttempt.java` / `VisionAttemptLog.java` | Compact lines, ThreadLocal cap 8 |
| `ViewportSweep` / `VisionGroundingEngine` / `VisionHealSupport` | Record attempts |
| `AuthoringService` / `CursorHealClient` / `FreeInventHealer` / `heal.mjs` | Inject hints |
| `application.properties` | Enable grounding + assertions + heal-hints for local test |

---

### Task 1: Excel optional VisualAssertion

**Files:**
- Modify: `src/main/java/delivery/excel/ManualTestCase.java`
- Modify: `src/main/java/delivery/excel/ExcelTcReader.java`
- Modify: `src/main/java/delivery/job/ConversionJobRunner.java` (`bodyOnlyCase` must keep `visualAssertion`)
- Test: `src/test/java/delivery/excel/ExcelTcReaderTest.java`
- Test: `src/test/java/delivery/excel/ManualTestCaseHashTest.java`

- [ ] **Step 1: Write failing tests** for missing column still reads; `Visual Assertion` header populates field; `contentHash` changes when only visual cell changes.

- [ ] **Step 2: Implement** 8th record component with 7-arg convenience constructor defaulting `""`. Reader uses `columns.getOrDefault("VISUALASSERTION", -1)`.

- [ ] **Step 3: Run** `mvn -q -Dtest=ExcelTcReaderTest,ManualTestCaseHashTest test`

---

### Task 2: Assertion parser + provider method

**Files:**
- Create: `VisionAssertionResult.java`, `VisionAssertionParser.java`
- Modify: `VisionGroundingProvider`, `QwenVisionProvider`, `UiTarsVisionProvider`, `FakeVisionGroundingProvider`
- Test: `src/test/java/delivery/vision/VisionAssertionParserTest.java`
- Test: `src/test/java/delivery/vision/QwenVisionProviderAssertTest.java`

Parser rules: missing/unknown status → UNCERTAIN; missing/NaN/negative confidence → 0.5; empty output → UNCERTAIN never PASS; prompt example confidence is 0.9.

- [ ] **Step 1: Failing parser tests**
- [ ] **Step 2: Implement parser + `assertVisual`**
- [ ] **Step 3: Run** `mvn -q -Dtest=VisionAssertionParserTest,QwenVisionProviderAssertTest test`

---

### Task 3: Config flags + gate + evidence + ProvePhase hook

**Files:**
- Modify: `VisionGroundingConfig.java`
- Create: `VisualAssertionGate.java`, `VisualAssertionEvidence.java`
- Modify: `ProvePhase.java` (intent-loop success + batch-fallback success)
- Test: `VisionGroundingConfigTest.java`, `VisualAssertionGateTest.java`, `VisualAssertionEvidenceTest.java`

`createAssertionProvider()` ignores grounding flag; uses same provider switch. Skip when flag off or cell blank. FAIL/UNCERTAIN → `TcDraftStatus.PARTIAL`, reason `VISUAL_ASSERT:FAIL` or `VISUAL_ASSERT:UNCERTAIN`. Capture PNG via `TcExecutionService.capturePngBytes()` at assert time.

- [ ] **Step 1: Failing flag/gate/evidence tests**
- [ ] **Step 2: Implement + wire ProvePhase**
- [ ] **Step 3: Run** those tests

---

### Task 4: VisionAttempt heal hints

**Files:**
- Create: `VisionAttempt.java`, `VisionAttemptLog.java`
- Modify: `ViewportSweep`, `VisionGroundingEngine`, `VisionHealSupport`
- Modify: `AuthoringService.healIntentWithOllama`, `CursorHealClient`, `FreeInventHealer`
- Modify: `tools/cursor-heal/heal.mjs`
- Test: `VisionAttemptTest.java`, `HealVisionHintsTest.java`

Line format: `VISION {provider}/{model} bbox=x,y,w,h conf=0.xx grounded=id|none used=yes|no outcome=grounded|miss|filtered|unavailable`  
Cap 8 × 200 chars. Omit `visionAttempts` / vision section when empty or `heal-hints` false. Sidecar inserts lines under `## Vision attempts this intent`.

- [ ] **Step 1: Failing format + payload tests**
- [ ] **Step 2: Implement recording + injection**
- [ ] **Step 3: Run** those tests

---

### Task 5: Enable for local testing

**Files:**
- Modify: `src/main/resources/application.properties`

Set:

```
delivery.vision.grounding.enabled=true
delivery.vision.assertions.enabled=true
delivery.vision.heal-hints.enabled=true
delivery.vision.provider=qwen
delivery.vision.model=qwen2.5vl:3b
```

Excel: add optional column `VisualAssertion` (example: `Secure Area heading is visible after login`). Empty cell = skip assert.

- [ ] **Step 1: Properties on**
- [ ] **Step 2: Run** `mvn -q "-Dtest=ExcelTcReaderTest,ManualTestCaseHashTest,VisionAssertionParserTest,QwenVisionProviderAssertTest,VisionGroundingConfigTest,VisualAssertionGateTest,VisualAssertionEvidenceTest,VisionAttemptTest,HealVisionHintsTest,VisionGroundingConfigProviderSwitchTest" test`

## Spec coverage

- Optional Excel column + hash → Task 1
- assertVisual JSON / UNCERTAIN / 0.9 example → Task 2
- When to run, flags, evidence, prove outcomes → Task 3
- Heal hints to Ollama/Cursor/invent → Task 4
- Enable for user test → Task 5
- iframe/canvas/Figma/dual-VLM → out of scope (no tasks)
