# Tasks: Manual TC to Automation Delivery Platform

**Input**: Design documents from `/specs/001-delivery-platform/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included for delivery contracts per constitution (Excel, locator validation, codegen, store). Full browser smoke remains manual per quickstart.

**Organization**: Tasks grouped by user story for independent implementation and testing.

**Build gate**: Do not execute these tasks until the product owner explicitly says to build.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story label (US1–US4)
- Exact file paths included in each description

## Path Conventions

- Engine: `src/main/java/delivery/`, tests under `src/test/java/delivery/`
- Customer core: `customer-framework-template/`
- Portal: `portal/` (after CLI New works)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Scaffold directories and shared config for the delivery engine

- [X] T001 Create package directories `src/main/java/delivery/{excel,authoring,codegen,job,packager,store,cli}` and `src/test/java/delivery/{excel,authoring,codegen,job,packager,store}`
- [X] T002 Create `customer-framework-template/` skeleton directories (`src/main/java/com/testpilot/core`, `src/test/java/com/testpilot/{pages,tests/generated,tests/todo}`, `templates/`, `.github/workflows/`)
- [X] T003 [P] Add Apache POI and Freemarker dependencies to root `pom.xml`
- [X] T004 [P] Add `src/main/resources/delivery.properties.example` with `LOCAL_LLM_BASE_URL`, `LOCAL_LLM_MODEL`, `DELIVERY_STORE_ROOT`, browser keys
- [X] T005 [P] Add blank customer Excel template at `docs/delivery/excel-template.xlsx` matching `specs/001-delivery-platform/contracts/excel-template.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core template, Excel contract, locator policy, local LLM client — MUST complete before user stories

**⚠️ CRITICAL**: No user story work until this phase is complete

- [X] T006 Create `customer-framework-template/pom.xml` with Java 24, Selenium 4, TestNG, Allure (or chosen reporter)
- [X] T007 [P] Implement `customer-framework-template/src/main/java/com/testpilot/core/driver/DriverFactory.java`
- [X] T008 [P] Implement `customer-framework-template/src/main/java/com/testpilot/core/base/BaseTest.java`
- [X] T009 [P] Implement `customer-framework-template/src/main/java/com/testpilot/core/waits/WaitUtils.java`
- [X] T010 [P] Add `customer-framework-template/src/test/resources/config/webapp.properties.example` (no real passwords)
- [X] T011 [P] Add `customer-framework-template/.github/workflows/ci.yml` running `mvn -B test`
- [X] T012 [P] Add `customer-framework-template/README.md` (configure, run, interpret TODOs)
- [X] T013 Verify `customer-framework-template` builds via `mvn -B -q test` in that directory
- [X] T014 [P] Create `src/main/java/delivery/excel/ManualTestCase.java` record per data-model.md
- [X] T015 [P] Create `src/main/java/delivery/excel/InvalidExcelTemplateException.java`
- [X] T016 Implement `src/main/java/delivery/excel/ExcelTcReader.java` per `contracts/excel-template.md`
- [X] T017 Add fixture `src/test/resources/delivery/sample-manual-tcs.xlsx` and `src/test/java/delivery/excel/ExcelTcReaderTest.java` (valid + missing column + blank TC_ID + duplicate TC_ID)
- [X] T018 [P] Create `src/main/java/delivery/authoring/LocatorCandidate.java`
- [X] T019 [P] Create `src/main/java/delivery/authoring/LocatorPolicy.java` with `promptRules()` from constitution locator policy
- [X] T020 Implement `src/main/java/delivery/authoring/LocatorValidator.java`
- [X] T021 Add `src/test/java/delivery/authoring/LocatorValidatorTest.java`
- [X] T022 [P] Create `src/main/java/delivery/authoring/AuthorBatchRequest.java` and `AuthorBatchResponse.java`
- [X] T023 Implement `src/main/java/delivery/authoring/LocalLlmClient.java` (Ollama HTTP only; no Gemini URLs)
- [X] T024 Add `src/test/java/delivery/authoring/LocalLlmClientTest.java` with mocked HTTP JSON fixture
- [X] T025 [P] Create shared job DTOs `src/main/java/delivery/job/TcStatus.java`, `TcOutcome.java`, `ProvenStep.java` under `src/main/java/delivery/codegen/ProvenStep.java` (single ProvenStep location: `delivery/codegen/ProvenStep.java`)
- [X] T026 Grep-guard note in `src/main/java/delivery/README.md`: delivery path must never call `llmLayer` Gemini client

**Checkpoint**: Foundation ready — template builds; Excel/locator/LLM client tested

---

## Phase 3: User Story 1 — First-time package delivery (New) (Priority: P1) 🎯 MVP

**Goal**: Excel + URL/login → live author → validate → generate pages/tests → ZIP downloadable via CLI

**Independent Test**: Run conversion CLI `--mode NEW` with sample Excel + SauceDemo (or demo URL); unzip artifact; confirm every TC_ID is generated or TODO; score file present; no password file; local `mvn test` works after config fill

### Implementation for User Story 1

