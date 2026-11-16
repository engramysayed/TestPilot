# Vision role split + Qwen assert prompt — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.  
> **Do not commit unless the user asks.**

**Goal:** Independently configure grounding vs assert VLM providers (default UI-TARS / Qwen) and harden Qwen’s assert prompt so live scorecards can prove the split.

**Architecture:** Extend `VisionGroundingConfig` with `grounding.*` / `assert.*` keys (legacy `delivery.vision.provider` / `.model` as fallback). `createProvider()` uses grounding; `createAssertionProvider()` uses assert. Harden `QwenVisionProvider.ASSERT_SYSTEM_PROMPT` and add a split live smoke that skips when models are missing.

**Tech Stack:** Java 21, TestNG, Ollama, existing `LocalLlmClient` / `VisionAssertionGate`.

**Spec:** `docs/superpowers/specs/2026-08-21-vision-role-split-design.md`

## Global Constraints

- Approach A; models option C (defaults in code; SkipException if Ollama/model missing).
- Do not port UI-TARS action prompts onto Qwen grounding.
- Do not fine-tune.
- Do not commit unless asked.
- Compile/test: `mvn -q "-Dmaven.compiler.release=21" -Dtest=… test`

---

## File map

| File | Responsibility |
|------|----------------|
| Modify: `VisionGroundingConfig.java` | Split grounding/assert resolve + factory |
| Modify: `QwenVisionProvider.java` | Assert prompt harden; optional empty retry; model from assert config when used as assert |
| Modify: `UiTarsVisionProvider.java` | Prefer grounding model when resolving model (if needed) |
| Create/Modify tests: `VisionGroundingConfigTest` / new split tests | Config matrix |
| Modify: `QwenVisionProviderAssertTest.java` | Prompt contract |
| Create: `VisionRoleSplitLiveSmokeTest.java` | TARS ground + Qwen assert, skip if missing |

---

### Task 1: Config split

**Files:**
- Modify: `src/main/java/delivery/vision/VisionGroundingConfig.java`
- Modify: `src/test/java/delivery/vision/VisionGroundingConfigTest.java`

**Interfaces:**
- `groundingProviderId()`, `groundingModel()`, `assertProviderId()`, `assertModel()`
- `createProvider()` → grounding LLM; `createAssertionProvider()` → assert LLM
- Legacy `providerId()` / `model()` remain grounding-oriented fallbacks for existing logs

- [x] **Step 1: Failing tests** — assert defaults uitars/qwen when enabled flags on and no legacy override; assert provider ignores grounding.enabled; grounding provider ignores assertions.enabled; legacy `delivery.vision.provider=qwen` forces both when split keys unset

- [x] **Step 2: Implement resolve helpers + factories**

- [x] **Step 3: Tests PASS** — `-Dtest=VisionGroundingConfigTest,VisionGroundingConfigProviderSwitchTest`

- [x] **Step 4: Commit only if asked**

---

### Task 2: Qwen assert prompt + retry

**Files:**
- Modify: `src/main/java/delivery/vision/QwenVisionProvider.java`
- Modify: `src/test/java/delivery/vision/QwenVisionProviderAssertTest.java`

**Interfaces:**
- `ASSERT_SYSTEM_PROMPT` must contain `"confidence":0.9`, must not contain `"confidence":0.0`, must not use `<describe…>` / `<why…>` in schema
- `assertVisual`: one retry when UNCERTAIN/empty/placeholder (mirror UI-TARS `needsAssertRetry` lightly, or inline)

- [ ] **Step 1: Fix failing `QwenVisionProviderAssertTest` prompt assertions**

- [ ] **Step 2: Implement prompt + retry**

- [ ] **Step 3: PASS** `-Dtest=QwenVisionProviderAssertTest`

- [ ] **Step 4: Commit only if asked**

---

### Task 3: Provider model resolution

**Files:**
- Modify: `QwenVisionProvider.resolveModel` / `fromConfig` to accept optional model override OR read `VisionGroundingConfig.assertModel()` when constructing via assert factory
- Modify: `UiTarsVisionProvider.resolveModel` similarly for grounding model
- Prefer: `VisionGroundingConfig.createLlmProvider(String providerId, String model)` private helper

- [ ] **Step 1: Unit test** — with `delivery.vision.assert.model=qwen2.5vl:7b` (fake URL ok), assertion provider’s client model is that tag (expose via package-visible getter or stub factory test)

- [ ] **Step 2: Implement**

- [ ] **Step 3: PASS**

---

### Task 4: Live role-split smoke

**Files:**
- Create: `src/test/java/delivery/vision/VisionRoleSplitLiveSmokeTest.java`
- Notes: `docs/superpowers/plans/2026-08-21-vision-role-split-live-notes.md`

**Interfaces:**
- Skip if Ollama down OR `ui-tars` missing OR `qwen2.5vl` (or configured assert model) missing
- Set grounding=uitars, assert=qwen explicitly
- Analyze Click Login → elementFromPoint Login button
- Assert with `createAssertionProvider()` → honesty gate; must not placeholder-PASS; prefer PASS/FAIL with obs≥20 ev≥8 when model works

- [ ] **Step 1: Write smoke**

- [ ] **Step 2: Run** `-Dtest=VisionRoleSplitLiveSmokeTest` — PASS or Skip only

- [ ] **Step 3: Commit only if asked**

---

## Verification

1. Split defaults: ground uitars, assert qwen.  
2. Legacy single provider still works.  
3. Qwen assert prompt unit green.  
4. Live smoke skip-or-pass (option C).  
5. UI-TARS grounding behavior unchanged aside from config.

## Execution handoff

Plan: `docs/superpowers/plans/2026-08-21-vision-role-split.md`

**Executing now** (user already approved + said start).
