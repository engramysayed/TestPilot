# Post-fix Enhancements All (A→B→C→D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the full post-fix enhancement program (KeelPath UX, mid-run Execute, Generate JSON/quality/async, cancel + pipeline, final-revise off + retention + model compare) with nothing from the coverage checklist dropped.

**Architecture:** Add small focused types (`KeelPathCounts`, `GenerateQualityGate`, `GeneratedTcJsonParser`, `JobCancelSupport`, `RetentionSweeper`, `PipelineService`) on top of existing jobs/workers/portal. Slice order is mandatory: finish A (tests green) before B, then C, then D.

**Tech Stack:** Java 21, Spring Boot portal, TestNG, Thymeleaf HTML/JS, Ollama via `LocalLlmClient`, existing `JobRecord` / workers.

**Spec:** `docs/superpowers/specs/2026-08-25-post-fix-enhancements-all-design.md`

## Global Constraints

- Delivery order: **A → B → C → D**; do not start the next slice until the current slice’s acceptance tests pass.
- AgentRouter / final-revise: **`delivery.final-revise.enabled=false` by default**; document how to re-enable; no fake revise success.
- Prefer JSON Generate; keep CSV/Excel export; CSV repair remains fallback.
- Quality gate: one auto-retry then surface errors — never silently save garbage.
- Mid-run Execute: durable-copy IR per TC under `execute-runs/{jobId}`; UI polls `/tcs` while `RUNNING`.
- Cancel is cooperative (`CANCELLED`); check between TCs/stories.
- Retention default **14 days**; never delete `generated/` latest via sweeper.
- **Do not commit unless the user explicitly asks** (user rule overrides plan “Commit” steps — treat commit steps as optional).
- Tests: `mvn -q "-Dtest=ClassA,ClassB" test` (PowerShell: quote `-Dtest`).
- Restart portal after property/UI changes before manual smoke.

---

## File map

| Unit | Path | Responsibility |
|------|------|----------------|
| KeelPathCounts | `src/main/java/delivery/excel/KeelPathCounts.java` | Count AUTOMATE/EXECUTE/VISION_ONLY/MANUAL/BLANK |
| Surface guard | `src/main/java/delivery/excel/KeelPathSurfaceGuard.java` | hardBlock / softWarn for Automate vs Execute |
| Workbook meta | `GeneratedWorkbookService.java` | Persist + return `keelPathCounts` |
| Preview KeelPath API | `GeneratedWorkbookController.java` (+ service method) | Update row KeelPaths + rewrite xlsx |
| Generate UI | `generate.html` | Editable KeelPath select |
| Automate/Execute UI | `upload.html`, `execute.html` | Counts, warn/block, mid-run `/tcs`, cancel, pipeline, continue-on-fail copy |
| Progress | `ConversionJobRunner.java`, `EmitPhase.java` | Reserve prove+emit total; `effectiveTotal` |
| Mid-run copy | `ExecuteJobRunner.java` / `ProvePhase.java` | Copy IR/evidence per TC to execute-runs |
| JSON parse | `GeneratedTcJsonParser.java` | Parse LLM JSON → `List<ManualTestCase>` |
| Quality gate | `GenerateQualityGate.java` | Validate + errors list |
| Generate orchestration | `TcGenerateService.java` + prompt file | JSON prompt, gate, retry, compare |
| Async generate | Reuse `GENERATE_BATCH` / `GenerateBatch*` | Single-story async job |
| Cancel | `JobRecord`, `PortalStore`, `*Worker`, new cancel API | Flag + CANCELLED |
| Pipeline | `PipelineService` + controller | Chain Generate→CONVERT→EXECUTE |
| Final revise default | `application.properties`, `DeliveryPortalProperties` | enabled=false; portal message |
| Retention | `RetentionSweeper` + props | Delete aged execute-runs / work dirs |
| Tests | `src/test/java/delivery/...` | One focused test class per task |

---

### Task 1: A1 — KeelPathCounts + workbook meta

**Files:**
- Create: `src/main/java/delivery/excel/KeelPathCounts.java`
- Create: `src/test/java/delivery/excel/KeelPathCountsTest.java`
- Modify: `GeneratedWorkbookService.java` (`saveFromCases`, `describe`)
- Modify: `upload.html`, `execute.html` (banner meta text)

**Interfaces:**
- Produces: `KeelPathCounts.from(List<ManualTestCase>)` → `Map<String,Integer>` with keys `AUTOMATE`,`EXECUTE`,`VISION_ONLY`,`MANUAL`,`BLANK`; `toMap()`; `automateRunnable()` = AUTOMATE+BLANK; `executeRunnable()` = EXECUTE+VISION_ONLY+BLANK
- Note: Do **not** reuse `GeneratedTcCsvParser.countByKeelPath` (it maps blank→EXECUTE).

