package delivery.job;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentRunHandle;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentRuntime;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.FakeAgentRuntime;
import delivery.agent.harness.SlowFakeAgentRuntime;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConversionJobRunnerExecutionRecoveryTest {

    private Path storeRoot;
    private Path workDir;

    @BeforeMethod
    public void setUp() throws Exception {
        storeRoot = Files.createTempDirectory("cjr-recovery-store-");
        workDir = Files.createTempDirectory("cjr-recovery-work-");
    }

    @Test
    public void flagsDisabled_noGateOnProve() throws Exception {
        assertNoGate(null);
        assertNoGate(flags(false, false));
        assertNoGate(flags(false, true));
        assertNoGate(flags(true, false));
    }

    @Test
    public void bothFlagsEnabled_proveReceivesSharedGate() throws Exception {
        var runner = new ConversionJobRunner(flags(true, true), Duration.ofMinutes(5));
        ProvePhase prove = runner.configureProvePhase(new JobProgressTracker(), () -> false, minimalRequest("conv-1"));
        ExecutionRecoveryEscalationGate gate = gateOn(prove);
        Assert.assertNotNull(gate);
        Assert.assertTrue(gate.flagsEnabled());
    }

    @Test
    public void exhaustedFailure_invokesSharedBridge() {
        AtomicInteger starts = new AtomicInteger();
        AgentHarnessProperties props = flags(true, true);
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                props, coordinator(props, countingRuntime(starts)), () -> false, Duration.ofMinutes(5));
        Assert.assertNotNull(gate);
        var outcome = gate.tryEscalate(exhaustedRequest("conv-bridge", "TC_C"));
        Assert.assertTrue(outcome.investigationRan());
        Assert.assertEquals(starts.get(), 1);
    }

    @Test
    public void notExhausted_doesNotInvokeHarness() {
        AtomicInteger starts = new AtomicInteger();
        AgentHarnessProperties props = flags(true, true);
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                props, coordinator(props, countingRuntime(starts)), () -> false);
        var outcome = gate.tryEscalate(new ExecutionRecoveryEscalationRequest(
                minimalRequest("conv-ne"),
                new ManualTestCase("TC_N", "t", "", "1", "ok", "", "", "", "", "", ""),
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "x", ""),
                0,
                "element not found",
                "",
                "ollama",
                false,
                false,
                false,
                "evidence",
                "",
                "",
                List.of(),
                "dedup-ne"));
        Assert.assertEquals(outcome.status(), ExecutionInvestigationOutcome.SKIPPED);
        Assert.assertEquals(starts.get(), 0);
    }

    @Test
    public void cancelBeforeInvestigation_skipsHarness() {
        AgentHarnessProperties props = flags(true, true);
        AtomicInteger starts = new AtomicInteger();
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                props, coordinator(props, countingRuntime(starts)), () -> true);
        var outcome = gate.tryEscalate(exhaustedRequest("conv-cancel", "TC_X"));
        Assert.assertEquals(outcome.detail(), "JOB_CANCELLED");
        Assert.assertEquals(starts.get(), 0);
    }

    @Test
    public void cancelDuringInvestigation_propagatesJobCancelled() throws Exception {
        AgentHarnessProperties props = flags(true, true);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                props, coordinator(props, new SlowFakeAgentRuntime(1_800)),
                cancelled::get, Duration.ofMinutes(15));
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(250);
                cancelled.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.start();
        try {
            gate.tryEscalate(exhaustedRequest("conv-cancel-mid", "TC_M"));
            Assert.fail("expected JobCancelledException");
        } catch (JobCancelledException expected) {
            // safe worker abort
        } finally {
            t.join();
        }
    }

    @Test
    public void leaseBudgetCap_timesOutBeforeSlowHarnessCompletes() {
        AgentHarnessProperties props = flags(true, true);
        props.setMaxRuntimeMinutes(15);
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                props,
                coordinator(props, new SlowFakeAgentRuntime(3_000)),
                () -> false,
                Duration.ofMillis(350));
        var outcome = gate.tryEscalate(exhaustedRequest("conv-budget", "TC_B"));
        Assert.assertEquals(outcome.status(), ExecutionInvestigationOutcome.HARNESS_FAILED);
        Assert.assertTrue(outcome.detail() != null && outcome.detail().contains("SIDECAR_TIMEOUT"));
    }

    @Test
    public void harnessInterrupted_stepAttemptRemainsFailed() throws Exception {
        AgentHarnessProperties props = flags(true, true);
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                props, coordinator(props, new InterruptingFakeRuntime()), () -> false);
        ProvePhase prove = new ProvePhase(new JobProgressTracker()).withExecutionRecoveryGate(gate);
        var m = ProvePhase.class.getDeclaredMethod(
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
        m.setAccessible(true);
        String reason = "HEAL_EXHAUSTED: conv fail";
        ProvePhase.StepAttempt attempt = (ProvePhase.StepAttempt) m.invoke(
                prove,
                new ManualTestCase("TC_I", "t", "", "1", "ok", "", "", "", "", "", ""),
                minimalRequest("conv-int"),
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "x", ""),
                0, reason, List.of(), "evidence", 1, "cursor", "HEAL_EXHAUSTED",
                List.of(), true, true, null);
        Assert.assertFalse(attempt.ok());
        Assert.assertEquals(attempt.reason(), reason);
    }

    @Test
    public void jobHarnessBudget_matchesConversionWorkerFormula() {
        java.time.Instant until = java.time.Instant.now().plusSeconds(600);
        Duration budget = JobHarnessBudget.remainingLeaseBudget(until);
        Assert.assertTrue(budget.toSeconds() >= 560 && budget.toSeconds() <= 580);
    }

    private void assertNoGate(AgentHarnessProperties props) throws Exception {
        ProvePhase prove = new ConversionJobRunner(props, Duration.ofMinutes(1))
                .configureProvePhase(new JobProgressTracker(), () -> false, minimalRequest("off"));
        Assert.assertNull(gateOn(prove));
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
        return new ExecutionRecoveryEscalationRequest(
                minimalRequest(jobId),
                new ManualTestCase(tcId, "t", "", "1. click", "ok", "", "", "", "", "", ""),
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click", ""),
                0,
                "HEAL_EXHAUSTED: missing",
                "HEAL_EXHAUSTED",
                "cursor",
                true,
                true,
                true,
                "evidence/" + tcId,
                "",
                "https://example.com",
                List.of(),
                jobId + ":" + tcId + ":0");
    }

    private static AgentHarnessCoordinator coordinator(AgentHarnessProperties props, AgentRuntime runtime) {
        EnumMap<delivery.agent.harness.AgentProviderId, AgentRuntime> map =
                new EnumMap<>(delivery.agent.harness.AgentProviderId.class);
        map.put(delivery.agent.harness.AgentProviderId.FAKE, runtime);
        return new AgentHarnessCoordinator(null, null, null, props, map);
    }

    private static AgentRuntime countingRuntime(AtomicInteger starts) {
        FakeAgentRuntime delegate = new FakeAgentRuntime();
        return new AgentRuntime() {
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
        };
    }

    private static ExecutionRecoveryEscalationGate gateOn(ProvePhase prove) throws Exception {
        Field f = ProvePhase.class.getDeclaredField("executionRecoveryGate");
        f.setAccessible(true);
        return (ExecutionRecoveryEscalationGate) f.get(prove);
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