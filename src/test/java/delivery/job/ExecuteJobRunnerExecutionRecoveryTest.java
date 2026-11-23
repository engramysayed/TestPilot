package delivery.job;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentRuntime;
import delivery.agent.harness.FakeAgentRuntime;
import delivery.agent.harness.AgentRunHandle;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.recovery.ExecutionInvestigationOutcome;
import delivery.agent.harness.recovery.ExecutionRecoveryEscalationGate;
import delivery.agent.harness.recovery.ExecutionRecoveryEscalationRequest;
import delivery.agent.harness.recovery.ExecutionRecoveryWiring;
import delivery.authoring.StepIntentBinder;
import delivery.excel.ManualTestCase;
import delivery.identity.TenantId;
import delivery.agent.harness.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecuteJobRunnerExecutionRecoveryTest {

    private Path storeRoot;
    private Path workDir;

    @BeforeMethod
    public void setUp() throws Exception {
        storeRoot = Files.createTempDirectory("ejr-exec-rec-store-");
        workDir = Files.createTempDirectory("ejr-exec-rec-work-");
    }

    @Test
    public void bothFlagsDisabled_optionalGateAndProveHaveNoGate() throws Exception {
        assertNoGate(null);
        assertNoGate(flags(false, false));
        assertNoGate(flags(false, true));
        assertNoGate(flags(true, false));
    }

    @Test
    public void bothFlagsEnabled_proveReceivesGate() throws Exception {
        AgentHarnessProperties props = flags(true, true);
        ProvePhase prove = runner(props).configureProvePhase(
                new JobProgressTracker(), storeRoot.resolve("mirror"), () -> false, minimalRequest("job-a"));
        ExecutionRecoveryEscalationGate gate = gateOn(prove);
        Assert.assertNotNull(gate);
        Assert.assertTrue(gate.flagsEnabled());
    }

    @Test
    public void bothFlagsEnabled_exhaustedFailureReachesHarnessThroughWiring() throws Exception {
        CountingFakeRuntime runtime = new CountingFakeRuntime();
        AgentHarnessProperties props = flags(true, true);
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(props, coordinator(props, runtime));
        Assert.assertNotNull(gate);

        var outcome = gate.tryEscalate(exhaustedRequest("job-b", "TC_B"));
        Assert.assertTrue(outcome.investigationRan());
        Assert.assertEquals(runtime.starts.get(), 1);
    }

    @Test
    public void harnessInterrupted_originalStepAttemptRemainsFailure() throws Exception {
        AgentHarnessProperties props = flags(true, true);
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                props, coordinator(props, new InterruptingFakeRuntime()));
        ProvePhase prove = new ProvePhase(new JobProgressTracker()).withExecutionRecoveryGate(gate);

        Method failInvestigate = ProvePhase.class.getDeclaredMethod(
                "failWithOptionalInvestigation",
                ManualTestCase.class,
                ConversionJobRequest.class,
                StepIntentBinder.IntentLine.class,
                int.class,
                String.class,
                List.class,
                String.class,
                int.class,
                String.class,
                String.class,
                List.class,
                boolean.class,
                boolean.class,
                drivers.WebDriverFactory.class);
        failInvestigate.setAccessible(true);

        ManualTestCase tc = new ManualTestCase(
                "TC_X", "t", "", "1. click", "ok", "", "", "", "", "", "");
        ConversionJobRequest request = minimalRequest("job-x");
        StepIntentBinder.IntentLine intent =
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "click", "");
        String reason = "HEAL_EXHAUSTED: still missing";
        ProvePhase.StepAttempt attempt = (ProvePhase.StepAttempt) failInvestigate.invoke(
                prove,
                tc,
                request,
                intent,
                0,
                reason,
                List.of(),
                "evidence",
                1,
                "cursor",
                "HEAL_EXHAUSTED: cursor",
                List.of(),
                true,
                true,
                null);

        Assert.assertFalse(attempt.ok());
        Assert.assertEquals(attempt.reason(), reason);
    }

    @Test
    public void disabledFlags_coordinatorNotUsedByOptionalGate() {
        CountingFakeRuntime runtime = new CountingFakeRuntime();
        AgentHarnessProperties off = flags(false, false);
        Assert.assertNull(ExecutionRecoveryWiring.optionalGate(off, coordinator(off, runtime)));
        Assert.assertEquals(runtime.starts.get(), 0);
    }

    private void assertNoGate(AgentHarnessProperties props) throws Exception {
        Assert.assertNull(ExecutionRecoveryWiring.optionalGate(props));
        ProvePhase prove = runner(props).configureProvePhase(
                new JobProgressTracker(), storeRoot.resolve("mirror"), () -> false, minimalRequest("job-off"));
        Assert.assertNull(gateOn(prove));
    }

    private static ExecuteJobRunner runner(AgentHarnessProperties props) {
        return new ExecuteJobRunner(props);
    }

    private static AgentHarnessProperties flags(boolean enabled, boolean escalation) {
        var p = new AgentHarnessProperties();
        p.setEnabled(enabled);
        p.setExecutionRecoveryCursorEscalationEnabled(escalation);
        p.setCursorEnabled(false);
        return p;
    }

    private ConversionJobRequest minimalRequest(String jobId) {
        return new ConversionJobRequest(
                "p1",
                Path.of("book.xlsx"),
                "https://example.com",
                "user",
                "secret",
                workDir,
                storeRoot,
                Path.of("template"),
                "NEW",
                "",
                "",
                false,
                false,
                delivery.authoring.AuthoringEngine.KEEL,
                null,
                TenantId.mint(),
                jobId);
    }

    private ExecutionRecoveryEscalationRequest exhaustedRequest(String jobId, String tcId) {
        ManualTestCase tc = new ManualTestCase(
                tcId, "title", "", "1. Click 'Login Button'", "see 'Dashboard Page'", "", "", "", "", "", "");
        return new ExecutionRecoveryEscalationRequest(
                minimalRequest(jobId),
                tc,
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login", ""),
                0,
                "HEAL_EXHAUSTED: element not found",
                "HEAL_EXHAUSTED: Cursor skip",
                "cursor",
                true,
                true,
                true,
                "evidence/" + tcId,
                "",
                "https://example.com/login",
                List.of(),
                jobId + ":" + tcId + ":0");
    }

    private static AgentHarnessCoordinator coordinator(AgentHarnessProperties props, AgentRuntime runtime) {
        EnumMap<delivery.agent.harness.AgentProviderId, AgentRuntime> map =
                new EnumMap<>(delivery.agent.harness.AgentProviderId.class);
        map.put(delivery.agent.harness.AgentProviderId.FAKE, runtime);
        return new AgentHarnessCoordinator(null, null, null, props, map);
    }

    private static ExecutionRecoveryEscalationGate gateOn(ProvePhase prove) throws Exception {
        Field f = ProvePhase.class.getDeclaredField("executionRecoveryGate");
        f.setAccessible(true);
        return (ExecutionRecoveryEscalationGate) f.get(prove);
    }

    static final class CountingFakeRuntime implements AgentRuntime {
        final AtomicInteger starts = new AtomicInteger();
        private final FakeAgentRuntime delegate = new FakeAgentRuntime();

        @Override
        public delivery.agent.harness.AgentProviderId providerId() {
            return delegate.providerId();
        }

        @Override
        public AgentRunHandle start(AgentTask task) {
            starts.incrementAndGet();
            return delegate.start(task);
        }

        @Override
        public AgentRunHandle resume(String agentId, String runId) {
            return delegate.resume(agentId, runId);
        }

        @Override
        public void cancel(String agentId, String runId) {
            delegate.cancel(agentId, runId);
        }

        @Override
        public AgentRunResult getResult(String agentId, String runId) {
            return delegate.getResult(agentId, runId);
        }
    }

    static final class InterruptingFakeRuntime implements AgentRuntime {
        @Override
        public delivery.agent.harness.AgentProviderId providerId() {
            return delivery.agent.harness.AgentProviderId.FAKE;
        }

        @Override
        public AgentRunHandle start(AgentTask task) {
            return new AgentRunHandle("a", "r", java.time.Instant.now());
        }

        @Override
        public AgentRunHandle resume(String agentId, String runId) {
            return new AgentRunHandle(agentId, runId, java.time.Instant.now());
        }

        @Override
        public void cancel(String agentId, String runId) {
        }

        @Override
        public AgentRunResult getResult(String agentId, String runId) {
            throw new AgentHarnessException("INTERRUPTED");
        }
    }
}