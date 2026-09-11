# Authoring Precision Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-job **Keel vs Precision** engine choice on Automate/Execute; Precision uses one multimodal Cursor `groundRank` call per intent with A+B hybrid shortlist policy, 50-call job cap, and Keel fallback.

**Architecture:** Extend `ConversionJobRequest` + portal start APIs with `authoringEngine`. New `PrecisionBindService` + `PrecisionCallBudget` called from `ProvePhase.attemptIntentWithRetry` when engine is `precision`. New `groundRank` mode in `heal.mjs` + `CursorHealClient.groundRank()`. Keel path unchanged.

**Tech Stack:** Java 21, Spring Boot portal, Selenium prove loop, Node `tools/cursor-heal/heal.mjs`, Cursor SDK Auto, TestNG.

## Global Constraints

- Default engine: `keel`; unknown values normalize to `keel`.
- Precision runtime: Cursor sidecar only (`CURSOR_API_KEY`); no Grok API v1.
- Max Precision calls per job: **50** (`delivery.authoring.precision.max-calls-per-job`).
- `groundRank` must pick `candidateId` from shortlist only; invent only via one `solve` on low confidence.
- On provider error or cap: fallback to Keel bind + log `fallbackReason`.
- Execute workbook patch: unchanged behavior when heal fixes locator.
- Do not change Bug Hunter or Generate flows in this plan.

---

### Task 1: Job model + properties

