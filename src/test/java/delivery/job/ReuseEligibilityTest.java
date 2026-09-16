package delivery.job;

import delivery.authoring.AuthoringEngine;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F03 / P1-02: unchanged TODO must not count as PASS; only complete prior proof is reusable.
 */
public class ReuseEligibilityTest {

    private static ProvenStep step(String id) {
        return new ProvenStep(id, "Home", "elementAction", "click",
                "id", "go", "", "", "", true, "proven");
    }

    private static TcDraft draft(String id, TcDraftStatus status, List<ProvenStep> steps) {
        return new TcDraft(id, id, "1. Click", "ok", status, steps, List.of(),
                false, status == TcDraftStatus.PASSED ? -1 : 1, "Click",
                status == TcDraftStatus.TODO ? "blocked" : "",
                status == TcDraftStatus.PASSED ? "evidence/" + id : "",
                0, "https://example.com/home");
    }

    private static ManualTestCase tc(String id, String steps, String callBefore) {
        return new ManualTestCase(id, id, "", steps, "ok", "P1", "", "", "", "AUTOMATE", callBefore);
    }

    private static ConversionJobRequest request(String baseUrl, AuthoringEngine engine) {
        return new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), baseUrl, "", "",
                Path.of("."), Path.of("."), Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, engine);
    }

    private static ReuseEligibility.Context matchingContext() {
        return ReuseEligibility.current(request("https://example.com", AuthoringEngine.KEEL));
    }

    @Test
    public void storedTodoIsNotReusable() {
        Assert.assertFalse(ReuseEligibility.canReuse(draft("TC_TODO", TcDraftStatus.TODO, List.of())));
    }

    @Test
    public void storedPartialIsNotReusable() {
        Assert.assertFalse(ReuseEligibility.canReuse(
                draft("TC_PART", TcDraftStatus.PARTIAL, List.of(step("TC_PART")))));
    }

    @Test
    public void passedWithoutProvenStepsIsNotReusable() {
        Assert.assertFalse(ReuseEligibility.canReuse(draft("TC_EMPTY", TcDraftStatus.PASSED, List.of())));
    }

    @Test
    public void missingOrCorruptDraftIsNotReusable() {
        Assert.assertFalse(ReuseEligibility.canReuse(null));
    }

    @Test
    public void passedWithProofIsReusable() {
        Assert.assertTrue(ReuseEligibility.canReuse(
                draft("TC_OK", TcDraftStatus.PASSED, List.of(step("TC_OK")))));
    }

    @Test
    public void reusedWithProofStaysReusableOnRepeatedUpdate() {
        TcDraft prior = new TcDraft(
                "TC_OK", "TC_OK", "1. Click", "ok", TcDraftStatus.REUSED,
                List.of(step("TC_OK")), List.of(), false, -1, "",
                "reused prior PASSED proof; not a fresh browser run",
                "evidence/TC_OK", 0, "https://example.com/home");
        Assert.assertTrue(ReuseEligibility.canReuse(prior));
    }

    @Test
    public void authorIdsReProveUnchangedTodoAndKeepEligiblePass() {
        ManualTestCase todo = tc("TC_TODO", "1. Click Submit", "");
        ManualTestCase pass = tc("TC_PASS", "1. Click Go", "");
        Map<String, String> hashes = Map.of(
                "TC_TODO", todo.contentHash(),
                "TC_PASS", pass.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_TODO", draft("TC_TODO", TcDraftStatus.TODO, List.of()),
                "TC_PASS", draft("TC_PASS", TcDraftStatus.PASSED, List.of(step("TC_PASS"))));
        Set<String> author = ReuseEligibility.authorIds(
                List.of(todo, pass), hashes, stored, matchingContext(), matchingContext());
        Assert.assertTrue(author.contains("TC_TODO"), "unchanged TODO must be re-proven");
        Assert.assertFalse(author.contains("TC_PASS"), "eligible PASS must be reusable");
    }

    @Test
    public void changedPrerequisiteInvalidatesDescendant() {
        ManualTestCase setup = tc("TC_CART", "1. Add widget", "");
        ManualTestCase leaf = tc("TC_CHECKOUT", "1. Place order", "TC_CART");
        ManualTestCase changedSetup = tc("TC_CART", "1. Add two widgets", "");
        Map<String, String> hashes = Map.of(
                "TC_CART", setup.contentHash(),
                "TC_CHECKOUT", leaf.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_CART", draft("TC_CART", TcDraftStatus.PASSED, List.of(step("TC_CART"))),
                "TC_CHECKOUT", draft("TC_CHECKOUT", TcDraftStatus.PASSED, List.of(step("TC_CHECKOUT"))));
        Set<String> author = ReuseEligibility.authorIds(
                List.of(changedSetup, leaf), hashes, stored, matchingContext(), matchingContext());
        Assert.assertTrue(author.contains("TC_CART"));
        Assert.assertTrue(author.contains("TC_CHECKOUT"),
                "changed prerequisite must invalidate the dependent case");
    }

    @Test
    public void todoPrerequisiteInvalidatesUnchangedDescendant() {
        ManualTestCase setup = tc("TC_CART", "1. Add widget", "");
        ManualTestCase leaf = tc("TC_CHECKOUT", "1. Place order", "TC_CART");
        Map<String, String> hashes = Map.of(
                "TC_CART", setup.contentHash(),
                "TC_CHECKOUT", leaf.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_CART", draft("TC_CART", TcDraftStatus.TODO, List.of()),
                "TC_CHECKOUT", draft("TC_CHECKOUT", TcDraftStatus.PASSED, List.of(step("TC_CHECKOUT"))));
        Set<String> author = ReuseEligibility.authorIds(
                List.of(setup, leaf), hashes, stored, matchingContext(), matchingContext());
        Assert.assertTrue(author.contains("TC_CART"));
        Assert.assertTrue(author.contains("TC_CHECKOUT"));
    }

    @Test
    public void environmentChangeInvalidatesEligiblePass() {
        ManualTestCase pass = tc("TC_PASS", "1. Click Go", "");
        Map<String, String> hashes = Map.of("TC_PASS", pass.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_PASS", draft("TC_PASS", TcDraftStatus.PASSED, List.of(step("TC_PASS"))));
        ReuseEligibility.Context storedCtx = new ReuseEligibility.Context(
                "https://old.example.com", "keel", ReuseEligibility.IR_SCHEMA);
        Set<String> author = ReuseEligibility.authorIds(
                List.of(pass), hashes, stored, storedCtx,
                ReuseEligibility.current(request("https://new.example.com", AuthoringEngine.KEEL)));
        Assert.assertTrue(author.contains("TC_PASS"), "changed BASE_WEB must not reuse prior proof");
    }

    @Test
    public void engineChangeInvalidatesEligiblePass() {
        ManualTestCase pass = tc("TC_PASS", "1. Click Go", "");
        Map<String, String> hashes = Map.of("TC_PASS", pass.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_PASS", draft("TC_PASS", TcDraftStatus.PASSED, List.of(step("TC_PASS"))));
        ReuseEligibility.Context storedCtx = new ReuseEligibility.Context(
                "https://example.com", "keel", ReuseEligibility.IR_SCHEMA);
        Set<String> author = ReuseEligibility.authorIds(
                List.of(pass), hashes, stored, storedCtx,
                ReuseEligibility.current(request("https://example.com", AuthoringEngine.PRECISION)));
        Assert.assertTrue(author.contains("TC_PASS"));
    }

    @Test
    public void copyKeepsProofAndMarksProvenance() {
        TcDraft prior = draft("TC_OK", TcDraftStatus.PASSED, List.of(step("TC_OK")));
        ManualTestCase incoming = tc("TC_OK", "1. Click", "");
        TcDraft reused = ReuseEligibility.copyForReuse(prior, incoming);
        Assert.assertEquals(reused.status(), TcDraftStatus.REUSED);
        Assert.assertEquals(reused.provenSteps(), prior.provenSteps());
        Assert.assertEquals(reused.loginSteps(), prior.loginSteps());
        Assert.assertEquals(reused.evidenceDir(), prior.evidenceDir());
        Assert.assertTrue(reused.needsLoginBeforeMethod() == prior.needsLoginBeforeMethod());
        Assert.assertTrue(reused.failureReason().toLowerCase().contains("reused"));
        Assert.assertTrue(reused.failureReason().toLowerCase().contains("not a fresh"));
        Assert.assertEquals(EmitPhase.toOutcome(reused).status(), TcStatus.PASSED);
        Assert.assertEquals(EmitPhase.toOutcome(reused).provenSteps().size(), 1);
    }

    @Test
    public void proveContextRoundTripAndCorruptFileInvalidates() throws Exception {
        Path root = java.nio.file.Files.createTempDirectory("prove-context");
        ReuseEligibility.Context written = new ReuseEligibility.Context(
                "https://example.com/", "keel", ReuseEligibility.IR_SCHEMA);
        ReuseEligibility.write(root, written);
        ReuseEligibility.Context read = ReuseEligibility.read(root);
        Assert.assertTrue(ReuseEligibility.environmentMatches(read, written));
        Assert.assertEquals(read.baseUrl(), "https://example.com");

        java.nio.file.Files.writeString(root.resolve(ReuseEligibility.CONTEXT_FILE), "{not-json");
        ReuseEligibility.Context corrupt = ReuseEligibility.read(root);
        Assert.assertFalse(ReuseEligibility.environmentMatches(
                corrupt, ReuseEligibility.current(request("https://example.com", AuthoringEngine.KEEL))));
    }

    @Test
    public void missingProveContextInvalidatesReuse() {
        ManualTestCase pass = tc("TC_PASS", "1. Click Go", "");
        Map<String, String> hashes = Map.of("TC_PASS", pass.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_PASS", draft("TC_PASS", TcDraftStatus.PASSED, List.of(step("TC_PASS"))));
        Set<String> author = ReuseEligibility.authorIds(
                List.of(pass), hashes, stored, null, matchingContext());
        Assert.assertTrue(author.contains("TC_PASS"),
                "missing prove-context.json must invalidate reuse like a corrupt file");
        Assert.assertFalse(ReuseEligibility.environmentMatches(null, matchingContext()));
        Assert.assertFalse(ReuseEligibility.environmentMatches(
                ReuseEligibility.read(Path.of("definitely-missing-prove-context-dir")), matchingContext()));
    }

    @Test
    public void dependencyInvalidationIsTransitive() {
        ManualTestCase a = tc("TC_A", "1. Create cart", "");
        ManualTestCase b = tc("TC_B", "1. Open checkout", "TC_A");
        ManualTestCase c = tc("TC_C", "1. Place order", "TC_B");
        ManualTestCase changedA = tc("TC_A", "1. Create two carts", "");
        Map<String, String> hashes = Map.of(
                "TC_A", a.contentHash(),
                "TC_B", b.contentHash(),
                "TC_C", c.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_A", draft("TC_A", TcDraftStatus.PASSED, List.of(step("TC_A"))),
                "TC_B", draft("TC_B", TcDraftStatus.PASSED, List.of(step("TC_B"))),
                "TC_C", draft("TC_C", TcDraftStatus.PASSED, List.of(step("TC_C"))));
        Set<String> author = ReuseEligibility.authorIds(
                List.of(changedA, b, c), hashes, stored, matchingContext(), matchingContext());
        Assert.assertTrue(author.contains("TC_A"));
        Assert.assertTrue(author.contains("TC_B"), "direct dependent of a changed case must re-prove");
        Assert.assertTrue(author.contains("TC_C"), "transitive dependent must re-prove");
    }

    @Test
    public void credentialProfileChangeInvalidatesReuse() {
        ManualTestCase pass = tc("TC_PASS", "1. Click Go", "");
        Map<String, String> hashes = Map.of("TC_PASS", pass.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_PASS", draft("TC_PASS", TcDraftStatus.PASSED, List.of(step("TC_PASS"))));
        ConversionJobRequest demo = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "demo", "secret",
                Path.of("."), Path.of("."), Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, AuthoringEngine.KEEL);
        ConversionJobRequest other = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "other", "secret",
                Path.of("."), Path.of("."), Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, AuthoringEngine.KEEL);
        Set<String> author = ReuseEligibility.authorIds(
                List.of(pass), hashes, stored,
                ReuseEligibility.current(demo), ReuseEligibility.current(other));
        Assert.assertTrue(author.contains("TC_PASS"), "credential profile change must invalidate reuse");
    }

    @Test
    public void precisionBudgetChangeInvalidatesReuse() {
        ManualTestCase pass = tc("TC_PASS", "1. Click Go", "");
        Map<String, String> hashes = Map.of("TC_PASS", pass.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_PASS", draft("TC_PASS", TcDraftStatus.PASSED, List.of(step("TC_PASS"))));
        ConversionJobRequest fifty = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "", "",
                Path.of("."), Path.of("."), Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, AuthoringEngine.PRECISION,
                new delivery.authoring.PrecisionJobConfig(true, 50));
        ConversionJobRequest ten = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "", "",
                Path.of("."), Path.of("."), Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, AuthoringEngine.PRECISION,
                new delivery.authoring.PrecisionJobConfig(true, 10));
        Set<String> author = ReuseEligibility.authorIds(
                List.of(pass), hashes, stored,
                ReuseEligibility.current(fifty), ReuseEligibility.current(ten));
        Assert.assertTrue(author.contains("TC_PASS"), "precision execution budget change must invalidate reuse");
    }

    @Test
    public void durableEvidenceSurvivesWorkDirRetention() throws Exception {
        Path store = java.nio.file.Files.createTempDirectory("reuse-store");
        Path project = store.resolve("prj_reuse");
        Path work = java.nio.file.Files.createTempDirectory("reuse-work");
        Path claimed = work.resolve("evidence").resolve("TC_OK");
        java.nio.file.Files.createDirectories(claimed);
        java.nio.file.Files.writeString(claimed.resolve("step-001.png"), "png");
        TcDraft prior = new TcDraft(
                "TC_OK", "TC_OK", "1. Click", "ok", TcDraftStatus.PASSED,
                List.of(step("TC_OK")), List.of(), false, -1, "", "",
                claimed.toString(), 0, "https://example.com/home");
        ReuseEligibility.preserveEvidence(prior, project, work.resolve("job-2"));
        java.nio.file.Files.walk(work)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> p.toFile().delete());
        Assert.assertTrue(ReuseEligibility.evidenceAvailable(prior, project),
                "project evidence must remain after work-dir cleanup");
        TcDraft reused = ReuseEligibility.copyForReuse(prior, tc("TC_OK", "1. Click", ""), project);
        Assert.assertEquals(reused.provenSteps(), prior.provenSteps());
        Assert.assertTrue(reused.evidenceDir().contains("prj_reuse"));
        Assert.assertTrue(java.nio.file.Files.isRegularFile(
                ReuseEligibility.durableEvidenceDir(project, "TC_OK").resolve("step-001.png")));
    }
}
