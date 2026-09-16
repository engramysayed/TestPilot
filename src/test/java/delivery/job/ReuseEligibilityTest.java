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
    public void passwordRotationWithSameUsernameInvalidatesReuse() throws Exception {
        ManualTestCase pass = tc("TC_PASS", "1. Click Go", "");
        Map<String, String> hashes = Map.of("TC_PASS", pass.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_PASS", draft("TC_PASS", TcDraftStatus.PASSED, List.of(step("TC_PASS"))));
        ConversionJobRequest original = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "demo", "secret",
                Path.of("."), Path.of("."), Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, AuthoringEngine.KEEL);
        ConversionJobRequest rotated = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "demo", "rotated-secret",
                Path.of("."), Path.of("."), Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, AuthoringEngine.KEEL);
        Set<String> author = ReuseEligibility.authorIds(
                List.of(pass), hashes, stored,
                ReuseEligibility.current(original), ReuseEligibility.current(rotated));
        Assert.assertTrue(author.contains("TC_PASS"),
                "password rotation with the same username must invalidate reuse");
        Assert.assertFalse(
                ReuseEligibility.environmentMatches(
                        ReuseEligibility.current(original), ReuseEligibility.current(rotated)),
                "prove-context must not match after password rotation");
        Path secretRoot = java.nio.file.Files.createTempDirectory("prove-secret");
        ReuseEligibility.Context writtenContext = ReuseEligibility.current(original);
        ReuseEligibility.write(secretRoot, writtenContext);
        String json = java.nio.file.Files.readString(
                secretRoot.resolve(ReuseEligibility.CONTEXT_FILE));
        Assert.assertFalse(json.contains("secret"),
                "raw password must never be stored in prove-context");
        Assert.assertFalse(json.contains("rotated-secret"), json);
        Assert.assertFalse(json.contains("credentialSecretFingerprint"),
                "unsalted password hashes must not be stored in prove-context:\n" + json);
        Assert.assertTrue(json.contains("credentialRevision"), json);
        Assert.assertTrue(writtenContext.credentialRevision().startsWith("cred_"),
                writtenContext.credentialRevision());
        Assert.assertFalse(json.toLowerCase().contains(sha256Hex("secret")),
                "SHA-256 of the password must not appear in prove-context:\n" + json);
        Assert.assertFalse(
                java.nio.file.Files.exists(secretRoot.resolve(CredentialRevision.BINDING_FILE)),
                "MAC binding belongs in the server store, not beside prove-context written for tests");
    }

    @Test
    public void credentialRevisionIsStableThenRotatesAndStaysOffCustomerArtifacts() throws Exception {
        Path store = java.nio.file.Files.createTempDirectory("cred-rev-store");
        ConversionJobRequest first = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "demo", "secret",
                Path.of("."), store, Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, AuthoringEngine.KEEL);
        ConversionJobRequest again = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "demo", "secret",
                Path.of("."), store, Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, AuthoringEngine.KEEL);
        ConversionJobRequest rotated = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "demo", "rotated-secret",
                Path.of("."), store, Path.of("customer-framework-template"),
                "UPDATE", "", "", false, false, AuthoringEngine.KEEL);
        ReuseEligibility.Context firstCtx = ReuseEligibility.current(first);
        String firstRev = firstCtx.credentialRevision();
        String againRev = ReuseEligibility.current(again).credentialRevision();
        String rotatedRev = ReuseEligibility.current(rotated).credentialRevision();
        Assert.assertEquals(againRev, firstRev, "same credentials must keep the same revision id");
        Assert.assertNotEquals(rotatedRev, firstRev, "password rotation must mint a new revision id");
        Path project = new delivery.store.ProjectStore(store, "https://example.com").projectRoot("prj_reuse");
        String binding = java.nio.file.Files.readString(project.resolve(CredentialRevision.BINDING_FILE));
        Assert.assertFalse(binding.contains("secret"), binding);
        Assert.assertFalse(binding.contains("rotated-secret"), binding);
        Path context = java.nio.file.Files.createTempDirectory("cred-rev-ctx");
        ReuseEligibility.write(context, firstCtx);
        String prove = java.nio.file.Files.readString(context.resolve(ReuseEligibility.CONTEXT_FILE));
        Assert.assertTrue(prove.contains(firstRev), prove);
        Assert.assertFalse(prove.contains("secretMac"), prove);
        Assert.assertFalse(
                java.nio.file.Files.exists(context.resolve(CredentialRevision.BINDING_FILE)));
    }

    @Test
    public void localLlmConfigurationChangeInvalidatesReuse() {
        ManualTestCase pass = tc("TC_PASS", "1. Click Go", "");
        Map<String, String> hashes = Map.of("TC_PASS", pass.contentHash());
        Map<String, TcDraft> stored = Map.of(
                "TC_PASS", draft("TC_PASS", TcDraftStatus.PASSED, List.of(step("TC_PASS"))));
        ConversionJobRequest ollama = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "", "",
                Path.of("."), Path.of("."), Path.of("customer-framework-template"),
                "UPDATE", "http://127.0.0.1:11434", "qwen2.5", false, false, AuthoringEngine.KEEL);
        ConversionJobRequest otherModel = new ConversionJobRequest(
                "prj_reuse", Path.of("unused.xlsx"), "https://example.com", "", "",
                Path.of("."), Path.of("."), Path.of("customer-framework-template"),
                "UPDATE", "http://127.0.0.1:11434", "llama3.1", false, false, AuthoringEngine.KEEL);
        Set<String> author = ReuseEligibility.authorIds(
                List.of(pass), hashes, stored,
                ReuseEligibility.current(ollama), ReuseEligibility.current(otherModel));
        Assert.assertTrue(author.contains("TC_PASS"),
                "local LLM URL/model change must invalidate reuse");
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

    @Test
    public void occurrenceEvidenceCountsAsAvailableForReuse() throws Exception {
        Path project = java.nio.file.Files.createTempDirectory("reuse-occ");
        Path occ = ReuseEligibility.durableEvidenceDir(project, "TC_CART")
                .getParent()
                .resolve(OccurrenceIdentity.folder("TC_CART", 2));
        java.nio.file.Files.createDirectories(occ);
        java.nio.file.Files.writeString(occ.resolve("step-001.png"), "png");
        TcDraft prior = draft("TC_CART", TcDraftStatus.PASSED, List.of(step("TC_CART")));
        prior = new TcDraft(
                prior.tcId(), prior.title(), prior.stepsText(), prior.expectedResult(),
                prior.status(), prior.provenSteps(), prior.loginSteps(),
                prior.needsLoginBeforeMethod(), prior.blockerStepIndex(), prior.blockerIntent(),
                prior.failureReason(), occ.toString(), prior.retryCountOnBlocker(), prior.lastPageUrl());
        Assert.assertTrue(ReuseEligibility.evidenceAvailable(prior, project),
                "occurrence-scoped evidence must satisfy UPDATE reuse");
        Path work = java.nio.file.Files.createTempDirectory("reuse-occ-work");
        ReuseEligibility.preserveEvidence(prior, project, work);
        Assert.assertTrue(java.nio.file.Files.isRegularFile(
                ReuseEligibility.durableEvidenceDir(project, "TC_CART").resolve("step-001.png")));
    }

    @Test
    public void occurrenceFoldersStayDistinctAfterUpdatePreserveAndRetention() throws Exception {
        Path store = java.nio.file.Files.createTempDirectory("reuse-occ-store");
        Path project = store.resolve("prj_reuse");
        Path evidence = project.resolve("evidence");
        Path occ1 = evidence.resolve(OccurrenceIdentity.folder("TC_CART", 1));
        Path occ2 = evidence.resolve(OccurrenceIdentity.folder("TC_CART", 2));
        java.nio.file.Files.createDirectories(occ1);
        java.nio.file.Files.createDirectories(occ2);
        java.nio.file.Files.writeString(occ1.resolve("step-001.png"), "first");
        java.nio.file.Files.writeString(occ2.resolve("step-001.png"), "second");
        TcDraft prior = new TcDraft(
                "TC_CART", "TC_CART", "1. Click", "ok", TcDraftStatus.PASSED,
                List.of(step("TC_CART")), List.of(), false, -1, "", "",
                occ2.toString(), 0, "https://example.com/cart");
        Path work = java.nio.file.Files.createTempDirectory("reuse-occ-work");
        ReuseEligibility.preserveEvidence(prior, project, work);
        Path agedWorkChild = work.resolve("old-job");
        java.nio.file.Files.createDirectories(agedWorkChild);
        java.nio.file.Files.setLastModifiedTime(agedWorkChild,
                java.nio.file.attribute.FileTime.from(java.time.Instant.now().minus(60, java.time.temporal.ChronoUnit.DAYS)));
        delivery.portal.DeliveryPortalProperties props = new delivery.portal.DeliveryPortalProperties();
        props.setStoreRoot(store.toString());
        props.setWorkDir(work.toString());
        props.getRetention().setDays(14);
        new delivery.portal.service.RetentionSweeper(props).sweep();

        Assert.assertEquals(java.nio.file.Files.readString(occ1.resolve("step-001.png")), "first");
        Assert.assertEquals(java.nio.file.Files.readString(occ2.resolve("step-001.png")), "second");
        Assert.assertNotEquals(occ1, occ2);
        Assert.assertTrue(ReuseEligibility.evidenceAvailable(prior, project));
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