**Files:**
- Create: `src/main/java/delivery/authoring/AuthoringEngine.java`
- Modify: `src/main/java/delivery/job/ConversionJobRequest.java`
- Modify: `src/main/java/delivery/portal/config/DeliveryPortalProperties.java` (or existing props holder)
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/delivery/authoring/AuthoringEngineTest.java`

**Interfaces:**
- Produces: `AuthoringEngine.parse(String)` → `KEEL` | `PRECISION`
- Produces: `ConversionJobRequest.authoringEngine()` field
- Produces: `delivery.authoring.precision.enabled`, `max-calls-per-job`

- [ ] **Step 1: Write failing test**

```java
@Test
public void parseDefaultsToKeel() {
    assertEquals(AuthoringEngine.KEEL, AuthoringEngine.parse(null));
    assertEquals(AuthoringEngine.KEEL, AuthoringEngine.parse("unknown"));
    assertEquals(AuthoringEngine.PRECISION, AuthoringEngine.parse("precision"));
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `mvn -q -Dtest=AuthoringEngineTest test`

- [ ] **Step 3: Implement enum + request field + properties**

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Commit** (only if user asks)

---

### Task 2: Portal API + UI toggle

**Files:**
- Modify: `src/main/java/delivery/portal/api/UploadController.java` (or automate start endpoint)
- Modify: `src/main/java/delivery/portal/api/ExecuteController.java`
- Modify: `src/main/resources/templates/upload.html`
- Modify: `src/main/resources/templates/execute.html`
- Test: `src/test/java/delivery/portal/api/AuthoringEngineApiTest.java` (new)

**Interfaces:**
- Consumes: `AuthoringEngine.parse`
- Produces: `authoringEngine` in job request JSON written to disk

- [ ] **Step 1: Write failing API test** — POST automate with `authoringEngine: "precision"`, read `request.json`, assert field present.

- [ ] **Step 2: Run test — FAIL**

- [ ] **Step 3: Add radio group to upload.html + execute.html** (Keel default, Precision opt-in); wire JSON body field.

- [ ] **Step 4: Wire controllers to set field on `ConversionJobRequest` / job builder.

- [ ] **Step 5: Run test — PASS**

---

### Task 3: Precision call budget

**Files:**
- Create: `src/main/java/delivery/authoring/PrecisionCallBudget.java`
- Test: `src/test/java/delivery/authoring/PrecisionCallBudgetTest.java`

**Interfaces:**
- Produces: `boolean tryConsume()` — false when cap reached
- Produces: `int used()`, `int remaining()`, `int cap()`

- [ ] **Step 1: Failing test** — cap=3, fourth `tryConsume()` returns false.

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement thread-safe counter loaded from properties (default 50).

- [ ] **Step 4: Run — PASS**

---

### Task 4: Ground rank response model + client stub

**Files:**
- Create: `src/main/java/delivery/authoring/GroundRankResult.java`
- Modify: `src/main/java/delivery/heal/CursorHealClient.java`
- Test: `src/test/java/delivery/authoring/GroundRankResultTest.java`
- Test: `src/test/java/delivery/heal/CursorHealClientGroundRankTest.java` (mock sidecar or parse-only)

**Interfaces:**
- Produces: `GroundRankResult.parse(JSONObject)` with `candidateId()`, `confidence()`, `isAcceptable()`
- Produces: `CursorHealClient.groundRank(intent, shortlist, slimHtml, screenshot, priorSteps)` → `GroundRankResult`

- [ ] **Step 1: Test parse high/medium/low confidence JSON**

- [ ] **Step 2: Test client returns empty result when disabled (no API key)

- [ ] **Step 3: Implement parse + client method sending `mode: groundRank` (response parsing only until Task 5)

- [ ] **Step 4: Run tests — PASS**

---

### Task 5: Sidecar `groundRank` mode

**Files:**
- Modify: `tools/cursor-heal/heal.mjs`
- Create: `tools/cursor-heal/test/groundRank-prompt.test.mjs` (optional) OR Java golden-file test
- Test: extend `CursorHealClientGroundRankTest` with recorded JSON fixture

**Interfaces:**
- Consumes: same request shape as spec
- Produces: JSON with `candidateId`, `confidence`, `rationale`, `ranked[]`

- [ ] **Step 1: Add `groundRank` prompt template** — multimodal, shortlist table, rules: pick only from list, return JSON only.

- [ ] **Step 2: Wire `mode === 'groundRank'` branch in heal.mjs (mirror `pick`/`solve` pattern).

- [ ] **Step 3: Manual smoke** — `echo '{...}' | node tools/cursor-heal/heal.mjs` with test payload (skip if no API key in CI; use fixture parse test instead).

- [ ] **Step 4: Document prompt in spec cross-link.

---

### Task 6: PrecisionBindService

**Files:**
- Create: `src/main/java/delivery/authoring/PrecisionBindService.java`
- Test: `src/test/java/delivery/authoring/PrecisionBindServiceTest.java`

**Interfaces:**
- Consumes: `CursorHealClient`, `PrecisionCallBudget`, `DomCandidate` list, `IntentLine`
- Produces: `PrecisionBindOutcome` — `BOUND` (ProvenStep), `NEEDS_SOLVE`, `FALLBACK_KEEL` (reason)

Logic:
1. If budget exhausted → `FALLBACK_KEEL(CAP_EXCEEDED)`
2. Build shortlist table string (reuse HealCascade formatter if exists)
3. `groundRank` → consume 1 call
4. If `high|medium` + valid candidateId → bind via existing binder helper
5. Else → `NEEDS_SOLVE` (caller invokes one solve)

- [ ] **Step 1: Failing tests** for high-confidence bind, low-confidence needs solve, cap fallback.

- [ ] **Step 2: Implement service**

- [ ] **Step 3: Run tests — PASS**

---

### Task 7: ProvePhase integration

**Files:**
- Modify: `src/main/java/delivery/job/ProvePhase.java`
- Modify: `src/main/java/delivery/job/TcDraft.java` (metadata fields)
- Test: `src/test/java/delivery/job/ProvePhasePrecisionTest.java`

**Interfaces:**
- Consumes: `ConversionJobRequest.authoringEngine()`, `PrecisionBindService`
- Produces: draft fields `authoringEngine`, `precisionCallsUsed`, `precisionFallback`, `fallbackReason`

- [ ] **Step 1: Failing test** — mock `CursorHealClient` returns high-confidence pick; prove uses bound step without `StepIntentBinder` on precision path.

- [ ] **Step 2: Failing test** — disabled client → Keel bind runs, `precisionFallback=true`.

- [ ] **Step 3: In `attemptIntentWithRetry`**, branch at top:
  - `precision` → `PrecisionBindService` → execute or one `solve` → else Keel fallback
  - `keel` → existing path unchanged
  - Skip `VisionProveHook` when engine is precision (vision in groundRank)

- [ ] **Step 4: Pass `PrecisionCallBudget` per job** (create in `prove()` loop).

- [ ] **Step 5: Run ProvePhase + existing prove tests — PASS**

---

### Task 8: Execute workbook patch + job message

**Files:**
- Modify: `src/main/java/delivery/job/ExecuteJobRunner.java` (if needed for message)
- Modify: `src/main/java/delivery/portal/worker/ConversionWorker.java` / status message
- Modify: `src/main/resources/templates/status.html` (fallback banner)
- Test: extend `ExecuteJobRunner` test if exists

- [ ] **Step 1: When `precisionFallback` on any draft, append warning to job message.

- [ ] **Step 2: Status page shows muted banner when message contains `PRECISION_FALLBACK`.

- [ ] **Step 3: Verify `HealWorkbookPatcher` still runs for precision-healed steps.

---

### Task 9: Docs + ops

**Files:**
- Modify: `docs/ops/end-to-end-flow.md`
- Modify: `docs/superpowers/specs/2026-09-11-authoring-precision-engine-design.md` (status → Implemented when done)

- [ ] **Step 1: Document engine toggle, cap, fallback, groundRank contract.

- [ ] **Step 2: Add properties table to README or ops doc.

---

## Self-review (plan vs spec)

| Spec requirement | Task |
|------------------|------|
| User toggle Automate+Execute | Task 2 |
| Option B one multimodal call | Tasks 4–6 |
| A+B hybrid shortlist | Task 6 |
| 50 call cap | Task 3, 7 |
| Keel fallback | Tasks 6–7 |
| Cursor sidecar only | Task 5 |
| Execute workbook patch | Task 8 |
| Draft metadata | Task 7 |
| Keel default | Tasks 1–2 |
| Skip UI-TARS on precision | Task 7 |

No placeholder tasks; all paths have tests specified.

---

## Execution handoff

**Plan saved to:** `docs/superpowers/plans/2026-09-11-authoring-precision-engine.md`

**Two execution options:**

1. **Subagent-Driven (recommended)** — one subagent per task, review between tasks  
2. **Inline Execution** — implement in this session with executing-plans checkpoints  

**Which approach do you want?** (You can also hand this plan to Grok bot to implement task-by-task.)
