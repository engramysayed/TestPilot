# Heal Recovery → Workbook Patch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After successful heal recovery on Execute, deterministically patch the project’s generated workbook (leave-empty Steps + blank TestData) and append unmatched automationNotes to coverage notes / meta.

**Architecture:** Pure `HealWorkbookPatcher` maps proven `clear` / blank-`type` recovery steps onto Excel step lines; `GeneratedWorkbookService.applyHealRecoveryPatch` loads/saves `latest.xlsx` behind the quality gate; `ProvePhase` best-effort calls apply after evidence write. No LLM rewrite, no new modal.

**Tech Stack:** Java 17, Spring Boot portal services, TestNG, existing `ManualTestCase` / `ProvenStep` / `GenerateAuthoringRules`.

**Spec:** `docs/superpowers/specs/2026-09-02-heal-workbook-patch-design.md`

## Global Constraints

- Patch only from proven recovery steps (`clear` or blank-value `type`); never invent new numbered Steps from prose.
- Do not rewrite ExpectedResult / Title / KeelPath; do not Excel-rewrite `navigate` / valued `type` / `click` / `select`.
- Quality gate fail → do not save Excel; still merge notes + coverage append when possible.
- ProvePhase must never fail the TC because patch failed.
- Target `storeRoot/{projectId}/generated/latest.xlsx`, not the job temp Excel.

## File structure

| File | Responsibility |
|------|----------------|
| `src/main/java/delivery/heal/HealWorkbookPatcher.java` | Pure patch: case + recovery steps + notes → result |
| `src/test/java/delivery/heal/HealWorkbookPatcherTest.java` | Unit tests for matching / blanking / unmatched |
| `src/main/java/delivery/portal/service/GeneratedWorkbookService.java` | `applyHealRecoveryPatch` + coverage append helper |
| `src/test/java/delivery/portal/service/GeneratedWorkbookHealPatchTest.java` | Save / skip / coverage / sibling TC tests |
| `src/main/java/delivery/job/ProvePhase.java` | Optional applier; call after successful recovery |
| `src/main/java/delivery/job/ExecuteJobRunner.java` | Wire `GeneratedWorkbookService` into ProvePhase |
| `src/main/java/delivery/job/ConversionJobRunner.java` | Same wiring for Automate prove path |
| `src/test/java/delivery/job/ProvePhaseHealWorkbookApplyTest.java` | Apply invoked; failure does not change recovery outcome |
| `docs/superpowers/plans/2026-09-02-defect-fix-authoring-heal-notes.md` | Mark Excel rewrite no longer deferred |

---

### Task 1: HealWorkbookPatcher (pure)

**Files:**
- Create: `src/main/java/delivery/heal/HealWorkbookPatcher.java`
- Create: `src/test/java/delivery/heal/HealWorkbookPatcherTest.java`

**Interfaces:**
- Consumes: `delivery.excel.ManualTestCase`, `delivery.codegen.ProvenStep`, `GenerateAuthoringRules` package helpers (use public/package API; if a helper is package-private in `delivery.excel`, either call from same package via a thin `delivery.excel` facade **or** duplicate only the field-kind inference from locator text — prefer calling `GenerateAuthoringRules` by making needed helpers `public` if currently package-private: `splitNumberedSteps`, `splitTestDataLines`, `isEnterStepForField`, `alreadyLeaveEmpty`)
- Produces:
  - `record PatchResult(ManualTestCase patchedCase, List<String> appliedSummaries, List<String> unmatchedNotes, boolean cellsChanged)`
  - `static PatchResult patch(ManualTestCase tc, List<ProvenStep> recoverySteps, List<String> automationNotes)`

- [x] **Step 1: Write the failing test**