- [ ] **Step 1: Write failing test**

```java
@Test
public void blankIsBlankNotExecute() {
    List<ManualTestCase> cases = List.of(
            tc("T1", "AUTOMATE"),
            tc("T2", ""),
            tc("T3", "EXECUTE"),
            tc("T4", "MANUAL"));
    KeelPathCounts c = KeelPathCounts.from(cases);
    Assert.assertEquals(c.get("AUTOMATE"), 1);
    Assert.assertEquals(c.get("BLANK"), 1);
    Assert.assertEquals(c.get("EXECUTE"), 1);
    Assert.assertEquals(c.get("MANUAL"), 1);
    Assert.assertEquals(c.automateRunnable(), 2);
    Assert.assertEquals(c.executeRunnable(), 2);
}
```

- [ ] **Step 2: Run test — expect FAIL** (`KeelPathCounts` missing)

`mvn -q "-Dtest=KeelPathCountsTest" test`

- [ ] **Step 3: Implement `KeelPathCounts` + wire into `saveFromCases` / `describe` as `keelPathCounts`**

- [ ] **Step 4: Update banners** to show e.g. `A:2 E:1 M:1 blank:1` from `data.keelPathCounts`

- [ ] **Step 5: Re-run tests — PASS**

- [ ] **Step 6: Commit only if user asked**

---

### Task 2: A2 — Surface mismatch hard block + soft warn

**Files:**
- Create: `src/main/java/delivery/excel/KeelPathSurfaceGuard.java`
- Create: `src/test/java/delivery/excel/KeelPathSurfaceGuardTest.java`
- Modify: `JobController.java`, `ExecuteRunController.java` (reject with 400 + message when hard block)
- Modify: `upload.html`, `execute.html` (confirm on soft warn before submit)

**Interfaces:**
- Produces: `KeelPathSurfaceGuard.hardBlock(Surface, KeelPathCounts) -> Optional<String>`; `softWarn(Surface, KeelPathCounts) -> Optional<String>`
- Hard block Automate when `automateRunnable()==0`; Execute when `executeRunnable()==0`
- Soft warn when runnable &lt; half of `tcCount`

- [ ] **Step 1: Failing tests** for hard/soft cases
- [ ] **Step 2: Implement guard + controller checks when `useGenerated` or after Excel read**
- [ ] **Step 3: UI confirm dialog when soft warn message returned from a lightweight preview endpoint OR client-side from counts already on banner**
- [ ] **Step 4: Tests PASS**

Prefer client-side soft warn from banner counts + server-side hard block (defense in depth).

---

### Task 3: A3 — Editable KeelPath on Generate preview

**Files:**
- Modify: `GeneratedWorkbookController.java` — `PUT /api/projects/{id}/generated-workbook/rows` body `{ "rows": [ { "tcId", "keelPath" } ] }`
- Modify: `GeneratedWorkbookService.java` — `updateKeelPaths(projectId, Map<String,String>)` reload cases from csv/xlsx, patch, `saveFromCases`
- Modify: `generate.html` — `<select>` per row; on change PATCH then refresh counts
- Test: `GeneratedWorkbookKeelPathUpdateTest` (service-level with temp store root)

- [ ] **Step 1: Failing service test** — save cases, update one KeelPath, describe counts change
- [ ] **Step 2: Implement service + API**
- [ ] **Step 3: Wire generate.html select**
- [ ] **Step 4: Tests PASS**

---

### Task 4: A4 — Automate Phase2 progress reserve

**Files:**
- Modify: `ConversionJobRunner.java`
- Modify: `EmitPhase.java` — use `progress.effectiveTotal(phaseSize)` instead of total=1 everywhere
- Test: `ConversionProgressReserveTest` or extend `JobProgressTrackerTest`

**Emit step units (fixed):** 7 (load IR, cluster, final revise slot, write pages, compile, static revise, zip) — or count actual `progress.update` call sites and keep constant `EMIT_UNITS = 7`.

- [ ] **Step 1: Failing test** — after proveUnits reserved, `effectiveTotal(proveUnits)` stays prove+emit while current==proveUnits message is Design/Phase2
- [ ] **Step 2: `ConversionJobRunner`: `int jobTotal = allCases.size() + EmitPhase.PROGRESS_UNITS;` before prove; pass reserved total via tracker
- [ ] **Step 3: `EmitPhase` updates with `progress.effectiveTotal(...)` and advances current from `proveUnits` base — simplest approach: runner sets `progress.update(allCases.size(), jobTotal, "Phase2 starting")` then EmitPhase only bumps message/`current` using `Math.max(progress.current(), …)` without resetting total to 1
- [ ] **Step 4: Tests PASS**

---

