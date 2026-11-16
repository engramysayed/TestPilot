# Library add TC + Cursor/Keel pre-run review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** People can add a blank case on the project Test cases tab, and Execute/Automate can review the run’s cases with Cursor or Keel (JSON + Keel authoring rules), then Accept saves the library and starts the job.

**Architecture:** `GeneratedWorkbookService.addCaseFields` assigns the next `TC_` id and `saveFromCases`s (creates the workbook if missing). `applyReviewedCases` upserts reviewed ids and keeps other library rows. `AuthoringReviewService` already sends suite JSON; extend the system prompt and accept `keel` as an alias for `ollama`. Pre-run Accept writes the library then starts the job with `useGenerated` (no CSV-as-upload that leaves the library stale).

**Tech Stack:** Java 21, Spring MVC, TestNG, Thymeleaf `project-detail.html` / `execute.html` / `upload.html`, existing `AuthoringReviewService` / `CursorHealClient` / `LocalLlmClient`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-18-library-add-and-prerun-review-design.md`
- TDD: failing TestNG test before production code. Watch it fail.
- PowerShell: quote Maven tests: `mvn -pl keel-portal "-Dtest=ClassName"` (commas inside the quotes). Always `"-Dmaven.compiler.release=21"`. CrowdStrike: prefer `cmd.exe` / `.bat`, no obfuscated PowerShell.
- Author/committer `rsayed <rsayed@axisapp.com>`. No `Co-authored-by: Cursor` (use `git write-tree` / `git commit-tree` / `git reset --soft` if `git commit` adds one). Do not push. Do not commit `META-INF/`, `target/`, portal DB, `api-keys.local.bat`.
- No Claude / Anthropic models.
- Product credits stay ON. Do not add a new credit product. Review is not charged; Execute/Automate charge when the job starts.
- Do not change `OutcomeKind` quote-wins-first. Do not edit `api-keys.local.bat`. Do not log secret values.
- After Java/template edits the running jar is stale until rebuild + restart + Ctrl+F5.
- Site-agnostic: no AxisPay hostnames in production code.

## File map

| File | Role |
|------|------|
| `GeneratedWorkbookService` | `nextTcId`, `addCaseFields`, `applyReviewedCases` |
| `GeneratedWorkbookController` | `POST .../generated-workbook/cases` |
| `project-detail.html` | **Add test case** button → existing edit panel |
| `AuthoringReviewService` | Prompt rules; `keel` → `ollama` |
| `PreRunAuthoringReviewController` | `provider` + `model`; stop hardcoding Cursor |
| `execute.html` / `upload.html` | Radios Cursor \| Keel; Accept saves library then runs |
| `AuthoringReviewServiceTest`, `GeneratedWorkbookCaseUpdateTest`, API/MVC tests | TDD |
| `docs/PRODUCT.md`, `CHANGELOG.md` | User-facing |

---

### Task 1: Next TC id + addCaseFields

**Files:**
- Modify: `src/main/java/delivery/portal/service/GeneratedWorkbookService.java`
- Test: `src/test/java/delivery/portal/service/GeneratedWorkbookCaseUpdateTest.java` (add methods or new `GeneratedWorkbookAddCaseTest.java`)

**Interfaces:**
- Produces: `static String nextTcId(List<String> ids)` — empty → `TC_1`; `TC_01` present → `TC_02` (keep digit width); ignore non-`TC_<digits>` ids
- Produces: `Map<String, Object> addCaseFields(String projectId, Map<String, String> fields, String baseUrl, String author) throws Exception` — same field keys as `updateCaseFields`; assigns id; quality gate; `saveFromCases(..., "CASE_EDIT", author)`; returns describe + `csv` + `row`

- [ ] **Step 1: Write the failing tests**

```java
@Test
public void nextTcId_empty_isTc1() {
    Assert.assertEquals(GeneratedWorkbookService.nextTcId(List.of()), "TC_1");
}