```java
package delivery.heal;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.List;

public class HealWorkbookPatcherTest {
    private static ProvenStep clear(String locator) {
        return new ProvenStep("TC1", "Page", "elementAction", "clear", "css", locator,
                "", "", "", true, "heal:recovery");
    }

    @Test
    public void clearEmail_rewritesEnterStep_andBlanksTestData() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "Empty email", "",
                "1. Enter in the Email field\n2. Click Login",
                "Error shown", "P1", "", "",
                "user@x.com\n", "EXECUTE");
        HealWorkbookPatcher.PatchResult r = HealWorkbookPatcher.patch(
                tc, List.of(clear("input[name='email']")), List.of("Keep Leave-empty on email"));
        Assert.assertTrue(r.cellsChanged());
        Assert.assertTrue(r.patchedCase().steps().toLowerCase().contains("leave"));
        Assert.assertTrue(r.patchedCase().steps().toLowerCase().contains("email"));
        String[] data = r.patchedCase().testData().split("\n", -1);
        Assert.assertTrue(data[0].isBlank());
        Assert.assertTrue(r.unmatchedNotes().isEmpty() || r.appliedSummaries().size() >= 1);
    }

    @Test
    public void alreadyLeaveEmpty_blanksTestDataOnly() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "",
                "1. Leave the Email field empty\n2. Click Login",
                "ok", "P1", "", "",
                "oops@x.com\n", "EXECUTE");
        HealWorkbookPatcher.PatchResult r = HealWorkbookPatcher.patch(
                tc, List.of(clear("#email")), List.of());
        Assert.assertTrue(r.cellsChanged());
        Assert.assertTrue(r.patchedCase().testData().split("\n", -1)[0].isBlank());
    }

    @Test
    public void clickAndNavigate_doNotRewrite() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "", "1. Enter in the Email field", "ok", "P1", "", "",
                "a@b.c", "EXECUTE");
        ProvenStep click = new ProvenStep("TC1", "Page", "elementAction", "click", "css", "#login",
                "", "", "", true, "heal:recovery");
        ProvenStep nav = new ProvenStep("TC1", "Page", "elementAction", "navigate", "url", "/login",
                "", "", "", true, "heal:recovery");
        HealWorkbookPatcher.PatchResult r = HealWorkbookPatcher.patch(tc, List.of(click, nav),
                List.of("Free-form note only"));
        Assert.assertFalse(r.cellsChanged());
        Assert.assertEquals(r.patchedCase().steps(), tc.steps());
        Assert.assertEquals(r.unmatchedNotes(), List.of("Free-form note only"));
    }

    @Test
    public void doesNotAddSteps() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "", "1. Click Login", "ok", "P1", "", "", "", "EXECUTE");
        long linesBefore = tc.steps().lines().count();
        HealWorkbookPatcher.PatchResult r = HealWorkbookPatcher.patch(
                tc, List.of(clear("input[type=email]")), List.of("note"));
        Assert.assertEquals(r.patchedCase().steps().lines().count(), linesBefore);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=HealWorkbookPatcherTest test`  
Expected: FAIL (class missing)

- [ ] **Step 3: Write minimal implementation**