- [X] T027 [P] [US1] Implement `src/main/java/delivery/authoring/AuthoringService.java` (TC + slim HTML + LocatorPolicy → parse batch → validate locators)
- [X] T028 [P] [US1] Add `src/test/java/delivery/authoring/AuthoringServiceTest.java` with mocked `LocalLlmClient`
- [X] T029 [US1] Implement `src/main/java/delivery/job/TcExecutionService.java` mapping ProvenStep → existing `executionLayer/actionExecute` + expected-result checks
- [X] T030 [US1] Add `src/test/java/delivery/job/TcExecutionServiceTest.java` asserting failure yields `TcStatus.TODO` with reason
- [X] T031 [P] [US1] Add Freemarker templates `customer-framework-template/templates/PageClass.java.ftl`, `GeneratedTest.java.ftl`, `TodoTest.java.ftl`
- [X] T032 [P] [US1] Implement `src/main/java/delivery/codegen/PageAccumulator.java`
- [X] T033 [US1] Implement `src/main/java/delivery/codegen/CodeWriter.java` writing to `pages/`, `tests/generated/`, `tests/todo/`
- [X] T034 [US1] Add `src/test/java/delivery/codegen/CodeWriterTest.java` (one PASSED + one TODO outcome)
- [X] T035 [US1] Implement `src/main/java/delivery/packager/FrameworkPackager.java` (copy template, write `docs/AUTOMATION_SCORE.md`, zip, exclude secrets)
- [X] T036 [US1] Add `src/test/java/delivery/packager/FrameworkPackagerTest.java` asserting example properties only (no password file)
- [X] T037 [US1] Implement `src/main/java/delivery/job/ConversionJobRequest.java` and `ConversionJobResult.java`
- [X] T038 [US1] Implement `src/main/java/delivery/job/ConversionJobRunner.java` for mode `NEW` (prestart URL/login → per-TC author/execute → code write → package)
- [X] T039 [US1] Implement `src/main/java/delivery/cli/DeliveryCli.java` per `contracts/conversion-cli.md`
- [X] T040 [US1] Wire Maven exec/main entry or documented `mvn -q exec:java` for `DeliveryCli` in root `pom.xml`
- [ ] T041 [US1] Run quickstart Phase C smoke (manual): NEW ZIP against demo app; record results in `specs/001-delivery-platform/quickstart.md` notes section if needed

**Checkpoint**: US1 MVP — CLI New ZIP works without portal

---

## Phase 4: User Story 2 — Update existing package (Priority: P1)

**Goal**: Server-stored framework + locator map; Update merges new/changed TCs only

**Independent Test**: After NEW, run CLI `--mode UPDATE` with one new TC_ID; new ZIP retains prior passed tests and adds the new case

### Implementation for User Story 2

- [X] T042 [P] [US2] Implement `src/main/java/delivery/store/StoredProject.java` and `LocatorMapStore.java`
- [X] T043 [US2] Implement `src/main/java/delivery/store/ProjectStore.java` under `DELIVERY_STORE_ROOT/{projectId}/`
- [X] T044 [US2] Add `src/test/java/delivery/store/ProjectStoreTest.java` (round-trip + version bump)
- [X] T045 [US2] Implement TC diff helper in `src/main/java/delivery/store/TcDiffService.java` using contentHash from `contracts/excel-template.md`
- [X] T046 [US2] Extend `src/main/java/delivery/job/ConversionJobRunner.java` for mode `UPDATE` (reuse unchanged; author only new/changed; merge into stored framework)
- [X] T047 [US2] Reject UPDATE when no stored framework (exit code 3) in `DeliveryCli` / runner
- [X] T048 [US2] Persist `locator-map.json` and `docs/LOCATOR_MAP.json` in package via `FrameworkPackager` / store
- [ ] T049 [US2] Run quickstart Phase D smoke (manual): UPDATE with one new TC_ID

**Checkpoint**: US1 + US2 — New and Update via CLI/store

---

## Phase 5: User Story 3 — Reject bad uploads early (Priority: P2)

**Goal**: Invalid Excel fails fast with actionable errors before conversion

**Independent Test**: Submit invalid spreadsheet (missing column / blank TC_ID / duplicate); conversion does not start; clear error returned

### Implementation for User Story 3

- [X] T050 [US3] Centralize Excel validation error codes/messages in `src/main/java/delivery/excel/ExcelValidationMessages.java`
- [X] T051 [US3] Ensure `ExcelTcReader` / CLI maps validation failures to exit code 2 and stderr JSON/message per `contracts/conversion-cli.md`
- [X] T052 [US3] Add `src/test/java/delivery/excel/ExcelTcReaderRejectTest.java` covering all reject rules with message assertions
- [X] T053 [US3] When portal exists, map same errors to HTTP 400 `INVALID_EXCEL` in `portal/.../JobController.java` (implement after T058+ or stub interface now in `delivery/excel` only if portal not started)

**Checkpoint**: US3 — schema failures never start browser/AI work

---

## Phase 6: User Story 4 — Job status and automation score (Priority: P2)