@Test
public void nextTcId_padded_keepsWidth() {
    Assert.assertEquals(GeneratedWorkbookService.nextTcId(List.of("TC_01", "TC_02")), "TC_03");
}

@Test
public void addCaseFields_appendsNextId() throws Exception {
    GeneratedWorkbookService service = service();
    String projectId = "proj-add-case";
    service.saveFromCases(projectId, List.of(
            new ManualTestCase("TC_01", "Login", "", "1. Open login", "1. Shown", "P1", "", "", "", "")
    ), "GENERATE", "unit-test");
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("title", "Manual case");
    fields.put("steps", "1. Open home");
    fields.put("expectedResult", "1. Home is shown");
    fields.put("keelPath", "");
    Map<String, Object> out = service.addCaseFields(projectId, fields, "https://example.test", "editor@example.com");
    Assert.assertEquals(out.get("tcCount"), 2);
    @SuppressWarnings("unchecked")
    Map<String, Object> row = (Map<String, Object>) out.get("row");
    Assert.assertEquals(row.get("tcId"), "TC_02");
    Assert.assertEquals(row.get("title"), "Manual case");
    Assert.assertEquals(out.get("source"), "CASE_EDIT");
}

@Test
public void addCaseFields_emptyLibrary_createsWorkbook() throws Exception {
    GeneratedWorkbookService service = service();
    String projectId = "proj-add-empty";
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("title", "First");
    fields.put("steps", "1. Open home");
    fields.put("expectedResult", "1. Home is shown");
    Map<String, Object> out = service.addCaseFields(projectId, fields, "https://example.test", "editor@example.com");
    Assert.assertEquals(out.get("tcCount"), 1);
    @SuppressWarnings("unchecked")
    Map<String, Object> row = (Map<String, Object>) out.get("row");
    Assert.assertEquals(row.get("tcId"), "TC_1");
}
```

Reuse the existing `service()` helper pattern in `GeneratedWorkbookCaseUpdateTest` (temp `storeRoot` under `./target/...`).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=GeneratedWorkbookCaseUpdateTest,GeneratedWorkbookAddCaseTest" test`

Expected: FAIL (method missing or compile error)

- [ ] **Step 3: Implement `nextTcId` + `addCaseFields`**

```java
static String nextTcId(List<String> ids) {
    int max = 0;
    int width = 1;
    boolean any = false;
    if (ids != null) {
        Pattern p = Pattern.compile("^TC_(\\d+)$", Pattern.CASE_INSENSITIVE);
        for (String id : ids) {
            if (id == null) continue;
            Matcher m = p.matcher(id.trim());
            if (!m.matches()) continue;
            any = true;
            String digits = m.group(1);
            int n = Integer.parseInt(digits);
            if (n > max) max = n;
            if (digits.length() > width) width = digits.length();
        }
    }
    if (!any) {
        return "TC_1";
    }
    return "TC_" + String.format("%0" + width + "d", max + 1);
}

public Map<String, Object> addCaseFields(
        String projectId, Map<String, String> fields, String baseUrl, String author) throws Exception {
    if (fields == null) {
        throw new IllegalArgumentException("At least one field is required");
    }
    List<ManualTestCase> existing;
    try {
        existing = new ExcelTcReader().read(requireExcel(projectId));
    } catch (IllegalStateException e) {
        if (!"NO_GENERATED_WORKBOOK".equals(e.getMessage())) {
            throw e;
        }
        existing = List.of();
    }
    List<String> ids = new ArrayList<>();
    for (ManualTestCase tc : existing) {
        ids.add(tc.tcId());
    }
    String tcId = nextTcId(ids);
    ManualTestCase blank = new ManualTestCase(tcId, "", "", "", "", "", "", "", "", "", "");
    ManualTestCase created = mergeCaseFields(blank, fields);
    List<ManualTestCase> patched = new ArrayList<>(existing);
    patched.add(created);
    List<ManualTestCase> repaired = TcImportRepair.repairCases(patched);
    List<String> gateErrors = GenerateQualityGate.validate(repaired, baseUrl);
    if (!gateErrors.isEmpty()) {
        throw GenerateQualityGate.failureException(gateErrors);
    }
    String sourceRef = author != null && !author.isBlank() ? author.trim() : "";
    saveFromCases(projectId, repaired, "CASE_EDIT", sourceRef, null, null);
    // return describe + csv + row (same shape as updateCaseFields)
}
```