```java
package delivery.heal;

import delivery.codegen.ProvenStep;
import delivery.excel.GenerateAuthoringRules;
import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HealWorkbookPatcher {
    private HealWorkbookPatcher() {}

    public record PatchResult(
            ManualTestCase patchedCase,
            List<String> appliedSummaries,
            List<String> unmatchedNotes,
            boolean cellsChanged
    ) {
        public PatchResult {
            appliedSummaries = appliedSummaries == null ? List.of() : List.copyOf(appliedSummaries);
            unmatchedNotes = unmatchedNotes == null ? List.of() : List.copyOf(unmatchedNotes);
        }
    }

    public static PatchResult patch(
            ManualTestCase tc,
            List<ProvenStep> recoverySteps,
            List<String> automationNotes
    ) {
        if (tc == null) {
            return new PatchResult(null, List.of(), notesOrEmpty(automationNotes), false);
        }
        List<String> stepLines = new ArrayList<>(GenerateAuthoringRules.splitNumberedSteps(tc.steps()));
        List<String> dataLines = new ArrayList<>(
                GenerateAuthoringRules.splitTestDataLines(tc.testData(), stepLines.size()));
        List<String> applied = new ArrayList<>();
        Set<Integer> touched = new LinkedHashSet<>();

        if (recoverySteps != null) {
            for (ProvenStep step : recoverySteps) {
                if (!isClearOrBlankType(step)) {
                    continue;
                }
                String kind = inferFieldKind(step.locatorValue());
                int idx = findBestStepIndex(stepLines, kind, step.locatorValue());
                if (idx < 0) {
                    continue;
                }
                String label = leaveEmptyLabel(stepLines.get(idx), kind);
                String rewritten = "Leave the " + label + " field empty";
                if (!GenerateAuthoringRules.alreadyLeaveEmpty(stepLines.get(idx))
                        || !stepLines.get(idx).equalsIgnoreCase(rewritten)) {
                    if (!GenerateAuthoringRules.alreadyLeaveEmpty(stepLines.get(idx))) {
                        stepLines.set(idx, rewritten);
                    }
                }
                while (dataLines.size() <= idx) {
                    dataLines.add("");
                }
                if (!dataLines.get(idx).isBlank()) {
                    dataLines.set(idx, "");
                }
                touched.add(idx);
                applied.add("step " + (idx + 1) + ": leave-empty " + label);
            }
        }

        boolean cellsChanged = !touched.isEmpty();
        // If leave-empty already and we only blanked data, cellsChanged still true via touched
        ManualTestCase out = tc;
        if (cellsChanged) {
            out = new ManualTestCase(
                    tc.tcId(), tc.title(), tc.preconditions(),
                    numberedSteps(stepLines),
                    tc.expectedResult(), tc.priority(), tc.tags(), tc.visualAssertion(),
                    String.join("\n", dataLines),
                    tc.keelPath());
        }

        List<String> unmatched = new ArrayList<>();
        if (automationNotes != null) {
            for (String n : automationNotes) {
                if (n != null && !n.isBlank()) {
                    unmatched.add(n.trim());
                }
            }
        }
        // v1: notes never consume structured matches; all notes remain for coverage/meta
        // (structured changes come only from ProvenSteps). Spec: unmatched = notes not used to invent steps.
        return new PatchResult(out, applied, unmatched, cellsChanged);
    }

    static boolean isClearOrBlankType(ProvenStep step) {
        if (step == null || step.action() == null) {
            return false;
        }
        String a = step.action().trim().toLowerCase(Locale.ROOT);
        if ("clear".equals(a)) {
            return true;
        }
        if ("type".equals(a)) {
            return step.value() == null || step.value().isBlank();
        }
        return false;
    }

    static String inferFieldKind(String locatorValue) {
        String h = locatorValue == null ? "" : locatorValue.toLowerCase(Locale.ROOT);
        if (h.contains("password") || h.contains("passwd")) {
            return "password";
        }
        if (h.contains("email") || h.contains("e-mail")) {
            return "email";
        }
        if (h.contains("phone") || h.contains("mobile") || h.contains("tel")) {
            return "phone";
        }
        return "";
    }

    static int findBestStepIndex(List<String> stepLines, String kind, String locatorValue) {
        if (stepLines == null || stepLines.isEmpty()) {
            return -1;
        }
        // Prefer leave-empty / enter step for inferred kind; else any step mentioning kind/locator token
        for (int i = 0; i < stepLines.size(); i++) {
            String lower = stepLines.get(i).toLowerCase(Locale.ROOT);
            if (!kind.isBlank() && GenerateAuthoringRules.isEnterStepForField(lower, kind)) {
                return i;
            }
            if (!kind.isBlank() && GenerateAuthoringRules.alreadyLeaveEmpty(stepLines.get(i))
                    && lower.contains(kind)) {
                return i;
            }
        }
        String token = kind.isBlank() ? lastLocatorToken(locatorValue) : kind;
        if (token.isBlank()) {
            return -1;
        }
        for (int i = 0; i < stepLines.size(); i++) {
            if (stepLines.get(i).toLowerCase(Locale.ROOT).contains(token)) {
                return i;
            }
        }
        return -1;
    }

    private static String lastLocatorToken(String locatorValue) {
        if (locatorValue == null || locatorValue.isBlank()) {
            return "";
        }
        String h = locatorValue.toLowerCase(Locale.ROOT);
        // crude: strip css noise
        h = h.replaceAll("[^a-z0-9_-]+", " ").trim();
        String[] parts = h.split("\\s+");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private static String leaveEmptyLabel(String stepText, String kind) {
        String lower = stepText == null ? "" : stepText.toLowerCase(Locale.ROOT);
        if (lower.contains("email or phone")) {
            return "Email or phone";
        }
        return switch (kind) {
            case "email" -> "Email";
            case "phone" -> "Phone";
            case "password" -> "Password";
            default -> "field";
        };
    }

    private static List<String> notesOrEmpty(List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String n : notes) {
            if (n != null && !n.isBlank()) {
                out.add(n.trim());
            }
        }
        return out;
    }

    private static String numberedSteps(List<String> stepLines) {
        List<String> numbered = new ArrayList<>(stepLines.size());
        for (int i = 0; i < stepLines.size(); i++) {
            numbered.add((i + 1) + ". " + stepLines.get(i));
        }
        return String.join("\n", numbered);
    }
}
```

