package delivery.mobile.runner.execute;

import delivery.mobile.runner.evidence.XmlRedactor;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

public class MobileExecuteEngineTest {
    private final MobileExecuteEngine engine = new MobileExecuteEngine();

    @Test
    public void everySelectedCaseAccountedAndSimulatedExcluded() {
        var report = engine.run(List.of(
                new SelectedCase("a", false),
                new SelectedCase("sim", true),
                new SelectedCase("b", false)
        ), false, c -> CaseResult.passed(c.tcId()));
        assertEquals(report.results().size(), 3);
        assertEquals(report.results().get(1).outcome(), CaseOutcome.EXCLUDED_SIMULATED);
        assertEquals(report.results().get(0).outcome(), CaseOutcome.PASSED);
        assertEquals(report.results().get(2).outcome(), CaseOutcome.PASSED);
    }

    @Test
    public void failFastBlocksRemainder() {
        AtomicInteger runs = new AtomicInteger();
        var report = engine.run(List.of(
                new SelectedCase("a", false),
                new SelectedCase("b", false),
                new SelectedCase("c", false)
        ), true, c -> {
            runs.incrementAndGet();
            if (c.tcId().equals("a")) return CaseResult.failed("a", "ASSERTION_FAILED", null);
            return CaseResult.passed(c.tcId());
        });
        assertTrue(report.failFastStopped());
        assertEquals(report.results().get(1).outcome(), CaseOutcome.BLOCKED);
        assertEquals(report.results().get(2).outcome(), CaseOutcome.BLOCKED);
        assertEquals(runs.get(), 1);
    }

    @Test
    public void deviceLossInterruptsAndBlocksRemaining() {
        var report = engine.run(List.of(
                new SelectedCase("a", false),
                new SelectedCase("b", false),
                new SelectedCase("c", false)
        ), false, c -> {
            if (c.tcId().equals("a")) throw new MobileExecuteEngine.DeviceLostException("gone");
            return CaseResult.passed(c.tcId());
        });
        assertTrue(report.deviceLost());
        assertEquals(report.results().get(0).outcome(), CaseOutcome.INTERRUPTED_UNCERTAIN);
        assertEquals(report.results().get(1).outcome(), CaseOutcome.BLOCKED);
        assertEquals(report.results().get(2).outcome(), CaseOutcome.BLOCKED);
    }

    @Test
    public void noRawSecretsInResults() {
        CaseResult dirty = new CaseResult("a", CaseOutcome.FAILED, XmlRedactor.SECRET_CANARY, null, true);
        CaseResult clean = MobileExecuteEngine.sanitize(dirty);
        assertFalse(clean.failureCode().contains(XmlRedactor.SECRET_CANARY));
        assertFalse(clean.containsRawSecret());
    }
}
