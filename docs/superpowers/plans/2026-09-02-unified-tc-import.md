# Unified TC Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give users one reliable door from external AI (JSON/CSV paste) or portal Generate into a project workbook that Automate/Execute can run, with shared repair + quality gate and fixed copy-prompts.

**Architecture:** Add `TcImportService` that strips fences → detects JSON/CSV → parses → safe-repairs → runs `GenerateQualityGate` → saves via `GeneratedWorkbookService`. Wire Paste API + Generate UI, Compare/Generate saves, and Automate/Execute Excel upload through the same gate. Rewrite copy-prompts to prefer JSON and instruct “paste into Keel Import”.

**Tech Stack:** Java 21, Spring Boot, TestNG, existing `GeneratedTcJsonParser` / `GeneratedTcCsvParser` / `GenerateQualityGate` / `GenerateAuthoringRules`, Thymeleaf `generate.html`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-02-unified-tc-import-design.md`
- Do not commit unless the user explicitly asks.
- Blank KeelPath stays eligible on **both** Automate and Execute (`KeelPathCaseFilter` semantics unchanged).
- Auto-repair is **safe only** (fences, literal `\n`, trim, valid KeelPath casing); never invent field labels or assert text.
- Authoring failures hard-block after repair (no soft save).
- Prefer extending existing parsers/gate over new frameworks.
- Windows PowerShell: use `;` not `&&` in shell examples.

---

## File map

| File | Responsibility |
|------|----------------|
| `src/main/java/delivery/excel/TcImportRepair.java` | Safe text/case repairs before gate |
| `src/main/java/delivery/portal/service/TcImportService.java` | Detect → parse → repair → gate → save → payload |
| `src/main/java/delivery/portal/api/GenerateTcController.java` | `POST .../generate/import` |
| `src/main/java/delivery/portal/service/TcGenerateService.java` | Route `saveCompared` (and any sync save) through import service where it persists workbook |
| `src/main/java/delivery/portal/api/JobController.java` + Execute run create | Gate Excel cases before queue |
| `src/main/resources/templates/generate.html` | Paste/Import UI + copy-prompt steps |
| `src/main/resources/static/prompts/keel-tc-generate-from-stories-to-json.txt` | Prefer JSON + Import instructions |
| `src/main/resources/static/prompts/keel-tc-generate-from-stories-to-csv.txt` | Quoted multiline + Import instructions |
| Tests under `src/test/java/delivery/...` | Repair, service, API, MVC, prompts, KeelPath blank, Excel gate |

---

### Task 1: Safe import repair (TDD)

**Files:**
- Create: `src/main/java/delivery/excel/TcImportRepair.java`
- Create: `src/test/java/delivery/excel/TcImportRepairTest.java`
- Reuse: `ExcelStepText.normalizeMultiline` if present

**Interfaces:**
- Produces: `public final class TcImportRepair` with:
  - `static String stripMarkdownFences(String raw)`
  - `static List<ManualTestCase> repairCases(List<ManualTestCase> cases)`
  - `static String repairMultilineField(String value)` — literal `\n` → real newline; optionally split smashed `" 2. "` / `"\n2. "` numbered steps when a line contains multiple `N. ` tokens

- [ ] **Step 1: Write failing tests**

```java
@Test
public void stripMarkdownFences_removesJsonFence() {
    String raw = "```json\n{\"testCases\":[]}\n```";
    Assert.assertTrue(TcImportRepair.stripMarkdownFences(raw).trim().startsWith("{"));
}

@Test
public void repairMultilineField_expandsLiteralBackslashN() {
    String in = "1. Open login\\n2. Enter in the Email or phone field";
    String out = TcImportRepair.repairMultilineField(in);
    Assert.assertTrue(out.contains("\n"));
    Assert.assertFalse(out.contains("\\n"));
}

