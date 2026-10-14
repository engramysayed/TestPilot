package delivery.job;

import delivery.ir.TcDraftStatus;
import org.json.JSONObject;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.*;

public class HonestRetryMetricsTest {

    @Test
    public void classifiesInfraReasons() {
        for (String r : List.of("Browser session died/navigate failed", "unknown error: net::ERR_CONNECTION_REFUSED",
                "invalid session id", "LEASE_LOST for job 12", "INTERRUPTED_UNCERTAIN", "Ollama unavailable",
                "model not found: qwen", "HTTP 503 from provider", "chrome not reachable",
                "java.io.IOException: No space left on device")) {
            assertEquals(RetryCategory.INFRA, RetryCategory.classify(r), r);
        }
    }

    @Test
    public void classifiesTestReasonsAndDefaultsUnknownToTest() {
        for (String r : List.of("no DOM candidate for 'Save'", "HEAL_EXHAUSTED: execute still failed after Cursor heal: timeout",
                "assertion failed: expected Welcome", "ambiguous: 3 candidates", "element not interactable",
                "something weird", "", "AMBIGUOUS: two buttons")) {
            assertEquals(RetryCategory.TEST, RetryCategory.classify(r), r);
        }
        assertEquals(RetryCategory.TEST, RetryCategory.classify((String) null));
        assertEquals(RetryCategory.RECOVERY, RetryCategory.classify("Recovered to Excel open-path; retry bind"));
    }

    @Test
    public void classifiesThrowableCauseChain() {
        Exception e = new RuntimeException("wrapper", new java.net.ConnectException("refused"));
        assertEquals(RetryCategory.INFRA, RetryCategory.classify(e));
        assertEquals(RetryCategory.TEST, RetryCategory.classify(new IllegalStateException("bad locator")));
    }

    @Test
    public void infraFailuresDoNotConsumeTestBudget() {
        RetryBudget b = new RetryBudget(2, 2);
        assertTrue(b.allowRetry(RetryCategory.INFRA));
        assertTrue(b.allowRetry(RetryCategory.INFRA));
        assertTrue(b.allowRetry(RetryCategory.TEST));
        assertTrue(b.allowRetry(RetryCategory.TEST));
        assertFalse(b.allowRetry(RetryCategory.TEST));
        assertEquals(RetryCategory.TEST, b.exhaustedOn());
        assertEquals(2, b.testRetriesUsed());
        assertEquals(2, b.infraRetriesUsed());
    }

    @Test
    public void infraBudgetIsBounded() {
        RetryBudget b = new RetryBudget(2, 1);
        assertTrue(b.allowRetry(RetryCategory.INFRA));
        assertFalse(b.allowRetry(RetryCategory.INFRA));
        assertEquals(RetryCategory.INFRA, b.exhaustedOn());
        assertEquals(0, b.testRetriesUsed());
    }

    @Test
    public void recoveryConsumesTestBudgetButIsNotATestFailure() {
        RetryBudget b = new RetryBudget(2, 2);
        assertTrue(b.allowRetry(RetryCategory.RECOVERY));
        assertEquals(1, b.testRetriesUsed());
        assertEquals(0, b.testFailures());
        assertTrue(b.allowRetry(RetryCategory.TEST));
        assertFalse(b.allowRetry(RetryCategory.RECOVERY));
    }

    @Test
    public void verdicts() {
        assertEquals(RetryVerdict.PASSED, RetryVerdict.of(TcDraftStatus.PASSED, 0, ""));
        assertEquals(RetryVerdict.FLAKY, RetryVerdict.of(TcDraftStatus.PASSED, 1, ""));
        assertEquals(RetryVerdict.FAILED, RetryVerdict.of(TcDraftStatus.TODO, 0, "no DOM candidate"));
        assertEquals(RetryVerdict.INFRA_ERROR, RetryVerdict.of(TcDraftStatus.TODO, 0, "Browser session died/navigate failed"));
        assertEquals(RetryVerdict.FAILED, RetryVerdict.of(TcDraftStatus.PARTIAL, 0, "semantic gate rejected"));
        assertEquals(RetryVerdict.REUSED, RetryVerdict.of(TcDraftStatus.REUSED, 0, ""));
        assertTrue(RetryVerdict.FLAKY.finalPass());
        assertFalse(RetryVerdict.INFRA_ERROR.finalPass());
    }