### Task 5: A5 — Mid-run Execute IR copy + UI poll

**Files:**
- Modify: `ExecuteJobRunner.java` — pass `executeDest` into prove OR copy after each TC
- Modify: `ProvePhase.java` — optional `Path durableIrRoot` callback/copy after `drafts.write`
- Modify: `execute.html` — in `pollJob`, when not done, call `loadResults(jobId)` (or lighter refresh) every poll
- Test: unit test that a helper `ExecuteRunArtifacts.copyTc(work, dest, tcId)` copies `ir/{tcId}.json` if present

**Preferred design:** extract `ExecuteRunArtifacts.syncTc(Path work, Path dest, String tcId)` and call from ExecuteJobRunner loop if ProvePhase stays conversion-shared — **better:** in `ExecuteJobRunner`, custom loop is too heavy; add optional `Consumer<TcDraft> afterDraft` or `Path mirrorRoot` on ProvePhase used only by Execute.

- [ ] **Step 1: Failing test** for `ExecuteRunArtifacts.syncTc`
- [ ] **Step 2: Implement sync + wire Execute prove path
- [ ] **Step 3: `execute.html` poll loads partial results while RUNNING
- [ ] **Step 4: Tests PASS**

**Slice A gate:** run A1–A5 tests together before starting B.

---

### Task 6: B1 — GeneratedTcJsonParser + prompt

**Files:**
- Create: `GeneratedTcJsonParser.java`
- Create: `GeneratedTcJsonParserTest.java`
- Modify: prompt `keel-tc-generate-from-stories-to-csv.txt` **or** add sibling `...-to-json.txt` and select in `TcGenerateService`
- Modify: `TcGenerateService` — try JSON parse first, else CSV

JSON shape:

```json
{
  "testCases": [
    {
      "tcId": "TC_01",
      "title": "...",
      "preconditions": "...",
      "steps": "1. ...\n2. ...",
      "expectedResult": "...",
      "priority": "High",
      "tags": "",
      "visualAssertion": "",
      "testData": "",
      "keelPath": "AUTOMATE"
    }
  ],
  "coverageNotes": "..."
}
```

- [ ] **Step 1: Failing parser tests** (happy JSON, markdown-fenced JSON, fallback throws → caller uses CSV)
- [ ] **Step 2: Implement parser**
- [ ] **Step 3: Wire generateStory**
- [ ] **Step 4: PASS**

---

### Task 7: B2 — GenerateQualityGate + one retry

**Files:**
- Create: `GenerateQualityGate.java` + test
- Modify: `TcGenerateService.generateStory` — after parse, `gate.validate(cases)`; on fail, one more LLM call with error list in user prompt; if still fail, throw `IllegalArgumentException` with joined errors (API → 400)

Checks:
- `tcId` matches `TC_\\d+` or `TC_[A-Z0-9_]+` (project may use `TC_DQ_01` — allow `TC_` + alnum/underscore)
- keelPath blank or parseable by `KeelPath.parse` without throwing for known aliases; reject garbage
- steps non-blank after `ExcelStepText` normalize; reject if contains literal backslash-n as two chars without real newlines for multi-step (use existing normalizer)
- tcId must not contain step-like prose

- [ ] **Step 1: Failing gate tests**
- [ ] **Step 2: Implement + wire retry once**
- [ ] **Step 3: PASS**

---

### Task 8: B3 — Async single Generate job UX

**Files:**
- Modify: `GenerateTcController` or add endpoint `POST /api/projects/{id}/generate-async` that creates `GENERATE_BATCH` job with one story file (reuse batch upload path)
- Modify: `generate.html` — optional “Run as job” that redirects to status/batch progress (reuse existing batch UI patterns from generate page if any)

Simplest path: write temp stories file → same as batch controller → return `jobId`.

- [ ] **Step 1: API test or service test** enqueue job kind GENERATE_BATCH
- [ ] **Step 2: Implement endpoint + minimal UI**
- [ ] **Step 3: PASS**

**Slice B gate:** B1–B3 tests green.

---

### Task 9: C1 — Job cancel

**Files:**
- Modify: `JobRecord.java` — add `CANCELLED`; `AtomicBoolean cancelRequested`
- Modify: `PortalStore` persist cancelRequested if needed (or memory-only flag is OK for single-node portal — **persist status only**; cancelRequested memory map on PortalStore `ConcurrentHashMap`)
- Create: cancel endpoint on `JobController` / `ExecuteRunController` / shared `POST /api/jobs/{jobId}/cancel`
- Modify: `ProvePhase` / `GenerateBatchJobRunner` — check cancel between iterations; throw `CancellationException` or return early
- Workers: map to `CANCELLED` status
- UI: Cancel button on execute/automate progress panels
- Test: `JobCancelSupportTest` — flag set; prove loop respects flag with mock list

