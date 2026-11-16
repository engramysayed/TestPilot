# TestPilot Delivery Platform MVP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.  
> **Do not start implementation until the product owner explicitly says to build.**

**Goal:** Deliver a hosted MVP where customers upload our Excel TCs + URL/login, choose New or Update, and download a Java/Selenium/TestNG ZIP (reports + CI) with passed tests and TODO stubs for failures — authored by **local AI only**.

**Architecture:** Evolve TestPilot into a Conversion Engine + Control Plane + thin Portal. One adaptable framework **core template**; deterministic **code writer** fills `pages/` and `tests/` from proven step JSON. Frameworks and locator maps are stored per project on the server for Update.

**Tech Stack:** Java 24, Maven, TestNG, Selenium 4, Apache POI (Excel), local LLM via Ollama HTTP API, Spring Boot (control plane + portal API), simple web UI (Thymeleaf or static+fetch), ZipOutputStream / commons-compress, existing TestPilot layers (`OrchestratorBuilder`, `actionExecute`, `JsonMapper`, drivers).

**Spec:** `docs/superpowers/specs/2026-08-04-testpilot-delivery-platform-design.md`

## Global Constraints

- Local AI only for authoring — **no cloud AI / no Gemini API** in MVP paths.
- Excel ingest accepts **only** columns: `TC_ID`, `Title`, `Preconditions`, `Steps`, `ExpectedResult`, `Priority`, `Tags` (required: `TC_ID`, `Title`, `Steps`, `ExpectedResult`).
- Customer ZIP stack is **Java + Selenium + TestNG** only.
- Partial success: **all** TCs appear in ZIP; failures → `tests/todo/` stubs.
- Secrets: do **not** embed customer app passwords in the ZIP; ship `webapp.properties.example`.
- Update mode stores frameworks on **our server** (Git/PR out of scope).
- AI always authors **new/changed** TCs; rules = prompt policy + validators; skip AI only for **unchanged stored** TCs on Update.
- Do not invent free-form Java from the model — **code writer templates** only.

## Scope note (phases)

This MVP is multiple subsystems. Implement **in order**. Each phase must be demoable before the next:

| Phase | Deliverable |
|-------|-------------|
| A | Framework core template (empty customer layer) builds & CI file present |
| B | Excel → model + local AI author adapter + locator validators |
| C | Author → execute/validate → code writer → ZIP (CLI job, New) |
| D | Project store + Update merge |
| E | Thin portal (upload / status / download) |

---

## File map (target)

```
customer-framework-template/          # Phase A — shipped inside ZIP as core
  pom.xml
  .github/workflows/ci.yml
  README.md
  src/main/java/.../core/...
  src/test/java/.../pages/.gitkeep
  src/test/java/.../tests/generated/.gitkeep
  src/test/java/.../tests/todo/.gitkeep
  src/test/resources/config/webapp.properties.example
  templates/                          # Velocity/Freemarker or Java text templates used by writer
    PageClass.java.ftl
    GeneratedTest.java.ftl
    TodoTest.java.ftl

src/main/java/
  delivery/                           # NEW — engine for Delivery Platform
    excel/ExcelTcReader.java
    excel/ManualTestCase.java
    authoring/LocalLlmClient.java     # Ollama HTTP, replaces Gemini for delivery jobs
    authoring/LocatorPolicy.java
    authoring/LocatorValidator.java
    authoring/AuthoringService.java
    codegen/ProvenStep.java
    codegen/CodeWriter.java
    codegen/PageAccumulator.java
    job/ConversionJobRequest.java
    job/ConversionJobResult.java
    job/ConversionJobRunner.java
    packager/FrameworkPackager.java
    store/ProjectStore.java
    store/LocatorMapStore.java
  llmLayer/                           # keep for now; delivery path must not call Gemini

portal/                               # Phase E — separate Maven module or Spring Boot app
  ...
```

---

### Task 1: Customer framework core template

