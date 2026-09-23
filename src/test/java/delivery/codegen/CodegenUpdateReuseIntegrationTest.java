package delivery.codegen;

import delivery.authoring.AuthoringEngine;
import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.EmitPhase;
import delivery.job.JobProgressTracker;
import delivery.job.OccurrenceIdentity;
import delivery.job.ReuseEligibility;
import delivery.packager.FrameworkPackager;
import delivery.store.ProjectStore;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UPDATE reuse through {@link ReuseEligibility#authorIds} + {@link ReuseEligibility#copyForReuse}
 * + {@link EmitPhase}. Distinct from {@link GeneratedZipReplayBenchmarkTest}, which regenerates
 * fixture IR and does not exercise this pipeline.
 */
public class CodegenUpdateReuseIntegrationTest {
    private static final Path TEMPLATE = Path.of("customer-framework-template");
    private static final String PROJECT = "codegen_update_reuse";

    @Test
    public void updateReuseRetainsUnchangedReauthorsChangedDemotesPassToTodoInvalidatesPrereqsAndRepeatedEdits()
            throws Exception {
        Path workRoot = Files.createTempDirectory("codegen-update-reuse");
        Path storeRoot = Files.createTempDirectory("codegen-update-reuse-store");
        String baseUrl = "https://example.invalid/reuse";

        List<ManualTestCase> original = originalCases();
        Path newWork = workRoot.resolve("new");
        emit("NEW", newWork, original, originalDrafts(newWork, original, baseUrl), storeRoot, baseUrl);

        ProjectStore store = new ProjectStore(storeRoot, baseUrl);
        Path projectRoot = store.projectRoot(PROJECT);
        Map<String, String> storedHashes = readHashes(projectRoot);
        Map<String, TcDraft> storedDrafts = ReuseEligibility.loadStoredDrafts(projectRoot);
        ReuseEligibility.Context storedCtx = ReuseEligibility.read(projectRoot);

        List<ManualTestCase> updated = updatedCases();
        Path updateWork = workRoot.resolve("update");
        ConversionJobRequest updateReq = request("UPDATE", updateWork, storeRoot, baseUrl);
        Set<String> author = ReuseEligibility.authorIds(
                updated, storedHashes, storedDrafts, storedCtx,
                ReuseEligibility.current(updateReq), projectRoot);

        Assert.assertFalse(author.contains("TC_KEEP"), "unchanged PASS must be reused: " + author);
        Assert.assertTrue(author.contains("TC_CHANGE"), "changed case must re-author: " + author);
        Assert.assertTrue(author.contains("TC_FALL"), "PASS→TODO case must re-author: " + author);
        Assert.assertTrue(author.contains("TC_SETUP"), author.toString());
        Assert.assertTrue(author.contains("TC_LEAF"), "changed prerequisite must invalidate leaf: " + author);
        Assert.assertTrue(author.contains("TC_EDIT"), "repeated-edit value change must re-author: " + author);

        List<TcDraft> updateDrafts = new ArrayList<>();
        for (ManualTestCase tc : updated) {
            if (!author.contains(tc.tcId())) {
                updateDrafts.add(ReuseEligibility.copyForReuse(storedDrafts.get(tc.tcId()), tc, projectRoot));
                continue;
            }
            switch (tc.tcId()) {
                case "TC_FALL" -> updateDrafts.add(todoDraft(tc, updateWork, baseUrl));
                case "TC_CHANGE" -> updateDrafts.add(passed(tc,
                        List.of(click(tc.tcId(), "Home", "second")), updateWork, baseUrl));
                case "TC_SETUP" -> updateDrafts.add(passed(tc,
                        List.of(click(tc.tcId(), "Home", "save")), updateWork, baseUrl));
                case "TC_LEAF" -> updateDrafts.add(passed(tc,
                        List.of(click(tc.tcId(), "Home", "first")), updateWork, baseUrl));
                case "TC_EDIT" -> updateDrafts.add(passed(tc, List.of(
                        type(tc.tcId(), "Profile", "email", "first@example.invalid"),
                        type(tc.tcId(), "Profile", "email", "third@example.invalid")), updateWork, baseUrl));
                default -> Assert.fail("unexpected author id " + tc.tcId() + " in " + author);
            }
        }

        emit("UPDATE", updateWork, updated, updateDrafts, storeRoot, baseUrl);
        Path updateProject = updateWork.resolve("project");

        Assert.assertEquals(statusOf(updateDrafts, "TC_KEEP"), TcDraftStatus.REUSED);
        String keep = Files.readString(updateProject.resolve("src/test/java/project/tests/generated/TC_KEEP.java"));
        Assert.assertTrue(keep.contains("click_Save_Button") || keep.contains("save"), keep);

        String change = Files.readString(updateProject.resolve("src/test/java/project/tests/generated/TC_CHANGE.java"));
        Assert.assertTrue(change.contains("click_Second_Button") || change.contains("second"), change);
        Assert.assertFalse(change.contains("click_First_Button"), change);

        Assert.assertFalse(Files.exists(updateProject.resolve("src/test/java/project/tests/generated/TC_FALL.java")),
                "PASS→TODO must delete the generated class");
        Assert.assertTrue(Files.isRegularFile(
                updateProject.resolve("src/test/java/project/tests/todo/TC_FALLTodo.java")));

        String setup = Files.readString(updateProject.resolve("src/test/java/project/tests/generated/TC_SETUP.java"));
        Assert.assertTrue(setup.contains("click_Save_Button") || setup.contains("save"), setup);
        String leaf = Files.readString(updateProject.resolve("src/test/java/project/tests/generated/TC_LEAF.java"));
        Assert.assertTrue(leaf.contains("click_First_Button") || leaf.contains("first"), leaf);

        String data = Files.readString(
                updateProject.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        Assert.assertTrue(data.contains("first@example.invalid"), data);
        Assert.assertTrue(data.contains("third@example.invalid"), data);
        Assert.assertFalse(data.contains("second@example.invalid"), data);
        String edit = Files.readString(updateProject.resolve("src/test/java/project/tests/generated/TC_EDIT.java"));
        Assert.assertTrue(edit.contains(".1"), edit);
        Assert.assertTrue(edit.contains(".2"), edit);

        Map<String, String> afterHashes = readHashes(store.projectRoot(PROJECT));
        Map<String, TcDraft> afterDrafts = ReuseEligibility.loadStoredDrafts(store.projectRoot(PROJECT));
        ReuseEligibility.Context afterCtx = ReuseEligibility.read(store.projectRoot(PROJECT));
        Set<String> secondAuthor = ReuseEligibility.authorIds(
                updated, afterHashes, afterDrafts, afterCtx,
                ReuseEligibility.current(updateReq), store.projectRoot(PROJECT));
        Assert.assertFalse(secondAuthor.contains("TC_KEEP"), secondAuthor.toString());
        Assert.assertFalse(secondAuthor.contains("TC_CHANGE"),
                "second UPDATE must reuse the re-proven change: " + secondAuthor);
        Assert.assertTrue(secondAuthor.contains("TC_FALL"),
                "TODO remains in the author set on a repeated UPDATE: " + secondAuthor);
        Assert.assertFalse(secondAuthor.contains("TC_SETUP"), secondAuthor.toString());
        Assert.assertFalse(secondAuthor.contains("TC_LEAF"), secondAuthor.toString());
        Assert.assertFalse(secondAuthor.contains("TC_EDIT"), secondAuthor.toString());
    }

    private ConversionJobResult emit(
            String mode,
            Path work,
            List<ManualTestCase> cases,
            List<TcDraft> drafts,
            Path storeRoot,
            String baseUrl) throws Exception {
        Files.createDirectories(work);
        Path excelPath = work.resolve("cases.xlsx");
        ManualTcExcelWriter.write(excelPath, cases);
        TcDraftStore ir = new TcDraftStore(work);
        for (TcDraft draft : drafts) {
            ir.write(draft);
        }
        Path projectDir = work.resolve("project");
        FrameworkPackager packager = new FrameworkPackager();
        packager.copyTemplate(TEMPLATE, projectDir);
        ProjectStore store = new ProjectStore(storeRoot, baseUrl);
        if ("UPDATE".equals(mode) && store.hasFramework(PROJECT)) {
            packager.overlayCustomerConfig(store.projectRoot(PROJECT).resolve("framework"), projectDir);
        }
        ConversionJobRequest req = request(mode, work, storeRoot, baseUrl);
        return new EmitPhase(new JobProgressTracker()).emit(
                req, cases, work, projectDir, work.getFileName().toString(), mode);
    }

    private static ConversionJobRequest request(String mode, Path work, Path storeRoot, String baseUrl) {
        return new ConversionJobRequest(
                PROJECT, work.resolve("cases.xlsx"), baseUrl, "demo", "demo-pass",
                work, storeRoot, TEMPLATE, mode,
                "http://127.0.0.1:11434", "qwen2.5", false, false, AuthoringEngine.KEEL);
    }

    private static List<ManualTestCase> originalCases() {
        return List.of(
                caseRow("TC_KEEP", "Keep save", "", "click save"),
                caseRow("TC_CHANGE", "Change target", "", "click first"),
                caseRow("TC_FALL", "Fall to todo", "", "click first"),
                caseRow("TC_SETUP", "Setup click", "", "click first"),
                caseRow("TC_LEAF", "Leaf after setup", "TC_SETUP", "click first"),
                caseRow("TC_EDIT", "Repeated email edits", "", "type email first then second"));
    }

    private static List<ManualTestCase> updatedCases() {
        return List.of(
                caseRow("TC_KEEP", "Keep save", "", "click save"),
                caseRow("TC_CHANGE", "Change target", "", "click second"),
                caseRow("TC_FALL", "Fall to todo", "", "click missing"),
                caseRow("TC_SETUP", "Setup click", "", "click save"),
                caseRow("TC_LEAF", "Leaf after setup", "TC_SETUP", "click first"),
                caseRow("TC_EDIT", "Repeated email edits", "", "type email first then third"));
    }

    private static List<TcDraft> originalDrafts(Path work, List<ManualTestCase> rows, String baseUrl)
            throws Exception {
        return List.of(
                passed(rows.get(0), List.of(click("TC_KEEP", "Home", "save")), work, baseUrl),
                passed(rows.get(1), List.of(click("TC_CHANGE", "Home", "first")), work, baseUrl),
                passed(rows.get(2), List.of(click("TC_FALL", "Home", "first")), work, baseUrl),
                passed(rows.get(3), List.of(click("TC_SETUP", "Home", "first")), work, baseUrl),
                passed(rows.get(4), List.of(click("TC_LEAF", "Home", "first")), work, baseUrl),
                passed(rows.get(5), List.of(
                        type("TC_EDIT", "Profile", "email", "first@example.invalid"),
                        type("TC_EDIT", "Profile", "email", "second@example.invalid")), work, baseUrl));
    }

    private static TcDraft passed(ManualTestCase row, List<ProvenStep> proven, Path work, String baseUrl)
            throws Exception {
        Path ev = work.resolve("evidence").resolve(OccurrenceIdentity.folder(row.tcId(), 1));
        Files.createDirectories(ev);
        Files.writeString(ev.resolve("step-001.png"), row.tcId(), StandardCharsets.UTF_8);
        return new TcDraft(
                row.tcId(), row.title(), row.steps(), row.expectedResult(), TcDraftStatus.PASSED,
                proven, List.of(), false, -1, "", "", ev.toString(), 0, baseUrl + "/")
                .withLoginFormUrl(baseUrl + "/");
    }

    private static TcDraft todoDraft(ManualTestCase row, Path work, String baseUrl) throws Exception {
        Path ev = work.resolve("evidence").resolve(OccurrenceIdentity.folder(row.tcId(), 1));
        Files.createDirectories(ev);
        Files.writeString(ev.resolve("step-001.png"), row.tcId(), StandardCharsets.UTF_8);
        return new TcDraft(
                row.tcId(), row.title(), row.steps(), row.expectedResult(), TcDraftStatus.TODO,
                List.of(), List.of(), false, 0, "CLICK", "re-prove failed after Excel change",
                ev.toString(), 0, baseUrl + "/");
    }

    private static ManualTestCase caseRow(String id, String title, String callBefore, String steps) {
        return new ManualTestCase(id, title, "", steps, "ok", "P1", "", "", "", "AUTOMATE", callBefore);
    }

    private static ProvenStep type(String tcId, String page, String id, String value) {
        return new ProvenStep(tcId, page, "elementAction", "type",
                "id", id, value, "", "", true, "intent:TYPE");
    }

    private static ProvenStep click(String tcId, String page, String id) {
        return new ProvenStep(tcId, page, "elementAction", "click",
                "id", id, "", "", "", true, "intent:CLICK");
    }

    private static Map<String, String> readHashes(Path projectRoot) throws Exception {
        Path hashFile = projectRoot.resolve("tc-hashes.json");
        Assert.assertTrue(Files.isRegularFile(hashFile), "missing " + hashFile);
        JSONObject json = new JSONObject(Files.readString(hashFile, StandardCharsets.UTF_8));
        Map<String, String> out = new HashMap<>();
        for (String key : json.keySet()) {
            out.put(key, json.getString(key));
        }
        return out;
    }

    private static TcDraftStatus statusOf(List<TcDraft> drafts, String tcId) {
        return drafts.stream()
                .filter(d -> tcId.equals(d.tcId()))
                .map(TcDraft::status)
                .findFirst()
                .orElseThrow();
    }
}