- [ ] **Step 1: Failing cancel unit test**
- [ ] **Step 2: Implement flag + API + worker/prove checks**
- [ ] **Step 3: UI button**
- [ ] **Step 4: PASS**

---

### Task 10: C2 — Continue-on-fail UI copy

**Files:**
- Modify: `execute.html` — hint under progress: “Failed cases are marked TODO; the run continues with remaining cases.”
- No behavior change unless a stop-on-fail exists (verify ProvePhase; do not add stop-on-fail).

- [ ] **Step 1: Confirm ProvePhase continues after TODO/PARTIAL** (grep/read)
- [ ] **Step 2: Add UI copy only**
- [ ] **Step 3: No new failing test required** (copy-only); optional snapshot not needed

---

### Task 11: C3 — Pipeline Generate→Automate→Execute

**Files:**
- Create: `PipelineService.java`, `PipelineController.java`
- Optional: simple in-memory/DB-free `pipelineId` → stage status JSON under store `pipelines/{id}.json`
- Stages: (1) ensure generated workbook / generate if stories provided (2) start CONVERT job useGenerated (3) on CONVERT success start EXECUTE useGenerated
- Skip Automate stage if `automateRunnable()==0`; skip Execute if `executeRunnable()==0`; fail if both zero
- UI: button on generate.html and/or project page “Run pipeline”
- Test: `PipelineServiceTest` with mocked job starter interface

**Interfaces:**
```java
public interface JobStarter {
    String startConvert(String projectId, ...);
    String startExecute(String projectId, ...);
}
```

- [ ] **Step 1: Failing pipeline unit test** (stage transitions with fake starter)
- [ ] **Step 2: Implement service + API**
- [ ] **Step 3: Minimal UI**
- [ ] **Step 4: PASS**

**Slice C gate:** C1–C3 tests green.

---

### Task 12: D1 — Final-revise off by default

**Files:**
- Modify: `application.properties` — `delivery.final-revise.enabled=false`
- Modify: `DeliveryPortalProperties` default `finalReviseEnabled = false`
- Modify: `delivery.properties.example` + short note in `docs/ops/draft-final-revise-layer.md` or `README` snippet
- Modify: Automate UI (`upload.html`) — if checkbox Client delivery checked while props say disabled, show warning (expose `finalReviseEnabled` via existing config/bootstrap endpoint or data attribute on page)

- [ ] **Step 1: Assert properties default false in a small Spring test or property file grep test**
- [ ] **Step 2: Flip defaults + docs + UI warning**
- [ ] **Step 3: PASS**

---

### Task 13: D2 — Retention sweeper

**Files:**
- Create: `RetentionSweeper.java` + `RetentionProperties` on `DeliveryPortalProperties` (`retentionDays`, default 14; 0=off)
- `@Scheduled` daily or hourly
- Delete: `{storeRoot}/{projectId}/execute-runs/{jobId}` older than TTL by dir lastModified; `{workDir}` child dirs older than TTL
- Never touch `{storeRoot}/{projectId}/generated`
- Test: create temp dirs with old lastModifiedTime, run sweeper, assert deleted

- [ ] **Step 1: Failing RetentionSweeperTest**
- [ ] **Step 2: Implement**
- [ ] **Step 3: PASS**

---

### Task 14: D3 — Model compare

**Files:**
- Modify: `TcGenerateService` — `compare(project, stories, modelA, modelB)` runs two `generateStory` **without** auto-save
- Modify: `GenerateTcController` — `POST .../generate/compare`
- Modify: `generate.html` — Compare button → side-by-side counts; Save applies chosen model via existing save
- Test: service test with stubbed LLM or parse-only compare of two fixture strings

- [ ] **Step 1: Failing compare test** (mock/stub)
- [ ] **Step 2: Implement API + UI**
- [ ] **Step 3: PASS**

**Slice D gate:** D1–D3 + full checklist in spec §11 marked done.

---

## Spec coverage self-check

| Spec item | Task |
|-----------|------|
| A1 counts | Task 1 |
| A2 block/warn | Task 2 |
| A3 editable KeelPath | Task 3 |
| A4 Phase2 progress | Task 4 |
| A5 mid-run Execute | Task 5 |
| B1 JSON | Task 6 |
| B2 gate+retry | Task 7 |
| B3 async generate | Task 8 |
| C1 cancel | Task 9 |
| C2 continue UI | Task 10 |
| C3 pipeline | Task 11 |
| D1 final-revise off | Task 12 |
| D2 retention | Task 13 |
| D3 model compare | Task 14 |

No placeholders. Types named above must match across tasks.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-25-post-fix-enhancements-all.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute tasks in this session with `executing-plans` checkpoints  

Which approach?