Also make `GenerateAuthoringRules.splitNumberedSteps`, `splitTestDataLines`, `isEnterStepForField`, `alreadyLeaveEmpty` **public** if still package-private.

- [ ] **Step 4: Run tests — expect PASS**

Run: `mvn -q -Dtest=HealWorkbookPatcherTest test`

- [ ] **Step 5: Commit** (only if user asked to commit; otherwise skip)

---

### Task 2: GeneratedWorkbookService.applyHealRecoveryPatch

**Files:**
- Modify: `src/main/java/delivery/portal/service/GeneratedWorkbookService.java`
- Create: `src/test/java/delivery/portal/service/GeneratedWorkbookHealPatchTest.java`

**Interfaces:**
- Consumes: `HealWorkbookPatcher.patch(...)`
- Produces:
  - `record HealPatchApplyResult(boolean excelSaved, boolean skipped, String reason, List<String> appliedSummaries)`
  - `HealPatchApplyResult applyHealRecoveryPatch(String projectId, String tcId, List<ProvenStep> recoverySteps, List<String> automationNotes, String baseUrl) throws Exception`

- [ ] **Step 1: Write failing tests**

```java
package delivery.portal.service;

import delivery.codegen.ProvenStep;
import delivery.excel.ExcelTcReader;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class GeneratedWorkbookHealPatchTest {
    private static GeneratedWorkbookService svc(Path root) {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(root.toString());
        return new GeneratedWorkbookService(props);
    }

    private static ProvenStep clearEmail() {
        return new ProvenStep("TC1", "Page", "elementAction", "clear", "css", "input[name=email]",
                "", "", "", true, "heal:recovery");
    }

    @Test
    public void apply_savesLeaveEmptyAndBlanksTestData() throws Exception {
        Path root = Files.createTempDirectory("keel-heal-patch-ok");
        GeneratedWorkbookService svc = svc(root);
        ManualTestCase tc = new ManualTestCase(
                "TC1", "Empty email login", "",
                "1. Enter in the Email field\n2. Click Login",
                "Shows error", "P1", "", "",
                "typed@x.com\n", "EXECUTE");
        ManualTestCase other = new ManualTestCase(
                "TC2", "ok", "", "1. Open /", "ok", "P1", "", "", "", "EXECUTE");
        svc.saveFromCases("p1", List.of(tc, other), "IMPORT", "t");

        var result = svc.applyHealRecoveryPatch(
                "p1", "TC1", List.of(clearEmail()), List.of("Keep Leave-empty"), "https://example.test");
        Assert.assertTrue(result.excelSaved());
        ManualTestCase saved = new ExcelTcReader().read(svc.requireExcel("p1")).stream()
                .filter(c -> "TC1".equals(c.tcId())).findFirst().orElseThrow();
        Assert.assertTrue(saved.steps().toLowerCase().contains("leave"));
        Assert.assertTrue(saved.testData().split("\n", -1)[0].isBlank());
        ManualTestCase sibling = new ExcelTcReader().read(svc.requireExcel("p1")).stream()
                .filter(c -> "TC2".equals(c.tcId())).findFirst().orElseThrow();
        Assert.assertEquals(sibling.title(), "ok");
        Map<?, ?> byTc = (Map<?, ?>) svc.describe("p1").orElseThrow().get("automationNotesByTc");
        Assert.assertTrue(((List<?>) byTc.get("TC1")).contains("Keep Leave-empty"));
        String coverage = String.valueOf(svc.describe("p1").orElseThrow().get("coverageNotes"));
        Assert.assertTrue(coverage.contains("Heal notes") && coverage.contains("Keep Leave-empty"));
    }

    @Test
    public void apply_missingWorkbook_skipsExcel() throws Exception {
        Path root = Files.createTempDirectory("keel-heal-patch-miss");
        GeneratedWorkbookService svc = svc(root);
        var result = svc.applyHealRecoveryPatch(
                "none", "TC1", List.of(clearEmail()), List.of("n"), "https://example.test");
        Assert.assertTrue(result.skipped());
        Assert.assertFalse(result.excelSaved());
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`applyHealRecoveryPatch` missing)

- [ ] **Step 3: Implement `applyHealRecoveryPatch` + coverage append**

Add to `GeneratedWorkbookService`:

```java
public record HealPatchApplyResult(
        boolean excelSaved,
        boolean skipped,
        String reason,
        List<String> appliedSummaries
) {
    public HealPatchApplyResult {
        reason = reason == null ? "" : reason;
        appliedSummaries = appliedSummaries == null ? List.of() : List.copyOf(appliedSummaries);
    }
}