If `requireExcel` throws before any file exists, catch `NO_GENERATED_WORKBOOK` as above. Title/steps empty may fail the quality gate — tests use a real step + expected. For the UI, require title + one step like the edit form (`lib-edit-title` required).

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Commit** as rsayed, no Cursor trailer. Message: `Add next-id library case create on the workbook.`

---

### Task 2: POST add-case API

**Files:**
- Modify: `src/main/java/delivery/portal/api/GeneratedWorkbookController.java`
- Test: `src/test/java/delivery/portal/api/GeneratedWorkbookCaseUpdateApiTest.java` (add `addCase_*` methods)

**Interfaces:**
- Consumes: `addCaseFields`
- Produces: `POST /api/projects/{projectId}/generated-workbook/cases` JSON body same as `UpdateCaseFieldsRequest` (no tcId). 200 describe + `row`. 404 unknown project. Same operable check as PUT.

- [ ] **Step 1: Failing API test** (copy auth/setup from `updateCase_success`)

```java
@Test
public void addCase_success() throws Exception {
    mockMvc.perform(post("/api/projects/" + projectId + "/generated-workbook/cases")
                    .with(httpBasic(AUTH_USER, AUTH_PASS))
                    .header("X-Keel-Requested-With", "Keel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"title":"Manual","steps":"1. Open home","expectedResult":"1. Home is shown"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.row.tcId").value("TC_02")); // or TC_2 depending on seed ids
}
```

Read the existing test’s seed `tcId`s and assert the **next** id.

- [ ] **Step 2: Run — expect FAIL (404/405)**

- [ ] **Step 3: Map POST** next to the PUT case method. Reuse `UpdateCaseFieldsRequest`. Call `workbooks.addCaseFields(..., currentUser.requireUser().getEmail())`. Same error mapping as PUT (quality gate, NO_GENERATED_WORKBOOK should **not** fire after Task 1 creates the file).

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit** `Expose POST to add a library test case.`

---

### Task 3: Add test case on the project tab

**Files:**
- Modify: `src/main/resources/templates/project-detail.html` (toolbar ~625–635, edit open ~2550–2620)
- Test: existing project-detail MVC test if present; else `src/test/java/delivery/portal/web/ProjectDetailMvcTest.java` or extend the test that already fetches `/projects/{id}`

**Interfaces:**
- Consumes: POST from Task 2
- Produces: button `id="lib-add-btn"` visible even when `lib-empty` is shown

- [ ] **Step 1: Failing MVC assert**

```java
Assert.assertTrue(body.contains("id=\"lib-add-btn\""), "Add test case missing");
Assert.assertTrue(body.contains("Add test case"), "Add test case label missing");
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: UI**

Toolbar: `<button type="button" class="button" id="lib-add-btn">Add test case</button>` before Refresh.

Click handler:
- `libEditingId = null` (new)
- Show `#lib-edit-panel`, set `#lib-edit-id` to `(new)` until save
- Clear title; one empty align row; KeelPath `""`
- On submit: if new, `POST .../generated-workbook/cases` with the same JSON as edit; else existing PUT
- After 200: `loadLibrary()`, open the new `row.tcId` or just refresh list
- Keep `#lib-empty` copy, but **do not hide** Add (people can create the first case)

- [ ] **Step 4: MVC test PASS**

- [ ] **Step 5: Commit** `Add a blank library case from the project Test cases tab.`

---

### Task 4: Review prompt + `keel` provider

