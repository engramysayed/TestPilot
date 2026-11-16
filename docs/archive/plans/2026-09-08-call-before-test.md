# Call-before Test + UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ordered Call-before TC references (runtime expand on Automate/Execute), and polish Projects + TCs library chrome (Delete all, Select all, toolbar alignment).

**Architecture:** Persist `callBefore` as an optional workbook column on `ManualTestCase`. Pure expander `CallBeforeExpander` turns selected leaf IDs into an ordered run list (recursive, cycle-safe, re-run prerequisites before each leaf). `WorkbookJobMaterializer` loads the full case map then expands. UI: chip picker under Preconditions; Projects page danger Delete all; library Select all in table header.

**Tech Stack:** Java 21, Spring MVC, Apache POI Excel, TestNG, Thymeleaf + vanilla JS, existing portal CSS.

## Global Constraints

- No Claude / Anthropic models.
- Expand at job materialization only — do not rewrite leaf steps in the library.
- Recursive expansion; cycles fail closed.
- Before each selected leaf, re-run that leaf’s call-before chain (no cross-leaf dedupe of Login).
- Within one leaf’s chain, dedupe repeated IDs (keep first).
- `CallBefore` column optional on read; always written on save.
- Do not commit unless the user explicitly asks (plan steps may stage files; skip `git commit` unless requested).

---

## File map

| File | Responsibility |
|------|----------------|
| `delivery/excel/ManualTestCase.java` | Add `callBefore` component + constructors |
| `delivery/excel/CallBefore.java` | Parse/normalize comma IDs; validate refs |
| `delivery/job/CallBeforeExpander.java` | Expand selected leaves → ordered run list |
| `delivery/excel/ExcelTcReader.java` | Read `CALLBEFORE` |
| `delivery/excel/ManualTcExcelWriter.java` | Write `CallBefore` column |
| `delivery/excel/GeneratedTcCsvParser.java` | CSV round-trip |
| `delivery/excel/GeneratedTcJsonParser.java` | JSON `callBefore` |
| `delivery/portal/service/WorkbookJobMaterializer.java` | Merge full map + expand |
| `delivery/portal/service/GeneratedWorkbookService.java` | updateCaseFields / toRowMap / mergeCaseFields |
| `delivery/portal/api/GeneratedWorkbookController.java` | Accept `callBefore` on PUT |
| `templates/projects.html` + CSS | Remove Open Automate; Delete all placement/color |
| `templates/project-detail.html` + CSS | Toolbar; select-all in header; call-before picker |
| `templates/generate.html` | Call-before in TC edit modal |

---

### Task 1: CallBefore parse + Expander (TDD)

**Files:**
- Create: `src/main/java/delivery/excel/CallBefore.java`
- Create: `src/main/java/delivery/job/CallBeforeExpander.java`
- Create: `src/test/java/delivery/job/CallBeforeExpanderTest.java`
- Create: `src/test/java/delivery/excel/CallBeforeTest.java`

**Interfaces:**
- Produces:
  - `CallBefore.parse(String raw) → List<String>` (trim, split on `,`, drop blanks, preserve order, dedupe consecutive? **No** — preserve order, allow intentional repeats only if operator typed them; expander within-leaf dedupes)
  - `CallBefore.format(List<String> ids) → String`
  - `CallBeforeExpander.expand(List<ManualTestCase> allCases, List<String> selectedLeafIds) → List<ManualTestCase>`
  - Throws `IllegalArgumentException` with message starting `CALL_BEFORE_CYCLE:` or `UNKNOWN_CALL_BEFORE:` or `UNKNOWN_TC:`

- [ ] **Step 1: Write failing expander tests**

```java
package delivery.job;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.List;

public class CallBeforeExpanderTest {
    private static ManualTestCase tc(String id, String callBefore) {
        return new ManualTestCase(id, id, "", "1. Go", "1. Ok", "P1", "", "", "", "AUTOMATE", callBefore);
    }

    @Test
    public void expandsRecursiveBeforeEachLeaf() {
        List<ManualTestCase> all = List.of(
                tc("TC_00", ""),
                tc("TC_01", "TC_00"),
                tc("TC_05", "TC_01"),
                tc("TC_06", "TC_01"));
        List<ManualTestCase> out = CallBeforeExpander.expand(all, List.of("TC_05", "TC_06"));
        Assert.assertEquals(out.stream().map(ManualTestCase::tcId).toList(),
                List.of("TC_00", "TC_01", "TC_05", "TC_00", "TC_01", "TC_06"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void cycleFails() {
        List<ManualTestCase> all = List.of(tc("TC_A", "TC_B"), tc("TC_B", "TC_A"));
        CallBeforeExpander.expand(all, List.of("TC_A"));
    }

    @Test
    public void withinLeafDedupesOverlappingNest() {
        // TC_06 → TC_01,TC_00 and TC_01 → TC_00  ⇒ once TC_00 then TC_01 then TC_06
        List<ManualTestCase> all = List.of(
                tc("TC_00", ""),
                tc("TC_01", "TC_00"),
                tc("TC_06", "TC_01,TC_00"));
        List<ManualTestCase> out = CallBeforeExpander.expand(all, List.of("TC_06"));
        Assert.assertEquals(out.stream().map(ManualTestCase::tcId).toList(),
                List.of("TC_00", "TC_01", "TC_06"));
    }
}
```