**Files:**
- Create: `customer-framework-template/pom.xml`
- Create: `customer-framework-template/.github/workflows/ci.yml`
- Create: `customer-framework-template/README.md`
- Create: `customer-framework-template/src/main/java/com/testpilot/core/driver/DriverFactory.java`
- Create: `customer-framework-template/src/main/java/com/testpilot/core/base/BaseTest.java`
- Create: `customer-framework-template/src/main/java/com/testpilot/core/waits/WaitUtils.java`
- Create: `customer-framework-template/src/test/resources/config/webapp.properties.example`
- Create: `customer-framework-template/src/test/java/com/testpilot/pages/.gitkeep`
- Create: `customer-framework-template/src/test/java/com/testpilot/tests/generated/.gitkeep`
- Create: `customer-framework-template/src/test/java/com/testpilot/tests/todo/.gitkeep`

**Interfaces:**
- Produces: a Maven project that compiles with an empty test suite; `BaseTest` exposes `WebDriver driver` lifecycle; config keys `BASE_URL`, `BROWSER_TYPE`, `USERNAME`, `PASSWORD` (password documented as local-only).

- [ ] **Step 1: Scaffold template `pom.xml`** with Selenium 4, TestNG 7.11, Allure or Extent (pick one and stick to it — recommend Allure), Java 24.

- [ ] **Step 2: Add `DriverFactory` + `BaseTest` + `WaitUtils`** (minimal: Chrome/Edge, quit in `@AfterMethod`).

- [ ] **Step 3: Add CI workflow** that runs `mvn -B test` on push/PR (will no-op/pass with zero tests).

- [ ] **Step 4: Add README** explaining: fill `webapp.properties`, run `mvn test`, meaning of `tests/todo`, do not commit secrets.

- [ ] **Step 5: Verify**

```bash
cd customer-framework-template && mvn -B -q test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit** (only when owner allows commits) `chore: add customer framework core template`

---

### Task 2: Excel TC model + reader

**Files:**
- Create: `src/main/java/delivery/excel/ManualTestCase.java`
- Create: `src/main/java/delivery/excel/ExcelTcReader.java`
- Create: `src/test/java/delivery/excel/ExcelTcReaderTest.java`
- Create: `src/test/resources/delivery/sample-manual-tcs.xlsx` (fixture matching schema)
- Modify: `pom.xml` — add Apache POI dependency

**Interfaces:**
- Produces:
  - `record ManualTestCase(String tcId, String title, String preconditions, String steps, String expectedResult, String priority, String tags)`
  - `ExcelTcReader.read(Path excel) -> List<ManualTestCase>`
  - Throws `InvalidExcelTemplateException` if required headers missing or `TC_ID` blank

- [ ] **Step 1: Write failing test** `ExcelTcReaderTest.readsRows_whenHeadersMatchTemplate`

```java
@Test
public void readsRows_whenHeadersMatchTemplate() throws Exception {
    var path = Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx");
    var rows = new ExcelTcReader().read(path);
    Assert.assertFalse(rows.isEmpty());
    Assert.assertEquals(rows.get(0).tcId(), "TC_001");
}
```

- [ ] **Step 2: Run test — expect FAIL** (class missing)

```bash
mvn -B -Dtest=ExcelTcReaderTest test
```

- [ ] **Step 3: Implement `ManualTestCase` + `ExcelTcReader`** (header row case-insensitive match to exact names from Global Constraints).

- [ ] **Step 4: Add test** `rejects_whenRequiredColumnMissing` expecting `InvalidExcelTemplateException`.

- [ ] **Step 5: Run tests — expect PASS**

```bash
mvn -B -Dtest=ExcelTcReaderTest test
```

- [ ] **Step 6: Commit** `feat: parse Delivery Platform Excel TC template`

---

### Task 3: Locator policy + validator

**Files:**
- Create: `src/main/java/delivery/authoring/LocatorPolicy.java`
- Create: `src/main/java/delivery/authoring/LocatorCandidate.java`
- Create: `src/main/java/delivery/authoring/LocatorValidator.java`
- Create: `src/test/java/delivery/authoring/LocatorValidatorTest.java`

**Interfaces:**
- Produces:
  - `LocatorPolicy.promptRules(): String` — full policy text for AI system prompt (spec §6.4)
  - `LocatorCandidate(String strategy, String value, String pageName, String rationale)`
  - `LocatorValidator.validate(LocatorCandidate c, Predicate uniquenessCheck) -> ValidationResult`
  - Rejects: absolute `/html` xpath, obvious dynamic id patterns (`.*\d{6,}.*`, uuid regex), empty value

- [ ] **Step 1: Write tests** for reject absolute xpath, reject uuid-like id, accept `css` `#login-button`.