**Files:**
- Modify: `src/main/java/delivery/portal/service/AuthoringReviewService.java`
- Test: `src/test/java/delivery/portal/service/AuthoringReviewServiceTest.java`

**Interfaces:**
- Consumes: existing `reviewCases(..., provider, model, ...)`
- Produces: `provider` `keel` normalized to `ollama` in the LLM port call; `result.provider` may stay `keel` for the UI. System prompt includes OTP-accepted and quote-must-match-screen rules.

- [ ] **Step 1: Failing tests**

```java
@Test
public void review_keel_usesOllamaPort() throws Exception {
    Fixture fixture = fixture(List.of(originalCase()), validReviewJson());
    Map<String, Object> result = fixture.service.review(
            PROJECT_ID, OWNER_ID, "keel", "gemma4:e2b", "", "");
    Assert.assertEquals(fixture.lastProvider, "ollama");
    Assert.assertEquals(result.get("provider"), "keel");
}

@Test
public void review_systemPrompt_teachesExecuteRules() throws Exception {
    Fixture fixture = fixture(List.of(originalCase()), validReviewJson());
    fixture.service.review(PROJECT_ID, OWNER_ID, "ollama", "gemma4:e2b", "", "");
    Assert.assertTrue(fixture.lastSystem.contains("OTP is accepted"));
    Assert.assertTrue(fixture.lastSystem.contains("visible-text"));
    Assert.assertTrue(fixture.lastSystem.toLowerCase().contains("masked"));
}
```

Change `review_unknownProvider_fails` expected message to include `keel`.

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implementation**

- Allow `cursor`, `ollama`, `keel`. If `keel`, set port provider to `ollama` (needs model). Put `provider` in the result as the **requested** name (`keel` or `cursor` or `ollama`).
- Append to `SYSTEM_PROMPT` (keep existing JSON contract):

```
Quoted phrases are visible-text waits: the string MUST match the screen (Upload Transaction List is not Upload Transactions List).
Outcome "and" may split extra checks; "expands and sub-menu items are shown" stays one check.
After a fill, prefer "{Field} is accepted" or "OTP is accepted". Never assert typed digits or password on a masked field (do not keep filled with '245345' as visible text).
Keep "{Field} is shown", "{Button} is clicked", and page-shown wording.
Do not invent large extra suites. Preserve tcId and KeelPath.
Flag leftover Expected with no matching step.
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit** `Teach pre-run review Keel execute wording; allow Keel provider.`

---

### Task 5: Pre-run API takes Cursor or Keel

**Files:**
- Modify: `src/main/java/delivery/portal/api/PreRunAuthoringReviewController.java` (today hardcodes `"cursor"`)
- Test: `src/test/java/delivery/portal/api/WorkspaceRoleApiTest.java` pre-run section **or** new `PreRunAuthoringReviewApiTest.java`

**Interfaces:**
- Consumes: Task 4 `reviewCases`
- Produces: multipart/form fields `provider` (`cursor`|`keel`|`ollama`, default `keel`), `model` (optional; if Keel/ollama and blank, controller passes `props.getGenerateModel()`)

- [ ] **Step 1: Failing test** — POST with `provider=keel` succeeds against a stubbed review **or** assert controller no longer always logs `provider=cursor` if easier: API test that missing Cursor key + `provider=cursor` returns 503 `PROVIDER_UNAVAILABLE` (use a project fixture). If Cursor key is present in the test JVM, skip that assertion and instead unit-test a package helper `normalizeProvider`.

Prefer a focused test on `AuthoringReviewService` already done; for the controller, add:

```java
// In PreRunAuthoringReviewApiTest or WorkspaceRoleApiTest:
mockMvc.perform(multipart("/api/projects/" + projectId + "/pre-run-authoring-review")
        .param("useGenerated", "true")
        .param("provider", "keel")
        .param("model", "gemma4:e2b")
        .with(httpBasic(...))
        .header("X-Keel-Requested-With", "Keel"))