- [ ] **Step 2: Run tests — expect compile/fail**

```powershell
cd D:\priv\testpilot\TestPilot
mvn -q "-Dtest=CallBeforeExpanderTest" test
```

Expected: FAIL (class or constructor missing).

- [ ] **Step 3: Add `callBefore` to `ManualTestCase`**

Extend the canonical constructor with `String callBefore` (default `""` in compact constructors). Update every existing `new ManualTestCase(...)` call site that uses the full arity — prefer adding overload:

```java
public record ManualTestCase(
        String tcId, String title, String preconditions, String steps, String expectedResult,
        String priority, String tags, String visualAssertion, String testData, String keelPath,
        String callBefore) {
    public ManualTestCase {
        visualAssertion = visualAssertion == null ? "" : visualAssertion;
        testData = testData == null ? "" : testData;
        keelPath = keelPath == null ? "" : keelPath;
        callBefore = callBefore == null ? "" : callBefore.trim();
    }
    // Keep old 10-arg ctor delegating callBefore=""
}
```

Fix compile breaks across the repo (writers/readers temporarily pass `""`).

- [ ] **Step 4: Implement `CallBefore` + `CallBeforeExpander`**

```java
public final class CallBeforeExpander {
    public static List<ManualTestCase> expand(List<ManualTestCase> allCases, List<String> selectedLeafIds) {
        Map<String, ManualTestCase> byId = index(allCases);
        List<ManualTestCase> out = new ArrayList<>();
        List<String> leaves = selectedLeafIds == null ? List.of() : selectedLeafIds;
        for (String leaf : leaves) {
            String id = leaf == null ? "" : leaf.trim();
            if (id.isEmpty()) continue;
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException("UNKNOWN_TC: " + id);
            }
            List<String> chainIds = new ArrayList<>();
            expandInto(id, byId, new LinkedHashSet<>(), new LinkedHashSet<>(), chainIds);
            for (String cid : chainIds) {
                out.add(byId.get(cid));
            }
        }
        return out;
    }
    // expandInto: path stack for cycles; "seenInLeaf" LinkedHashSet for within-leaf dedupe;
    // for each callBefore id: expandInto first; then add self to chainIds if seenInLeaf.add(self)
}
```

- [ ] **Step 5: Re-run tests — expect PASS**

```powershell
mvn -q "-Dtest=CallBeforeExpanderTest,CallBeforeTest" test
```

---

### Task 2: Excel / CSV / JSON I/O

**Files:**
- Modify: `ManualTcExcelWriter.java` — append header `CallBefore`, cell 10
- Modify: `ExcelTcReader.java` — `columns.getOrDefault("CALLBEFORE", -1)`
- Modify: `GeneratedTcCsvParser.java` — parse + `toCsv`
- Modify: `GeneratedTcJsonParser.java` — `callBefore` field
- Modify: any `mergeCaseFields` / repair paths that reconstruct `ManualTestCase`
- Test: `src/test/java/delivery/excel/CallBeforeWorkbookRoundTripTest.java`

- [ ] **Step 1: Failing round-trip test**

Write xlsx via `ManualTcExcelWriter` with `callBefore="TC_01"`, read back with `ExcelTcReader`, assert field preserved. Same for CSV `toCsv`/`parse`.

- [ ] **Step 2: Implement reader/writer/parser changes**

Header normalize already strips non-alphanumerics → `Call Before` → `CALLBEFORE`.

- [ ] **Step 3: Run round-trip test — PASS**

```powershell
mvn -q "-Dtest=CallBeforeWorkbookRoundTripTest" test
```

---

### Task 3: Wire materializer + job start errors

**Files:**
- Modify: `WorkbookJobMaterializer.java`
- Modify: `GeneratedWorkbookService.materializeForJob`
- Modify: `JobController` / `ExecuteRunController` (map `CALL_BEFORE_*` / `UNKNOWN_*` to 400 `ApiError`)
- Test: `src/test/java/delivery/portal/service/WorkbookJobMaterializerCallBeforeTest.java`

**Interfaces:**
- Change merge behavior when `selectedTcIds` non-empty:
  1. Build `byId` from **all** library + upload (upload wins).
  2. `ordered = CallBeforeExpander.expand(new ArrayList<>(byId.values()), selectedLeafIds)`.
  3. Return `ordered` (not `byId.values()`).