@Test
public void repairCases_expandsLiteralBackslashNInSteps() {
    ManualTestCase tc = new ManualTestCase(
            "TC_01", "t", "", "1. A\\n2. B", "1. ok", "", "", "", "", "");
    List<ManualTestCase> out = TcImportRepair.repairCases(List.of(tc));
    Assert.assertTrue(out.get(0).steps().contains("\n"));
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```powershell
cd D:\priv\testpilot\TestPilot; mvn -q test "-Dtest=TcImportRepairTest"
```

- [ ] **Step 3: Implement `TcImportRepair`**

Minimal: fence strip (regex for optional language tag), replace `\\n` with `\n` in steps/expected/testData fields via `repairCases`, trim tcId/keelPath, uppercase keelPath only when `KeelPath.parse` would succeed after trim.

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Do not commit** (unless user asked)

---

### Task 2: `TcImportService` core (TDD)

**Files:**
- Create: `src/main/java/delivery/portal/service/TcImportService.java`
- Create: `src/test/java/delivery/portal/service/TcImportServiceTest.java`
- Read: `GeneratedWorkbookService`, `GenerateQualityGate`, `GeneratedTcJsonParser`, `GeneratedTcCsvParser`, `PortalStore`

**Interfaces:**
- Consumes: `TcImportRepair`, parsers, gate, `GeneratedWorkbookService`, `PortalStore`
- Produces:

```java
public record ImportRequest(String raw, String formatHint) {} // formatHint: auto|json|csv|null

public Map<String, Object> importRaw(
    String projectId, Long ownerUserId, String raw, String formatHint, String source)
    throws Exception;

public Map<String, Object> importCases(
    String projectId, Long ownerUserId, List<ManualTestCase> cases, String source, String model)
    throws Exception;
```

Payload must include at least: `projectId`, `rows`, `csv`, `counts`, `keelPathCounts` (same spirit as `TcGenerateService.toGeneratePayload`).

Detection: after fence strip, if trimmed starts with `{` → JSON; else CSV. If `formatHint` is `json`/`csv`, force that path.

On gate failure throw `GenerateQualityGate.failureException(errors)` (existing helper).

- [ ] **Step 1: Write failing tests** using stub/fake workbook + in-memory or Spring-less construction where possible. Prefer unit-style with real parsers:

```java
@Test
public void importRaw_jsonGolden_savesAndReturnsRows() throws Exception {
    // load facebook-login-negative-golden.json from test resources
    // mock GeneratedWorkbookService to capture saveFromCases
    // assert rows.size() >= 8 and save called with source PASTE_IMPORT
}

@Test
public void importRaw_badPhoneField_throwsQualityGate() throws Exception {
    // JSON/CSV with Enter in the Phone field + email-or-phone context
    // expect GenerateQualityGate.isQualityGateFailure
}
```

- [ ] **Step 2: Run — expect FAIL**

```powershell
mvn -q test "-Dtest=TcImportServiceTest"
```

- [ ] **Step 3: Implement service**

Wire constructor deps like other portal services. Call `workbooks.saveFromCases(projectId, cases, source, projectId, model)`.

- [ ] **Step 4: Run — expect PASS**

---

### Task 3: Import API endpoint

**Files:**
- Modify: `src/main/java/delivery/portal/api/GenerateTcController.java`
- Create: `src/test/java/delivery/portal/api/GenerateImportApiTest.java`

**Interfaces:**
- Consumes: `TcImportService.importRaw`
- HTTP:

```http
POST /api/projects/{projectId}/generate/import
{ "raw": "...", "format": "auto" }
```

Map exceptions like existing generate endpoints (`QUALITY_GATE` → 400, parse → 422, unknown project → 404).

- [ ] **Step 1: Write API test** (MockMvc + httpBasic, mock `TcImportService` or use full stack with dry-run store — follow `CompareAsyncApiTest` / `GenerateAsyncApiTest` patterns)

```java
@Test
public void importJson_returnsOkPayload() throws Exception { ... }

@Test
public void importEmpty_returnsBadRequest() throws Exception { ... }
```

- [ ] **Step 2: Run — expect FAIL**

```powershell
mvn -q test "-Dtest=GenerateImportApiTest"
```

- [ ] **Step 3: Add controller method + request record `ImportGenerateRequest(String raw, String format)`**

- [ ] **Step 4: Run — expect PASS**

---

### Task 4: Generate UI — Paste/Import panel

**Files:**
- Modify: `src/main/resources/templates/generate.html`
- Modify: `src/test/java/delivery/portal/web/GenerateMvcTest.java`
- Optionally: `src/main/resources/static/css/portal.css` (minimal)

**UI requirements:**
- Visible section **Paste from external AI** with textarea `#import-raw` and button `#import-btn`
- JS `runImport()` → `POST /api/projects/{id}/generate/import` with `{ raw, format: "auto" }`
- Success: reuse `showResult(body)`; status “Workbook saved — use Automate/Execute with generated workbook”
- Failure: show message from body
- Update copy-prompt `<ol>` steps to say paste into **Import into project**, not Excel-only

- [ ] **Step 1: Extend `GenerateMvcTest`**

```java
Assert.assertTrue(body.contains("id=\"import-raw\""));
Assert.assertTrue(body.contains("id=\"import-btn\""));
Assert.assertTrue(body.contains("/generate/import"));
Assert.assertTrue(body.contains("Import into project") || body.contains("import-btn"));
Assert.assertFalse(mainShellOnlyClaimsExcelThenAutomateAsSolePath); // assert new step text present
```

- [ ] **Step 2: Run MVC test — expect FAIL**

- [ ] **Step 3: Implement HTML/JS**

- [ ] **Step 4: Run — expect PASS**

```powershell
mvn -q test "-Dtest=GenerateMvcTest"
```

---

### Task 5: Rewrite copy-prompts

**Files:**
- Modify: `src/main/resources/static/prompts/keel-tc-generate-from-stories-to-json.txt`
- Modify: `src/main/resources/static/prompts/keel-tc-generate-from-stories-to-csv.txt`
- Modify: `src/test/java/delivery/portal/web/GenerateJsonPromptResourceTest.java`
- Modify: `src/test/java/delivery/portal/web/GeneratePromptResourceTest.java`

**Must assert / include:**
- Prefer JSON; paste result into Keel **Import into project**
- Blank KeelPath allowed (= both surfaces in Keel)
- Email/Mobile → Email or phone field
- CSV: multiline fields must use RFC4180 quotes; real line breaks inside quotes
- No sole instruction “only upload via Excel Automate” without Import

- [ ] **Step 1: Add failing prompt assertions**

- [ ] **Step 2: Edit both prompt files**

- [ ] **Step 3: Run prompt tests — PASS**

```powershell
mvn -q test "-Dtest=GenerateJsonPromptResourceTest,GeneratePromptResourceTest"
```

---

### Task 6: Route Compare save through import gate

**Files:**
- Modify: `src/main/java/delivery/portal/service/TcGenerateService.java` (`saveCompared`)
- Modify or add: test that bad CSV on saveCompared fails gate (extend existing compare/save test if any)

**Behavior:** `saveCompared` parses CSV → `TcImportRepair.repairCases` → gate → save (or call `TcImportService.importCases`). Do not bypass gate.

- [ ] **Step 1: Failing test — saveCompared with Phone-field CSV throws QUALITY_GATE**

- [ ] **Step 2: Implement wiring**

- [ ] **Step 3: Tests PASS**

---

### Task 7: Gate Excel upload on Automate + Execute job create

**Files:**
- Modify: `src/main/java/delivery/portal/api/JobController.java` (Automate `/jobs`)
- Modify: Execute run controller that accepts excel / useGenerated (find `ExecuteRunController` or equivalent)
- Create: `src/test/java/delivery/portal/api/ExcelUploadQualityGateApiTest.java` (or extend existing job API test)

**Behavior:** After `ExcelTcReader.read`, run `TcImportRepair.repairCases` + `GenerateQualityGate.validate(cases, project.baseUrl)`. On errors return 400 with `QUALITY_GATE` detail **before** creating job. Blank KeelPath rows still allowed.

For `useGenerated=true`, skip re-gate if workbook was already gated on import/generate (optional optimization); still safe to re-gate.

- [ ] **Step 1: Write API test with temp xlsx containing bad Phone-field TC → expect 400 QUALITY_GATE**

- [ ] **Step 2: Implement gate in both create endpoints**

- [ ] **Step 3: Tests PASS**

---

### Task 8: KeelPath blank regression + docs touch

**Files:**
- Confirm/extend: `src/test/java/delivery/excel/KeelPathCaseFilterTest.java`
- Modify: `README.md` Part C or short note pointing to unified import (user previously wanted README handoff — add a subsection under Part C: Unified Import)

- [ ] **Step 1: Assert blank path eligible on AUTOMATE and EXECUTE** (add if missing)

```java
@Test
public void blankKeelPath_eligibleOnBothSurfaces() {
    ManualTestCase blank = new ManualTestCase("TC_01", "t", "", "1. x", "1. y", "", "", "", "", "");
    Assert.assertEquals(KeelPathCaseFilter.forSurface(List.of(blank), Surface.AUTOMATE).size(), 1);
    Assert.assertEquals(KeelPathCaseFilter.forSurface(List.of(blank), Surface.EXECUTE).size(), 1);
}
```

- [ ] **Step 2: README short subsection** — Paste Import path + blank KeelPath matrix (do not delete Part C history)

- [ ] **Step 3: Run KeelPath tests PASS**

---

### Task 9: Full verification bundle + manual smoke checklist

- [ ] **Step 1: Run automated bundle**

```powershell
cd D:\priv\testpilot\TestPilot
mvn -q test "-Dtest=TcImportRepairTest,TcImportServiceTest,GenerateImportApiTest,GenerateMvcTest,GenerateJsonPromptResourceTest,GeneratePromptResourceTest,KeelPathCaseFilterTest,ExcelUploadQualityGateApiTest,FacebookLoginNegativeGenerateTest,GenerateAuthoringRulesTest,GenerateQualityGateTest"
```

Expected: all green.

- [ ] **Step 2: Manual smoke (portal running)**

1. Restart portal; hard-refresh `/generate`.
2. Paste Cursor-style JSON or repaired CSV into **Import**.
3. Confirm preview + workbook saved.
4. Execute → **Use latest generated workbook** → job queues.
5. Paste known-bad Phone-field snippet → Import shows QUALITY_GATE (no save).
6. Optional: copy-prompt → Cursor → paste back into Import.

- [ ] **Step 3: Record smoke results** in `docs/superpowers/plans/2026-09-02-unified-tc-import-notes.md`

---

## Spec coverage check

| Spec requirement | Task |
|------------------|------|
| Shared import service | 2 |
| Safe repair | 1 |
| Paste API | 3 |
| Generate UI Import | 4 |
| Prompt rewrite / Import instructions | 5 |
| Compare save gated | 6 |
| Excel upload gated | 7 |
| Blank KeelPath both surfaces | 8 |
| Automated + manual verify | 9 |

## Placeholder scan

No TBD/TODO left in tasks; signatures named; commands concrete.

---

## Execution handoff

**Plan complete and saved to `docs/superpowers/plans/2026-09-02-unified-tc-import.md`.**

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute in this session with checkpoints  

**Which approach?**