```

If this hits live Ollama, **do not**. Inject/mock at service level like other API tests, or set `delivery.llm-base-url=http://127.0.0.1:9` and expect 503 for keel (unreachable) vs proving the param is read. **Assert** the error is not “provider must be cursor or ollama”.

Better: extract `String provider = Optional.ofNullable(providerParam).orElse("keel")` and add a TestNG test on a small package-visible `PreRunAuthoringReviewController.normalizeProvider(String)` if the full API is too heavy. Prefer wiring the param and a MockMvc test with a `@MockBean AuthoringReviewService` only if the suite already uses that. Otherwise: MockMvc + unreachable Ollama (`127.0.0.1:9`) + `provider=keel` → 503 `PROVIDER_UNAVAILABLE` (proves keel is accepted). `provider=openai` → 400.

- [ ] **Step 2: Run — expect FAIL** (`provider=openai` might already 400 via service; `keel` currently ignored)

- [ ] **Step 3: Read `provider` and `model` request params. Default provider `keel`. Pass them into `reviewCases`. Log the actual provider. Cursor missing key: keep 503 `PROVIDER_UNAVAILABLE`.**

- [ ] **Step 4: PASS**

- [ ] **Step 5: Commit** `Let Execute pre-run review choose Cursor or Keel.`

---

### Task 6: Accept writes the library

**Files:**
- Modify: `GeneratedWorkbookService` + controller `POST .../generated-workbook/apply-review`
- Test: `GeneratedWorkbookAddCaseTest` or CaseUpdate test: `applyReviewedCases_replacesMatchingKeepsOthers`
- Frontend uses this **before** starting the job (Task 7)

**Interfaces:**
- Produces: `Map<String, Object> applyReviewedCases(String projectId, List<ManualTestCase> reviewed, String author, String baseUrl)`
  - Load existing (empty → just reviewed)
  - For each reviewed `tcId`: replace if present, else append
  - Keep library cases whose ids are **not** in the reviewed set
  - Quality gate whole suite; `saveFromCases(..., "CASE_EDIT", author)`
- API: `POST /api/projects/{id}/generated-workbook/apply-review` body `{ "cases": [ {tcId,title,steps,...} ] }` using the same row maps the review already returns. 200 describe.

- [ ] **Step 1: Failing test**

```java
@Test
public void applyReviewedCases_upsertsAndKeepsOthers() throws Exception {
    GeneratedWorkbookService service = service();
    String projectId = "proj-apply-review";
    service.saveFromCases(projectId, List.of(
            new ManualTestCase("TC_01", "A", "", "1. Open a", "1. A is shown", "P1", "", "", "", ""),
            new ManualTestCase("TC_02", "B", "", "1. Open b", "1. B is shown", "P1", "", "", "", "")
    ), "GENERATE", "unit-test");
    ManualTestCase edited = new ManualTestCase("TC_01", "A", "", "1. Open a",
            "1. A is accepted", "P1", "", "", "", "");
    service.applyReviewedCases(projectId, List.of(edited), "editor@example.com", "https://example.test");
    List<ManualTestCase> all = new ExcelTcReader().read(service.requireExcel(projectId));
    Assert.assertEquals(all.size(), 2);
    Assert.assertTrue(all.stream().anyMatch(tc ->
            "TC_01".equals(tc.tcId()) && tc.expectedResult().contains("accepted")));
    Assert.assertTrue(all.stream().anyMatch(tc -> "TC_02".equals(tc.tcId())));
}
```

- [ ] **Step 2: FAIL**

- [ ] **Step 3: Implement apply + POST.** Map JSON rows with existing `GeneratedWorkbookService` row parsing if present (`toRowMap` inverse). If none, build `ManualTestCase` from map keys: `tcId`, `title`, `preconditions`, `steps`, `expectedResult`, `priority`, `tags`, `visualAssertion`, `testData`, `keelPath`, `callBefore`.

- [ ] **Step 4: PASS**