- [ ] **Step 2: Implement policy string + validator**.

- [ ] **Step 3: Run** `mvn -B -Dtest=LocatorValidatorTest test` — expect PASS.

- [ ] **Step 4: Commit** `feat: add locator policy and validator for authoring`

---

### Task 4: Local LLM client (Ollama) — replace cloud for delivery path

**Files:**
- Create: `src/main/java/delivery/authoring/LocalLlmClient.java`
- Create: `src/main/java/delivery/authoring/AuthorBatchRequest.java`
- Create: `src/main/java/delivery/authoring/AuthorBatchResponse.java`
- Create: `src/test/java/delivery/authoring/LocalLlmClientTest.java` (wiremock or recorded JSON fixture)
- Modify: `src/main/resources/webapp.properties` **or** new `delivery.properties.example` with:
  - `LOCAL_LLM_BASE_URL=http://127.0.0.1:11434`
  - `LOCAL_LLM_MODEL=qwen2.5-vl:latest` (or chosen model name)
- **Do not** call `llmLayer/LLMClient` (Gemini) from `delivery.*`

**Interfaces:**
- Produces:
  - `LocalLlmClient.completeJson(String system, String user) -> String` raw model JSON
  - Request/response DTOs aligned with existing PlannerBatch shape (reuse `JsonMapper.parseBatchToArrays` where possible)
- Consumes: Ollama `/api/chat` (or `/api/generate`) HTTP API

- [ ] **Step 1: Document** in `docs/superpowers/plans/` note or README snippet: install Ollama, pull model, verify `curl http://127.0.0.1:11434/api/tags`.

- [ ] **Step 2: Implement `LocalLlmClient`** with timeouts, non-2xx → exception, **no Anthropic/Google/OpenAI URLs**.

- [ ] **Step 3: Unit test** with mocked HTTP returning fixed JSON batch.

- [ ] **Step 4: Commit** `feat: local Ollama client for delivery authoring`

---

### Task 5: AuthoringService (Excel TC + DOM → proven steps JSON)

**Files:**
- Create: `src/main/java/delivery/authoring/AuthoringService.java`
- Create: `src/main/java/delivery/codegen/ProvenStep.java`
- Modify: reuse `parsingLayer/HtmlSlimmer`, `buildersLayer` state builders where practical
- Create: `src/test/java/delivery/authoring/AuthoringServiceTest.java`

**Interfaces:**
- Consumes: `LocalLlmClient`, `LocatorPolicy`, `LocatorValidator`, `ManualTestCase`, slim HTML, optional screenshot path
- Produces: `List<ProvenStep>` where

```java
public record ProvenStep(
  String tcId,
  String pageName,
  String actionType,
  String action,
  String locatorStrategy,
  String locatorValue,
  String value,
  String assertionType,
  String assertionExpected,
  boolean validated
) {}
```

- Flow: build prompt (TC + policy + slim DOM) → `completeJson` → parse → **validate each locator** → return candidates (validation failures marked for TODO path by caller)

- [ ] **Step 1: Define `ProvenStep` + parse mapping from batch JSON** (prefer adapting `JsonMapper`).

- [ ] **Step 2: Implement `AuthoringService.author(ManualTestCase tc, String slimHtml, Path screenshotOrNull)`**.

- [ ] **Step 3: Test with mocked `LocalLlmClient`** returning one click step; assert validator invoked.

- [ ] **Step 4: Commit** `feat: authoring service for delivery TCs`

---

### Task 6: Execute + validate loop for one TC

**Files:**
- Create: `src/main/java/delivery/job/TcExecutionService.java`
- Create: `src/main/java/delivery/job/TcOutcome.java`
- Modify: wrap/reuse `executionLayer/actionExecute`, `PreStartActions` patterns
- Create: `src/test/java/delivery/job/TcExecutionServiceTest.java` (mock driver if needed; or package-private pure mapping tests)

