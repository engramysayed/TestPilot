package delivery.job;

import delivery.agent.harness.AgentHarnessCoordinator;
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
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 2c — worker lifecycle, cancellation, and remote execution path audit tests.
 */
public class ExecuteJobRunnerExecutionRecoveryHardeningTest {

    private Path storeRoot;
    private Path workDir;

    @BeforeMethod
    public void setUp() throws Exception {
        storeRoot = Files.createTempDirectory("ejr-harden-store-");
        workDir = Files.createTempDirectory("ejr-harden-work-");
    }

    @Test
    public void remoteStyleRunner_noHarnessProperties_noGateInjected() throws Exception {
        ProvePhase prove = new ExecuteJobRunner().configureProvePhase(
                new JobProgressTracker(), storeRoot.resolve("mirror"), () -> false, minimalRequest("remote-1"));
        Assert.assertNull(gateOn(prove));
        Assert.assertNull(ExecutionRecoveryWiring.optionalGate(null));
    }

    @Test
    public void portalRunner_cancelBeforeInvestigation_skipsWithoutHarnessStart() {
        AgentHarnessProperties props = flags(true, true);
        props.setMaxRuntimeMinutes(15);
        AtomicInteger starts = new AtomicInteger();
        AgentHarnessCoordinator coordinator = coordinator(props, countingRuntime(starts));
        AtomicBoolean cancelled = new AtomicBoolean(true);
        ExecutionRecoveryEscalationGate gate =
                ExecutionRecoveryWiring.optionalGate(props, coordinator, cancelled::get);

        ExecutionInvestigationOutcome outcome = gate.tryEscalate(exhaustedRequest("job-cancel-before", "TC_01"));
        Assert.assertEquals(outcome.status(), ExecutionInvestigationOutcome.SKIPPED);
        Assert.assertEquals(outcome.detail(), "JOB_CANCELLED");
        Assert.assertEquals(starts.get(), 0);
    }

    @Test
    public void cancelDuringSlowInvestigation_propagatesJobCancelled() throws Exception {
        AgentHarnessProperties props = flags(true, true);
        props.setMaxRuntimeMinutes(15);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AgentHarnessCoordinator coordinator = coordinator(props, new SlowFakeAgentRuntime(2_000));
        ExecutionRecoveryEscalationGate gate =
                ExecutionRecoveryWiring.optionalGate(props, coordinator, cancelled::get);

        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(300);
                cancelled.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "cancel-during-investigation");
        canceller.start();

        try {
            gate.tryEscalate(exhaustedRequest("job-cancel-during", "TC_02"));
            Assert.fail("expected JobCancelledException");
        } catch (JobCancelledException expected) {
            // cooperative worker abort
        } finally {
            canceller.join();
        }
    }

    @Test
    public void slowInvestigation_workerThreadBlocked_parallelHeartbeatContinues() throws Exception {
        AgentHarnessProperties props = flags(true, true);
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                props, coordinator(props, new SlowFakeAgentRuntime(1_200)), () -> false);

        AtomicInteger heartbeats = new AtomicInteger();
        AtomicBoolean done = new AtomicBoolean();
        Thread leaseSim = new Thread(() -> {
            while (!done.get()) {
                heartbeats.incrementAndGet();
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "lease-sim");
        leaseSim.start();
        try {
            var outcome = gate.tryEscalate(exhaustedRequest("job-lease-par", "TC_03"));
            Assert.assertTrue(outcome.investigationRan());
        } finally {
            done.set(true);
            leaseSim.join(3_000);
        }
        Assert.assertTrue(heartbeats.get() >= 4,
                "background lease/progress threads must keep running while prove blocks on investigation");
    }

    @Test
    public void harnessFailureOutcome_stepAttemptStillFails() throws Exception {
        AgentHarnessProperties props = flags(true, true);
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                props, coordinator(props, new FakeAgentRuntime()), () -> false);
        ProvePhase prove = new ProvePhase(new JobProgressTracker()).withExecutionRecoveryGate(gate);

        var failInvestigate = ProvePhase.class.getDeclaredMethod(
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

        String reason = "HEAL_EXHAUSTED: still broken";
        ProvePhase.StepAttempt attempt = (ProvePhase.StepAttempt) failInvestigate.invoke(
                prove,
                new ManualTestCase("TC_F", "t", "", "1. x", "ok", "", "", "", "", "", ""),
                minimalRequest("job-fail"),
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "x", ""),
                0,
                reason,
                List.of(),
                "evidence",
                1,
                "cursor",
                "HEAL_EXHAUSTED",
                List.of(),
                true,
                true,
                null);
        Assert.assertFalse(attempt.ok());
        Assert.assertEquals(attempt.reason(), reason);
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
                tcId, "title", "", "1. Click", "see page", "", "", "", "", "", "");
        return new ExecutionRecoveryEscalationRequest(
                minimalRequest(jobId),
                tc,
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click", ""),
                0,
                "HEAL_EXHAUSTED: element not found",
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
}