- [ ] **Step 5: Commit** `Persist accepted pre-run review cases into the library.`

---

### Task 7: Execute and Automate UI

**Files:**
- Modify: `src/main/resources/templates/execute.html` (~147–176, ~390–540, submit ~1856)
- Modify: `src/main/resources/templates/upload.html` (same checkbox/panel)
- Test: `src/test/java/delivery/portal/web/ExecuteMvcTest.java`, `AutomateShellMvcTest.java`

**Interfaces:**
- Consumes: Tasks 5–6
- Produces: radios; Accept calls apply-review then existing Start with **library** (`useGenerated`), not CSV blob

- [ ] **Step 1: Failing MVC**

```java
Assert.assertTrue(body.contains("Review cases before run"));
Assert.assertTrue(body.contains("id=\"pre-run-review-provider-keel\"")
        || body.contains("value=\"keel\""));
Assert.assertTrue(body.contains("/generated-workbook/apply-review")
        || body.contains("apply-review"));
Assert.assertFalse(body.contains("propose only"));
```

- [ ] **Step 2: FAIL**

- [ ] **Step 3: UI behavior**

- Label: **Review cases before run**. Hint: Findings first. Accept saves the library and starts this run.
- Radios `name="pre-run-review-provider"` values `keel` | `cursor`. Default `sessionStorage` key `keel.preRunReview.provider`, else `keel`.
- `buildPreRunReviewInput`: append `provider`; if keel, append `model` from `/api/generate/models` default **or** omit and let the server use `props.getGenerateModel()`.
- Status text: `Keel is reviewing…` / `Cursor is reviewing…`
- Keep `preRunReviewedCsv` for display; also keep `preRunReviewedCases` (the `data.cases` array).
- Accept click:
  1. `POST apply-review` with `{ cases: preRunReviewedCases }`
  2. On 200, `preRunReviewApproved = true` and **clear the excel file input** so submit uses library
  3. Trigger the existing Start handler with `useGenerated=true`
- If apply-review fails, do not start. Show `data.message`.
- Unchecked review: Start as today (no apply-review).

Mirror on Automate (`upload.html`) — same ids if they are duplicated per page (they are separate templates; duplicate the same JS pattern already there).

- [ ] **Step 4: MVC PASS**

- [ ] **Step 5: Commit** `Save reviewed cases to the library before Execute and Automate start.`

---

### Task 8: Product docs

**Files:**
- Modify: `docs/PRODUCT.md` (library add; Execute/Automate review)
- Modify: `CHANGELOG.md` dated `2026-10-18`
- Modify: spec Status → Implemented (after code exists)
- Modify: `docs/ENGINEERING_HANDOFF.md` one row
- `docs/handoff/` short note + `D:\priv\testpilot\handoffs\RESUME-2026-10-15.md`

- [ ] **Step 1: Write the dated changelog and PRODUCT bullets** (no test). PRODUCT:

- Test cases: **Add test case** opens the editor; next id; Save.
- Execute: **Review cases before run** (Cursor or Keel). Accept **saves the library** and starts the run. Quotes still mean visible text at runtime.

- [ ] **Step 2: Commit** `Document library add and Cursor-or-Keel pre-run review.`

---

## Spec coverage (self-review)

| Spec item | Task |
|-----------|------|
| Add button + existing editor + next id + blank KeelPath | 1–3 |
| Empty library creates workbook | 1 |
| JSON suite + Keel rules prompt | 4 |
| Cursor \| Keel radios, default Keel | 5, 7 |
| Missing Cursor key / Ollama down → no run | 5, 7 |
| Accept writes library then run | 6–7 |
| Keep other library cases | 6 |
| Automate same | 7 |
| No OutcomeKind change, no new credits | constraints |
| Generate shares prompt | 4 (same SYSTEM_PROMPT) |

## Out of plan

Excel-on-disk rewrite, CSS reskin, Bug Hunter, auto-accept, `OutcomeKind` quote order.
