package delivery.job;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PrecisionBatchExecutionTest {
    private String previousWait;
    @org.testng.annotations.BeforeMethod public void fastWait() {
        previousWait = System.getProperty(PostActionSettle.PROPERTY);
        System.setProperty(PostActionSettle.PROPERTY, "0");
    }
    @org.testng.annotations.AfterMethod(alwaysRun = true) public void restoreWait() {
        if (previousWait == null) System.clearProperty(PostActionSettle.PROPERTY);
        else System.setProperty(PostActionSettle.PROPERTY, previousWait);
    }
    private final Path evidence = Path.of("target/batch-test");
    private List<List<ProvenStep>> rows() { return List.of(List.of(), List.of(), List.of()); }
    @Test public void stablePageExecutesEachIntentOnce() {
        var execution = mock(TcExecutionService.class);
        when(execution.execute(anyString(), anyList(), any())).thenReturn(new TcOutcome("TC", TcStatus.PASSED, List.of(), "", evidence));
        var result = ProvePhase.executePrecisionBound("TC", rows(), execution, evidence, "batch", () -> false, () -> "/form<input>");
        assertTrue(result.attempt().ok()); assertEquals(result.intentsConsumed(), 3);
        verify(execution, times(3)).execute(anyString(), anyList(), any());
    }
    @Test public void partialFailureNeverReplaysOrExecutesRemainingIntent() {
        var execution = mock(TcExecutionService.class);
        when(execution.execute(anyString(), anyList(), any())).thenReturn(
            new TcOutcome("TC", TcStatus.PASSED, List.of(), "", evidence),
            new TcOutcome("TC", TcStatus.PARTIAL, List.of(), "second failed", evidence));
        var result = ProvePhase.executePrecisionBound("TC", rows(), execution, evidence, "batch", () -> false, () -> "/form");
        assertTrue(result.usedBatch()); assertFalse(result.attempt().ok());
        assertEquals(result.intentsConsumed(), 2);
        verify(execution, times(2)).execute(anyString(), anyList(), any());
    }
    @Test public void transportExceptionStopsWithoutFallback() {
        var execution = mock(TcExecutionService.class);
        when(execution.execute(anyString(), anyList(), any())).thenThrow(new IllegalStateException("lost session"));
        var result = ProvePhase.executePrecisionBound("TC", rows(), execution, evidence, "batch", () -> false, () -> "/form");
        assertTrue(result.usedBatch()); assertFalse(result.attempt().ok());
        verify(execution, times(1)).execute(anyString(), anyList(), any());
    }
    @Test public void navigationDiscardsRemainingBindings() {
        var execution = mock(TcExecutionService.class);
        when(execution.execute(anyString(), anyList(), any())).thenReturn(new TcOutcome("TC", TcStatus.PASSED, List.of(), "", evidence));
        var calls = new AtomicInteger();
        var result = ProvePhase.executePrecisionBound("TC", rows(), execution, evidence, "batch", () -> false,
            () -> calls.getAndIncrement() == 0 ? "/form<input>" : "/form<div>changed</div>");
        assertTrue(result.attempt().ok()); assertEquals(result.intentsConsumed(), 1);
        verify(execution, times(1)).execute(anyString(), anyList(), any());
    }
    @Test public void cancellationPreventsExecution() {
        var execution = mock(TcExecutionService.class);
        var result = ProvePhase.executePrecisionBound("TC", rows(), execution, evidence, "batch", () -> true, () -> "/form");
        assertFalse(result.attempt().ok()); verifyNoInteractions(execution);
    }
    @Test public void clickAndTypingSharePlanningBatch() {
        var type = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.TYPE_FIELD, "Enter email");
        var click = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Next");
        var intents = List.of(type, type, click, type);
        assertEquals(ProvePhase.precisionBatchEnd(intents, 0), 4);
        assertEquals(ProvePhase.precisionBatchEnd(intents, 2), 4);
    }
    @Test public void changedRemainingTargetReplansOnlyUnexecutedSuffix() {
        var execution = mock(TcExecutionService.class);
        when(execution.execute(anyString(), anyList(), any())).thenReturn(new TcOutcome("TC", TcStatus.PASSED, List.of(), "", evidence));
        var checks = new AtomicInteger();
        var result = ProvePhase.executePrecisionBound("TC", rows(), execution, evidence, "batch", () -> false,
                () -> "/form", () -> {}, steps -> checks.getAndIncrement() == 0);
        assertTrue(result.attempt().ok()); assertEquals(result.intentsConsumed(), 1);
        verify(execution, times(1)).execute(anyString(), anyList(), any());
    }
}