    @Test
    public void runHealthRates() {
        RunHealth h = RunHealth.of(List.of(RetryVerdict.PASSED, RetryVerdict.PASSED, RetryVerdict.FLAKY,
                RetryVerdict.FAILED, RetryVerdict.INFRA_ERROR, RetryVerdict.REUSED));
        assertEquals(0.5, h.firstPassRate(), 1e-9);
        assertEquals(0.75, h.finalPassRate(), 1e-9);
        assertEquals(1, h.flaky());
        assertEquals(1, h.infraError());
        assertEquals("first-pass 50.0%, final-pass 75.0%, flaky 1, healed 0, infra errors 1, config errors 0", h.summary());
    }

    @Test
    public void runHealthLegacyExcludedFromFirstPassAndEmptyIsNull() {
        RunHealth h = RunHealth.of(List.of(RetryVerdict.LEGACY_PASSED, RetryVerdict.FAILED));
        assertEquals(0.0, h.firstPassRate(), 1e-9);
        assertEquals(0.5, h.finalPassRate(), 1e-9);
        RunHealth empty = RunHealth.of(List.of(RetryVerdict.INFRA_ERROR));
        assertNull(empty.firstPassRate());
        assertNull(empty.finalPassRate());
        assertTrue(empty.summary().contains("n/a"));
    }

    @Test
    public void storeRoundTripAndLegacyFallback() throws Exception {
        Path work = Files.createTempDirectory("retry-metrics");
        RetryRecordStore store = new RetryRecordStore(work);
        store.write("TC-1", RetryVerdict.FLAKY, 1, 1, 2, "");
        assertEquals(RetryVerdict.FLAKY, store.readOrLegacy("TC-1", TcDraftStatus.PASSED, ""));
        assertEquals(RetryVerdict.LEGACY_PASSED, store.readOrLegacy("TC-OLD", TcDraftStatus.PASSED, ""));
        assertEquals(RetryVerdict.INFRA_ERROR, store.readOrLegacy("TC-X", TcDraftStatus.TODO, "invalid session id"));
        store.writeRun(RunHealth.of(List.of(RetryVerdict.PASSED)));
        JSONObject run = new JSONObject(Files.readString(work.resolve("retry/_run.json")));
        assertEquals(1.0, run.getDouble("firstPassRate"), 1e-9);
        assertFalse(Files.exists(work.resolve("ir")), "sidecars must not land in ir/ (TcDraftStore.readAll)");
    }

    @Test
    public void progressTrackerCountsVerdicts() {
        JobProgressTracker p = new JobProgressTracker();
        p.recordOutcome(TcDraftStatus.PASSED);
        p.recordVerdict(RetryVerdict.FLAKY);
        p.recordOutcome(TcDraftStatus.TODO);
        p.recordVerdict(RetryVerdict.INFRA_ERROR);
        assertEquals(1, p.passed());
        assertEquals(1, p.flaky());
        assertEquals(1, p.infraError());
        assertEquals(0.0, p.health().firstPassRate(), 1e-9);
    }

    @Test
    public void legacyResultConstructor() {
        ExecuteJobResult r = new ExecuteJobResult(1, 2, "m");
        assertNull(r.firstPassRate());
        assertEquals(0, r.flaky());
    }

    @Test
    public void classifiesConfigReasons() {
        for (String r : List.of("LOGIN_FAILED: still on login form", "Invalid credentials for user qa",
                "Missing credential KEEL_PASS", "secret not found: tenant/app", "Base URL not set",
                "environment variable KEEL_USER is empty")) {
            assertEquals(RetryCategory.classify(r), RetryCategory.CONFIG, r);
        }
        assertEquals(RetryCategory.classify("HEAL_EXHAUSTED: login button missing"), RetryCategory.TEST);
    }

    @Test
    public void configIsNeverRetried() {
        RetryBudget b = new RetryBudget(2, 2);
        assertFalse(b.allowRetry(RetryCategory.CONFIG));
        assertEquals(b.exhaustedOn(), RetryCategory.CONFIG);
        assertEquals(b.testRetriesUsed(), 0);
    }