**Goal**: Visible job lifecycle + pass/TODO score; thin portal upload/status/download

**Independent Test**: Create project → upload → NEW → poll status → see score → download ZIP (portal API contract)

### Implementation for User Story 4

- [X] T054 [P] [US4] Add job progress callbacks/writer `src/main/java/delivery/job/JobProgressTracker.java` (current/total, message) used by runner
- [X] T055 [US4] Write `AUTOMATION_SCORE.md` generation in `FrameworkPackager` with passed/todo counts and TC lists
- [X] T056 [US4] Persist `last-job.json` status fields in `ProjectStore` for CLI consumers
- [X] T057 [US4] Scaffold Spring Boot module `portal/pom.xml` depending on delivery engine classes
- [X] T058 [P] [US4] Implement `portal/src/main/java/.../api/ProjectController.java` per `contracts/portal-api.md`
- [X] T059 [P] [US4] Implement `portal/src/main/java/.../api/JobController.java` (create job, get status, download)
- [X] T060 [US4] Implement async job worker `portal/src/main/java/.../worker/ConversionWorker.java` calling `ConversionJobRunner`
- [X] T061 [US4] Add minimal UI pages/static forms under `portal/src/main/resources/` (project, upload, status, download)
- [X] T062 [US4] Ensure download path never injects password into ZIP (integration assert in `portal/src/test/java/.../JobDownloadSecurityTest.java`)
- [X] T063 [US4] Run quickstart Phase E validation against portal API

**Checkpoint**: Full thin-portal journey for New (and Update via same APIs)

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Hardening across stories

- [ ] T064 [P] Confirm zero Gemini/cloud LLM references under `src/main/java/delivery/` via search and document in `src/main/java/delivery/README.md`
- [ ] T065 [P] Update root `README.md` with Delivery Platform overview linking `specs/001-delivery-platform/quickstart.md`
- [ ] T066 Align Superpowers plan checkboxes mentally with this file; note mapping in `specs/001-delivery-platform/tasks.md` Notes (no duplicate conflicting plans)
- [ ] T067 Security pass: logs must not print passwords; redact in `DeliveryCli` and portal worker
- [ ] T068 Run full `specs/001-delivery-platform/quickstart.md` checklist and mark outcomes

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Start immediately
- **Foundational (Phase 2)**: Depends on Setup — **BLOCKS all user stories**
- **US1 (Phase 3)**: Depends on Foundational — **MVP**
- **US2 (Phase 4)**: Depends on US1 (needs New packager/runner)
- **US3 (Phase 5)**: Mostly depends on Foundational Excel reader; portal mapping after US4 controllers exist (T053)
- **US4 (Phase 6)**: Depends on US1 (and US2 for Update via API); constitution: portal after CLI New
- **Polish (Phase 7)**: After desired stories complete

### User Story Dependencies

- **US1 (P1)**: After Foundational — no dependency on US2–US4
- **US2 (P1)**: After US1 runner/packager
- **US3 (P2)**: Excel reject independent at CLI after Foundational; portal 400 after JobController
- **US4 (P2)**: After US1 CLI path proven

### Parallel Opportunities

- T003–T005 parallel in Setup
- T007–T012, T014–T015, T018–T019, T022 parallel within Foundational where marked [P]
- T027–T028 and T031–T032 parallel within US1 early
- T042 parallel with docs; T058–T059 parallel within US4
- US3 CLI reject tests can proceed while US2 store work continues (different files)

---

## Parallel Example: User Story 1

```bash
# After Foundational:
Task: "Implement AuthoringService in src/main/java/delivery/authoring/AuthoringService.java"
Task: "Add Freemarker templates under customer-framework-template/templates/"
Task: "Implement PageAccumulator in src/main/java/delivery/codegen/PageAccumulator.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup  
2. Phase 2 Foundational  
3. Phase 3 US1 (CLI New ZIP)  
4. **STOP and VALIDATE** using quickstart Phase C  
5. Demo to stakeholders before Update/portal  

### Incremental Delivery

1. Setup + Foundational → foundation ready  
2. US1 → CLI New ZIP demo (MVP!)  
3. US2 → Update demo  
4. US3 → harden reject UX  
5. US4 → thin portal  
6. Polish  

### Parallel Team Strategy

1. Together: Setup + Foundational  
2. Then: Dev A finishes US1; Dev B prepares store interfaces for US2; Dev C prepares portal skeleton only after US1 checkpoint  

---

## Notes

- [P] = different files, safe parallel
- Story labels map to spec user stories US1–US4
- Detailed micro-steps also documented in `docs/superpowers/plans/2026-08-04-testpilot-delivery-platform.md` — prefer this `tasks.md` as Spec Kit execution order
- Do not start implementation until product owner says **build**
- Commit after each task or logical group when commits are authorized
- T057 deviation: portal lives in the root Maven module (`delivery.portal.*` + Spring Boot plugin) instead of a separate `portal/pom.xml` multi-module — same APIs/UI, simpler for MVP
- Portal dry-run: `delivery.dry-run=true` (default) packages TODO stubs without Ollama/browser; set `false` when local AI is ready