**Interfaces:**
- Produces:

```java
public enum TcStatus { PASSED, TODO }
public record TcOutcome(String tcId, TcStatus status, List<ProvenStep> provenSteps, String failureReason, Path evidenceDir) {}
```

- Consumes: live `WebDriver` / existing `WebDriverFactory` + `actionExecute`
- Behavior: run authored steps; on failure capture screenshot + reason → `TODO`; on success all steps `validated=true` → `PASSED`

- [ ] **Step 1: Implement execution mapping** from `ProvenStep` → existing action types used by `actionExecute`.

- [ ] **Step 2: Implement expected-result checks** (at minimum: URL contains / element visible / text contains — driven by `assertionType`).

- [ ] **Step 3: Unit-test failure path sets `TcStatus.TODO`** with non-blank reason.

- [ ] **Step 4: Commit** `feat: execute and validate authored TC steps`

---

### Task 7: Code writer (templates → pages + tests)

**Files:**
- Create: `customer-framework-template/templates/PageClass.java.ftl`
- Create: `customer-framework-template/templates/GeneratedTest.java.ftl`
- Create: `customer-framework-template/templates/TodoTest.java.ftl`
- Create: `src/main/java/delivery/codegen/CodeWriter.java`
- Create: `src/main/java/delivery/codegen/PageAccumulator.java`
- Create: `src/test/java/delivery/codegen/CodeWriterTest.java`
- Add: Freemarker (or StringReplace templates if avoiding new dep — prefer Freemarker)

**Interfaces:**
- Consumes: `List<TcOutcome>`, output directory of a copied template project
- Produces: files under:
  - `src/test/java/com/testpilot/pages/{PageName}Page.java`
  - `src/test/java/com/testpilot/tests/generated/{TcId}Test.java`
  - `src/test/java/com/testpilot/tests/todo/{TcId}TodoTest.java`
- `PageAccumulator` merges locators/actions by `pageName` across TCs
- **Never** write free-form model Java — only template render

- [ ] **Step 1: Write Freemarker templates** matching BaseTest / page-object style from Task 1.

- [ ] **Step 2: Implement `CodeWriter.write(Path projectRoot, List<TcOutcome> outcomes)`**.

- [ ] **Step 3: Test** with one PASSED + one TODO outcome; assert both files exist and PASSED test contains `@Test` and page method call; TODO contains `throw new SkipException` or `Assert.fail("TODO: ...")`.

- [ ] **Step 4: Commit** `feat: generate page and test classes from proven steps`

---

### Task 8: Packager + New job runner (CLI)

**Files:**
- Create: `src/main/java/delivery/packager/FrameworkPackager.java`
- Create: `src/main/java/delivery/job/ConversionJobRequest.java`
- Create: `src/main/java/delivery/job/ConversionJobResult.java`
- Create: `src/main/java/delivery/job/ConversionJobRunner.java`
- Create: `src/main/java/delivery/cli/DeliveryCli.java` (or `main` entry)
- Create: `src/test/java/delivery/packager/FrameworkPackagerTest.java`

**Interfaces:**
- `ConversionJobRequest(Path excel, String baseUrl, String username, String password, Path workDir, String mode /* NEW */)`
- `ConversionJobResult(Path zipFile, int passed, int todo, Path scoreReport)`
- `FrameworkPackager.copyTemplate(Path dest)`, `zip(Path projectDir, Path zipOut)`, write `docs/AUTOMATION_SCORE.md` + `docs/LOCATOR_MAP.json`
- Runner wiring: read excel → prestart login/url → for each TC author→execute→collect → write code → zip

- [ ] **Step 1: Implement template copy + zip** (exclude `.git`, include generated sources).

- [ ] **Step 2: Implement `ConversionJobRunner.run(request)` for mode `NEW`**.

- [ ] **Step 3: Manual smoke** against SauceDemo (or internal demo) with sample Excel — expect ZIP with score file. (Owner may run; automate later.)

- [ ] **Step 4: Commit** `feat: conversion job runner produces customer ZIP for New mode`

---

### Task 9: Project store + Update mode

