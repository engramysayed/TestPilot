package delivery.agent.harness.recovery;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentRuntime;
import delivery.agent.harness.FakeAgentRuntime;
import delivery.agent.harness.AgentRunHandle;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.AgentHarnessException;
import delivery.authoring.StepIntentBinder;
import delivery.excel.ManualTestCase;
import delivery.job.ConversionJobRequest;
import delivery.agent.harness.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutionRecoveryEscalationGateTest {

    private Path storeRoot;
    private Path workDir;

    @BeforeMethod
    public void setUp() throws Exception {
        storeRoot = Files.createTempDirectory("exec-recovery-store-");
        workDir = Files.createTempDirectory("exec-recovery-work-");
    }

    @Test
    public void flagsDisabled_skipsWithoutHarnessCall() {
        CountingFakeRuntime runtime = new CountingFakeRuntime();
        var gate = gateFor(runtime, flags(false, false));
        var req = exhaustedRequest("job1", "TC_01");
        var outcome = gate.tryEscalate(req);
        Assert.assertEquals(outcome.status(), ExecutionInvestigationOutcome.SKIPPED);
        Assert.assertEquals(runtime.starts.get(), 0);
    }

    @Test
    public void flagsEnabled_exhaustedFailure_invokesHarness() {
        CountingFakeRuntime runtime = new CountingFakeRuntime();
        var gate = gateFor(runtime, flags(true, true));
        var outcome = gate.tryEscalate(exhaustedRequest("job2", "TC_02"));
        Assert.assertEquals(outcome.status(), ExecutionInvestigationOutcome.INVESTIGATED);
        Assert.assertEquals(runtime.starts.get(), 1);
    }

    @Test
    public void ineligible_assertIntent_noHarness() {
        CountingFakeRuntime runtime = new CountingFakeRuntime();
        var gate = gateFor(runtime, flags(true, true));
        var base = exhaustedRequest("job3", "TC_03");
        var req = new ExecutionRecoveryEscalationRequest(
                base.jobRequest(), base.testCase(),
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.ASSERT_VISIBLE, "confirm text", ""),
                base.intentIndex(), base.originalFailure(), base.healSkipReason(), base.healTier(),
                base.healPipelineExhausted(), base.ollamaHealAttempted(), base.cursorHealAttempted(),
                base.evidenceDir(), base.screenshotPath(), base.currentUrl(), base.failedLocators(),
                base.deduplicationKey());
        var outcome = gate.tryEscalate(req);
        Assert.assertEquals(outcome.status(), ExecutionInvestigationOutcome.SKIPPED);
        Assert.assertEquals(runtime.starts.get(), 0);
    }

    @Test
    public void notExhausted_noEscalation() {
        CountingFakeRuntime runtime = new CountingFakeRuntime();
        var gate = gateFor(runtime, flags(true, true));
        var req = exhaustedRequest("job4", "TC_04");
        var notExhausted = new ExecutionRecoveryEscalationRequest(
                req.jobRequest(), req.testCase(), req.intent(), req.intentIndex(),
                req.originalFailure(), req.healSkipReason(), req.healTier(),
                false, false, false, req.evidenceDir(), "", "", List.of(), req.deduplicationKey());
        var outcome = gate.tryEscalate(notExhausted);
        Assert.assertEquals(outcome.status(), ExecutionInvestigationOutcome.SKIPPED);
        Assert.assertEquals(runtime.starts.get(), 0);
    }

    @Test
    public void duplicateBoundary_investigatedOnce() {
        CountingFakeRuntime runtime = new CountingFakeRuntime();
        var gate = gateFor(runtime, flags(true, true));
        var req = exhaustedRequest("job5", "TC_05");
        Assert.assertEquals(gate.tryEscalate(req).status(), ExecutionInvestigationOutcome.INVESTIGATED);
        Assert.assertEquals(gate.tryEscalate(req).status(), ExecutionInvestigationOutcome.DUPLICATE);
        Assert.assertEquals(runtime.starts.get(), 1);
    }

    @Test
    public void harnessInterrupted_stillNotPass() {
        InterruptingFakeRuntime runtime = new InterruptingFakeRuntime();
        var gate = gateFor(runtime, flags(true, true));
        var outcome = gate.tryEscalate(exhaustedRequest("job6", "TC_06"));
        Assert.assertEquals(outcome.status(), ExecutionInvestigationOutcome.INVESTIGATED);
        Assert.assertEquals(outcome.harnessTerminalStatus(), AgentTaskStatus.INTERRUPTED);
    }

    @Test
    public void harnessExecuteFails_originalFailureUnchanged() {
        FailingFakeRuntime runtime = new FailingFakeRuntime();
        var gate = gateFor(runtime, flags(true, true));
        var outcome = gate.tryEscalate(exhaustedRequest("job7", "TC_07"));
        Assert.assertEquals(outcome.status(), ExecutionInvestigationOutcome.HARNESS_FAILED);
    }

    @Test
    public void evidenceBuilder_schemaVersion() {
        var req = exhaustedRequest("job8", "TC_08");
        var evidence = ExecutionInvestigationEvidenceBuilder.build(req);
        Assert.assertEquals(evidence.schemaVersion(), ExecutionInvestigationEvidenceV1.SCHEMA_VERSION);
        Assert.assertEquals(evidence.contractId(), ExecutionInvestigationEvidenceV1.CONTRACT_ID);
        Assert.assertTrue(evidence.toMap().containsKey("missingFields"));
    }

    private ExecutionRecoveryEscalationGate gateFor(AgentRuntime runtime, AgentHarnessProperties props) {
        var custom = new AgentHarnessCoordinator(
                null,
                null,
                null,
                props,
                runtimeMap(runtime));
        var bridge = new ExecutionRecoveryHarnessBridge(props, custom);
        return new ExecutionRecoveryEscalationGate(props, bridge);
    }

    private static java.util.Map<delivery.agent.harness.AgentProviderId, AgentRuntime> runtimeMap(
            AgentRuntime runtime) {
        java.util.Map<delivery.agent.harness.AgentProviderId, AgentRuntime> map =
                new java.util.EnumMap<>(delivery.agent.harness.AgentProviderId.class);
        map.put(delivery.agent.harness.AgentProviderId.FAKE, runtime);
        return map;
    }

    private static AgentHarnessProperties flags(boolean enabled, boolean escalation) {
        var p = new AgentHarnessProperties();
        p.setEnabled(enabled);
        p.setExecutionRecoveryCursorEscalationEnabled(escalation);
        p.setCursorEnabled(false);
        return p;
    }

    private ExecutionRecoveryEscalationRequest exhaustedRequest(String jobId, String tcId) {
        ManualTestCase tc = new ManualTestCase(
                tcId, "title", "", "1. Click 'Login Button'", "see 'Dashboard Page'", "", "", "", "", "", "");
        ConversionJobRequest job = new ConversionJobRequest(
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
                delivery.identity.TenantId.mint(),
                jobId);
        return new ExecutionRecoveryEscalationRequest(
                job,
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
            return new AgentRunResult(
                    AgentTaskStatus.INTERRUPTED,
                    "interrupted",
                    Map.of(),
                    List.of(),
                    "");
        }
    }

    static final class FailingFakeRuntime implements AgentRuntime {
        @Override
        public delivery.agent.harness.AgentProviderId providerId() {
            return delivery.agent.harness.AgentProviderId.FAKE;
        }

        @Override
        public AgentRunHandle start(AgentTask task) {
            throw new AgentHarnessException("runtime unavailable");
        }

        @Override
        public AgentRunHandle resume(String agentId, String runId) {
            throw new AgentHarnessException("resume");
        }

        @Override
        public void cancel(String agentId, String runId) {
        }

        @Override
        public AgentRunResult getResult(String agentId, String runId) {
            throw new AgentHarnessException("no result");
        }
    }
}