    @Test
    public void infraBackoffIsTwoThenFiveSecondsAndInjectable() {
        List<Long> slept = new java.util.ArrayList<>();
        RetryBudget b = new RetryBudget(2, 2, RetryBudget.DEFAULT_INFRA_BACKOFF_MS, slept::add);
        assertTrue(b.allowRetry(RetryCategory.INFRA));
        assertTrue(b.allowRetry(RetryCategory.TEST));
        assertTrue(b.allowRetry(RetryCategory.INFRA));
        assertFalse(b.allowRetry(RetryCategory.INFRA));
        assertEquals(slept, List.of(2000L, 5000L));
        assertEquals(b.backoffFor(3), 5000L);
    }

    @Test
    public void interruptedBackoffStopsRetrying() {
        RetryBudget b = new RetryBudget(2, 2, new long[]{10L}, ms -> { throw new InterruptedException(); });
        assertFalse(b.allowRetry(RetryCategory.INFRA));
        assertTrue(Thread.interrupted());
    }

    @Test
    public void healedAndConfigVerdicts() {
        assertEquals(RetryVerdict.of(TcDraftStatus.PASSED, 0, "ollama", ""), RetryVerdict.HEALED);
        assertEquals(RetryVerdict.of(TcDraftStatus.PASSED, 0, "none", ""), RetryVerdict.PASSED);
        assertEquals(RetryVerdict.of(TcDraftStatus.PASSED, 1, "cursor", ""), RetryVerdict.FLAKY);
        assertEquals(RetryVerdict.of(TcDraftStatus.TODO, 0, "none", "LOGIN_FAILED: bad password"),
                RetryVerdict.CONFIG_ERROR);
        assertEquals(RetryVerdict.of(TcDraftStatus.TODO, 0, "no DOM candidate for 'Save'"), RetryVerdict.FAILED);
        assertEquals(RetryVerdict.HEALED.badgeKey(), "healed");
        assertEquals(RetryVerdict.CONFIG_ERROR.badgeKey(), "config");
        assertEquals(RetryVerdict.INFRA_ERROR.badgeKey(), "infra");
        assertEquals(RetryVerdict.LEGACY_PASSED.badgeKey(), "legacy");
    }

    @Test
    public void healedCountsFinalNotFirstAndConfigExcluded() {
        RunHealth h = RunHealth.of(List.of(RetryVerdict.PASSED, RetryVerdict.HEALED,
                RetryVerdict.CONFIG_ERROR, RetryVerdict.CONFIG_ERROR), 0);
        assertEquals(h.firstPassRate(), 0.5, 1e-9);
        assertEquals(h.finalPassRate(), 1.0, 1e-9);
        assertEquals(h.configError(), 2);
        assertEquals(h.healed(), 1);
        assertEquals(h.toJson().getInt("configError"), 2);
    }

    @Test
    public void reusedFlakyIsFlaggedAndChainsThroughReuse() throws Exception {
        Path prev = Files.createTempDirectory("retry-prev");
        new RetryRecordStore(prev).write("TC-F", RetryVerdict.FLAKY, 1, 1, 0, "");
        assertEquals(RetryRecordStore.priorVerdict(prev, "TC-F"), RetryVerdict.FLAKY);
        assertEquals(RetryRecordStore.priorVerdict(prev, "TC-NONE"), RetryVerdict.LEGACY_PASSED);
        Path cur = Files.createTempDirectory("retry-cur");
        RetryRecordStore store = new RetryRecordStore(cur);
        assertTrue(store.writeReused("TC-F", RetryRecordStore.priorVerdict(prev, "TC-F")));
        JSONObject rec = new JSONObject(Files.readString(cur.resolve("retry").resolve(
                delivery.ir.TcIdentity.storageKey("TC-F") + ".json")));
        assertEquals(rec.getString("verdict"), "REUSED");
        assertEquals(rec.getString("reusedFromVerdict"), "FLAKY");
        assertTrue(rec.getBoolean("reusedFlaky"));
        // reuse of a reuse keeps the flag
        assertEquals(RetryRecordStore.priorVerdict(cur, "TC-F"), RetryVerdict.FLAKY);
        assertEquals(RunHealth.of(List.of(RetryVerdict.REUSED), 1).reusedFlaky(), 1);
    }
}