public HealPatchApplyResult applyHealRecoveryPatch(
        String projectId,
        String tcId,
        List<delivery.codegen.ProvenStep> recoverySteps,
        List<String> automationNotes,
        String baseUrl
) throws Exception {
    if (projectId == null || projectId.isBlank() || tcId == null || tcId.isBlank()) {
        return new HealPatchApplyResult(false, true, "missing projectId/tcId", List.of());
    }
    Path dir = generatedDir(projectId);
    if (!Files.isRegularFile(dir.resolve(EXCEL_FILE))) {
        return new HealPatchApplyResult(false, true, "NO_GENERATED_WORKBOOK", List.of());
    }
    List<ManualTestCase> cases = new ExcelTcReader().read(requireExcel(projectId));
    String normalizedTcId = tcId.trim();
    int index = -1;
    for (int i = 0; i < cases.size(); i++) {
        if (normalizedTcId.equals(cases.get(i).tcId())) {
            index = i;
            break;
        }
    }
    if (index < 0) {
        mergeAutomationNotes(projectId, normalizedTcId, automationNotes);
        appendHealCoverageNotes(projectId, normalizedTcId, automationNotes);
        return new HealPatchApplyResult(false, true, "unknown tcId", List.of());
    }

    HealWorkbookPatcher.PatchResult patched = HealWorkbookPatcher.patch(
            cases.get(index), recoverySteps, automationNotes);
    mergeAutomationNotes(projectId, normalizedTcId, automationNotes);
    appendHealCoverageNotes(projectId, normalizedTcId, patched.unmatchedNotes());

    if (!patched.cellsChanged()) {
        return new HealPatchApplyResult(false, false, "no cell changes", patched.appliedSummaries());
    }

    List<ManualTestCase> next = new ArrayList<>(cases);
    next.set(index, patched.patchedCase());
    List<ManualTestCase> repaired = TcImportRepair.repairCases(next);
    List<String> gateErrors = GenerateQualityGate.validate(repaired, baseUrl);
    if (!gateErrors.isEmpty()) {
        return new HealPatchApplyResult(false, true,
                "HEAL_WORKBOOK_PATCH_SKIPPED: " + String.join("; ", gateErrors),
                patched.appliedSummaries());
    }
    Map<String, Object> meta = readMeta(dir);
    String source = String.valueOf(meta.getOrDefault("source", "GENERATE"));
    String sourceRef = String.valueOf(meta.getOrDefault("sourceRef", ""));
    Object modelObj = meta.get("model");
    String model = modelObj == null ? null : String.valueOf(modelObj);
    if (model != null && model.isBlank()) {
        model = null;
    }
    saveFromCases(projectId, repaired, source, sourceRef, model);
    // re-merge notes after saveFromCases preserves prior automationNotesByTc
    mergeAutomationNotes(projectId, normalizedTcId, automationNotes);
    appendHealCoverageNotes(projectId, normalizedTcId, patched.unmatchedNotes());
    return new HealPatchApplyResult(true, false, "", patched.appliedSummaries());
}