**Files:**
- Create: `src/main/java/delivery/store/ProjectStore.java`
- Create: `src/main/java/delivery/store/LocatorMapStore.java`
- Create: `src/main/java/delivery/store/StoredProject.java`
- Create: `src/test/java/delivery/store/ProjectStoreTest.java`
- Modify: `ConversionJobRunner` — support mode `UPDATE`

**Interfaces:**
- Storage root: configurable `DELIVERY_STORE_ROOT` (default `./delivery-store/{projectId}/`)
- Stores: `framework/` snapshot, `locator-map.json`, `versions/vN.zip`, `last-job.json`
- Update: load map → skip AI for unchanged `TC_ID` with identical Steps+ExpectedResult hash → author only new/changed → merge files into stored framework → bump version → zip

- [ ] **Step 1: Implement filesystem `ProjectStore`** with create/load/saveVersion.

- [ ] **Step 2: Implement TC diff** by `TC_ID` + content hash.

- [ ] **Step 3: Wire UPDATE into `ConversionJobRunner`**.

- [ ] **Step 4: Test** store round-trip + diff detects changed Steps.

- [ ] **Step 5: Commit** `feat: server-side project store and Update conversion`

---

### Task 10: Thin portal + API

**Files:**
- Create module or package: `portal/` (Spring Boot recommended)
  - `ProjectController`, `JobController`, `Auth` (simple session or basic auth for MVP)
  - Upload endpoint multipart Excel
  - Job status polling
  - ZIP download
- Create: minimal HTML pages or SPA: login, project, upload form (URL, login, New|Update), status, download
- Wire: portal enqueues job → calls `ConversionJobRunner` on worker thread/queue

**Interfaces:**
- `POST /api/projects`
- `POST /api/projects/{id}/jobs` (excel, baseUrl, username, password, mode)
- `GET /api/jobs/{jobId}` → `{status, passed, todo, message}`
- `GET /api/jobs/{jobId}/download` → ZIP
- Passwords used for job only; not written into ZIP

- [ ] **Step 1: Scaffold Spring Boot app** depending on delivery engine JAR/module.

- [ ] **Step 2: Implement job API + in-memory or DB job table** (MVP: H2 or JSON files under store).

- [ ] **Step 3: Implement thin UI** for the 7 portal steps in the spec.

- [ ] **Step 4: End-to-end manual test** — upload sample Excel → wait → download ZIP → open score doc.

- [ ] **Step 5: Commit** `feat: thin portal for upload status and ZIP download`

---

### Task 11: Hardening checklist (before calling MVP done)

**Files:**
- Modify: README / portal docs
- Create: `docs/superpowers/specs/excel-template.xlsx` (canonical blank template for customers)
- Create: `delivery.properties.example`

- [ ] **Step 1: Ship blank Excel template** with correct headers only.

- [ ] **Step 2: Ensure Gemini path is unreachable** from `delivery.*` (grep for `generativelanguage.googleapis` / `GEMINI` in delivery package — zero hits).

- [ ] **Step 3: Confirm ZIP contains** CI workflow, README, `webapp.properties.example`, `docs/AUTOMATION_SCORE.md`, no real password file.

- [ ] **Step 4: Confirm Update** second job with one new TC_ID adds one test without wiping previous generated tests.

- [ ] **Step 5: Commit** `docs: delivery MVP hardening and customer Excel template`

---

## Spec coverage check

| Spec area | Tasks |
|-----------|-------|
| Thin portal + ZIP download | 10 |
| Engine live author + validate | 5–8 |
| Local AI only | 4, 11 |
| Framework core template | 1 |
| Code writer pages/tests | 7 |
| Excel template only | 2, 11 |
| TODO for failures | 6–7 |
| Server store Update | 9 |
| Secrets not in ZIP | 8, 10, 11 |
| Locator policy | 3, 5 |
| CI + reports in package | 1, 8 |

## Execution handoff

When the product owner says **build**:

1. Start at **Task 1**, finish Phase A before B.
2. Prefer `superpowers:subagent-driven-development` per task with two-stage review.
3. Do **not** implement portal (Task 10) until CLI New ZIP works (Task 8).
4. Do not enable cloud AI “temporarily.”

**Owner gate:** Implementation starts only after an explicit message such as `build` / `start implementing`.