- When `selectedTcIds` null/empty and using full library: expand each library ID in workbook order as a leaf (per spec).

- [ ] **Step 1: Failing materializer test** — library has TC_01+TC_06; select only TC_06 with callBefore TC_01; assert materialize list is TC_01, TC_06.

- [ ] **Step 2: Implement materializer + controller error mapping**

- [ ] **Step 3: Tests PASS**

```powershell
mvn -q "-Dtest=WorkbookJobMaterializerCallBeforeTest,CallBeforeExpanderTest" test
```

---

### Task 4: Case update API + validation

**Files:**
- Modify: `GeneratedWorkbookController.UpdateCaseFieldsRequest` — add `String callBefore`
- Modify: `GeneratedWorkbookService.mergeCaseFields` / `updateCaseFields` — persist + validate IDs exist in workbook, reject self
- Modify: `toRowMap` — include `callBefore`
- Optional: `CallBefore.validateRefs(String callBefore, String selfId, Set<String> knownIds)`
- Test: extend `GeneratedWorkbookCaseUpdateTest` / API test

- [ ] **Step 1: Failing unit test** — update TC_06 callBefore=`TC_01` OK; `TC_06` self → IAE; `TC_99` unknown → IAE

- [ ] **Step 2: Implement**

- [ ] **Step 3: Tests PASS**

```powershell
mvn -q "-Dtest=GeneratedWorkbookCaseUpdateTest,GeneratedWorkbookCaseUpdateApiTest" test
```

---

### Task 5: Projects page UI polish

**Files:**
- Modify: `src/main/resources/templates/projects.html`
- Modify: `src/main/resources/static/css/portal.css` (`.projects-panel-head`, `.create-row` align)

- [ ] **Step 1: Remove Open Automate** from page-title actions

- [ ] **Step 2: Move Delete all** into `.projects-panel-head` next to Show archived; use `class="button danger"` (remove ghost)

- [ ] **Step 3: Fix create-row** so labels + submit share one baseline (`align-items: end` on `.create-row`)

- [ ] **Step 4: Manual check** — `/projects` shows red Delete all in Your projects header; no Open Automate

---

### Task 6: Library toolbar + Select all in table

**Files:**
- Modify: `project-detail.html` (toolbar + thead checkbox)
- Modify: `portal.css` (`.lib-toolbar` align-items: center)

- [ ] **Step 1: Remove toolbar Select all label**

- [ ] **Step 2: Add `<th class="lib-col-check"><input type="checkbox" id="lib-select-all" …></th>`

- [ ] **Step 3: Keep existing change handler**; ensure flex alignment on toolbar

---

### Task 7: Call-before picker UI (library edit + Generate modal)

**Files:**
- Modify: `project-detail.html` — field under Preconditions; JS chips + search
- Modify: `generate.html` — same under preconditions; include in save payload
- Modify: `portal.css` — chip styles (reuse muted badges; no purple glow)

**Behavior:**
- `#lib-edit-call-before` hidden input stores comma-separated IDs
- Search box filters `libCases` excluding self; click adds chip at end
- Chips: label `TC_ID` + title snippet; × remove; ↑↓ reorder
- On open edit: parse `tc.callBefore` into chips
- On save: `callBefore` in PUT body
- Expanded preview: if callBefore set, show `Call before: TC_01 → TC_02` above step table

- [ ] **Step 1: Markup + CSS**

- [ ] **Step 2: Wire open/save/preview**

- [ ] **Step 3: Mirror on Generate edit modal** (`callBefore` in payload)

- [ ] **Step 4: Manual smoke** — set Login on TC_06, save, reload, expand shows chain; Execute with only TC_06 selected runs Login first (after portal restart)

---

### Task 8: Verification sweep

- [ ] **Step 1: Run focused suite**

```powershell
mvn -q "-Dtest=CallBefore*,WorkbookJobMaterializerCallBeforeTest,GeneratedWorkbookCaseUpdate*,GeneratedWorkbookMergeUpload*" test
```

Expected: PASS

- [ ] **Step 2: Spec coverage check** — A1–A3 UI, B1–B5 data/expand/API all done; non-goals untouched

---

## Spec coverage (self-review)

| Spec item | Task |
|-----------|------|
| Remove Open Automate | 5 |
| Delete all red + relocated | 5 |
| Create row alignment | 5 |
| Library toolbar align | 6 |
| Select all in table | 6 |
| `CallBefore` column + model | 1–2 |
| Edit picker under Preconditions | 7 |
| Recursive expand + cycle | 1 |
| Before each leaf re-run | 1 |
| Within-leaf dedupe | 1 |
| Materializer full map | 3 |
| API update/list | 4 |
| Automate does not copy steps | (by design — no task copies steps) |

## Placeholder scan

None intentional. Commit steps omitted unless user requests commits.