/** Append unmatched heal notes under a dated Heal notes heading; dedupe exact lines. */
void appendHealCoverageNotes(String projectId, String tcId, List<String> notes) throws Exception {
    if (notes == null || notes.isEmpty()) {
        return;
    }
    Path dir = generatedDir(projectId);
    if (!Files.isRegularFile(dir.resolve(EXCEL_FILE))) {
        return;
    }
    Map<String, Object> meta = new LinkedHashMap<>(readMeta(dir));
    String prior = meta.get("coverageNotes") == null ? "" : String.valueOf(meta.get("coverageNotes"));
    StringBuilder block = new StringBuilder();
    block.append("### Heal notes (").append(tcId).append(")\n");
    boolean any = false;
    for (String n : notes) {
        if (n == null || n.isBlank()) {
            continue;
        }
        String line = n.trim();
        if (prior.contains(line)) {
            continue;
        }
        block.append("- ").append(line).append('\n');
        any = true;
    }
    if (!any) {
        return;
    }
    String updated = prior.isBlank() ? block.toString().trim() : prior.trim() + "\n\n" + block.toString().trim();
    meta.put("coverageNotes", updated);
    meta.put("updatedAt", Instant.now().toString());
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(META_FILE).toFile(), meta);
}
```

Ensure `saveFromCases` continues to preserve `automationNotesByTc` and `coverageNotes` (already does). After `saveFromCases`, re-call merge/append because save may keep prior coverage but merge must re-apply if save wiped nothing — coverage is preserved from previous meta; append before save is enough if save preserves coverageNotes — **order:** append after save so new heal lines are not lost. Prefer: save first (preserves old coverage), then merge notes + append.

Fix order in implementation:
1. patch
2. if cellsChanged && gate OK → saveFromCases
3. mergeAutomationNotes
4. appendHealCoverageNotes

On gate fail / no cells: still steps 3–4.

- [ ] **Step 4: Run tests — PASS**

Run: `mvn -q -Dtest=GeneratedWorkbookHealPatchTest,GeneratedWorkbookCoverageNotesTest test`

- [ ] **Step 5: Commit** (skip unless user asked)

---

### Task 3: Wire ProvePhase + runners

**Files:**
- Modify: `src/main/java/delivery/job/ProvePhase.java`
- Modify: `src/main/java/delivery/job/ExecuteJobRunner.java`
- Modify: `src/main/java/delivery/job/ConversionJobRunner.java`
- Create: `src/test/java/delivery/job/ProvePhaseHealWorkbookApplyTest.java`

**Interfaces:**
- Consumes: `GeneratedWorkbookService.applyHealRecoveryPatch`
- Produces: `ProvePhase.withHealWorkbookApplier(HealWorkbookApplier)` where

```java
@FunctionalInterface
public interface HealWorkbookApplier {
    void apply(String projectId, String tcId, List<ProvenStep> recoverySteps,
               List<String> automationNotes, String baseUrl) throws Exception;
}
```

- [ ] **Step 1: Write failing test for apply-after-recovery helper**

Extract package-visible helper so tests don’t need full browser prove:

```java
// ProvePhase.java
static void applyHealWorkbookPatchBestEffort(
        HealWorkbookApplier applier,
        ConversionJobRequest request,
        String tcId,
        HealResult healed
) {
    if (applier == null || request == null || healed == null || !healed.ok()) {
        return;
    }
    if (!"recovery".equals(healed.tierUsed())) {
        return;
    }
    try {
        applier.apply(
                request.projectId(),
                tcId,
                healed.steps(),
                healed.automationNotes(),
                request.baseUrl());
    } catch (Exception e) {
        LogsManager.warn("HEAL_WORKBOOK_PATCH: " + e.getMessage());
    }
}
```

Test:

```java
@Test
public void applyBestEffort_invokesApplier_forRecovery() {
    AtomicBoolean called = new AtomicBoolean();
    HealWorkbookApplier applier = (pid, tc, steps, notes, url) -> called.set(true);
    ConversionJobRequest req = new ConversionJobRequest(
            "p1", Path.of("x.xlsx"), "https://ex.test", "", "", Path.of("."), Path.of("."), Path.of("."),
            "EXECUTE", "", "");
    HealResult healed = HealResult.recovery(List.of(/*clear*/), List.of("n"), "t");
    ProvePhase.applyHealWorkbookPatchBestEffort(applier, req, "TC1", healed);
    Assert.assertTrue(called.get());
}

@Test
public void applyBestEffort_swallowsApplierFailure() {
    HealWorkbookApplier applier = (a, b, c, d, e) -> { throw new RuntimeException("boom"); };
    // ... same req + recovery heal — must not throw
    ProvePhase.applyHealWorkbookPatchBestEffort(applier, req, "TC1", healed);
}

@Test
public void applyBestEffort_skipsNonRecovery() {
    AtomicBoolean called = new AtomicBoolean();
    HealWorkbookApplier applier = (a, b, c, d, e) -> called.set(true);
    HealResult classic = HealResult.success(List.of(), "cursor");
    ProvePhase.applyHealWorkbookPatchBestEffort(applier, req, "TC1", classic);
    Assert.assertFalse(called.get());
}
```

- [ ] **Step 2: Run — FAIL until helper exists**

- [ ] **Step 3: Implement wiring**

In `ProvePhase`:
- Field `private HealWorkbookApplier healWorkbookApplier;`
- `withHealWorkbookApplier(HealWorkbookApplier a)` fluent setter
- In `tryRecoveryHeal` after `writeHealRecoveryEvidence(...)`, call `applyHealWorkbookPatchBestEffort(healWorkbookApplier, /* need request */ ...)`.

**Problem:** `tryRecoveryHeal` is static and has no `request`. Change signature to accept `ConversionJobRequest request` and `HealWorkbookApplier applier` (or instance method). Prefer making `tryRecoveryHeal` an instance method, or pass request+applier as parameters from all call sites.

Update every `tryRecoveryHeal(...)` call site to pass `request` and use instance field `healWorkbookApplier`.

In `ExecuteJobRunner` / `ConversionJobRunner`:

```java
DeliveryPortalProperties props = new DeliveryPortalProperties();
props.setStoreRoot(request.storeRoot().toString());
GeneratedWorkbookService workbooks = new GeneratedWorkbookService(props);
ProvePhase prove = new ProvePhase(progress)
    .withMirrorRoot(dest) // execute only
    .withCancelCheck(cancelCheck)
    .withHealWorkbookApplier((projectId, tcId, steps, notes, baseUrl) ->
        workbooks.applyHealRecoveryPatch(projectId, tcId, steps, notes, baseUrl));
```

- [ ] **Step 4: Run**

Run: `mvn -q -Dtest=ProvePhaseHealWorkbookApplyTest,ProvePhaseRecoveryEscalateTest,HealWorkbookPatcherTest,GeneratedWorkbookHealPatchTest test`

- [ ] **Step 5: Commit** (skip unless asked)

---

### Task 4: Docs + verification bundle

**Files:**
- Modify: `docs/superpowers/plans/2026-09-02-defect-fix-authoring-heal-notes.md` — remove “Auto-rewrite Excel step cells” from intentional deferrals; note patcher shipped
- Modify: `docs/superpowers/specs/2026-09-02-heal-workbook-patch-design.md` — Status: Implemented
- Optional one line in `README.md` Recovery paragraph: successful recovery may update generated workbook leave-empty cells

- [ ] **Step 1: Update notes + spec status**
- [ ] **Step 2: Run focused suite**

```bash
mvn -q -Dtest=HealWorkbookPatcherTest,GeneratedWorkbookHealPatchTest,ProvePhaseHealWorkbookApplyTest,ProvePhaseRecoveryEscalateTest,GeneratedWorkbookCoverageNotesTest,RecoveryPlanParserTest test
```

Expected: all PASS

- [ ] **Step 3: Commit** (skip unless asked)

---

## Spec coverage checklist

| Spec requirement | Task |
|------------------|------|
| Deterministic clear/blank-type → leave-empty + blank TestData | T1 |
| No invent steps; no navigate/click Excel rewrite | T1 |
| Unmatched notes → meta + coverage append | T2 |
| Gate fail → no Excel save | T2 |
| Target generated latest.xlsx | T2 |
| Best-effort ProvePhase after recovery evidence | T3 |
| Runner wiring | T3 |
| Tests listed in spec | T1–T3 |
| Docs deferral update | T4 |

## Self-review

- No TBD placeholders in task steps.
- `PatchResult` / `applyHealRecoveryPatch` / `HealWorkbookApplier` names consistent across tasks.
- Note handling: all automationNotes go to meta + coverage (structured Excel changes from ProvenSteps only) — matches “notes never invent Steps”.
