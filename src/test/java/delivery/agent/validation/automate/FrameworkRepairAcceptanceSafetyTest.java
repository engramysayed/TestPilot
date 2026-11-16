package delivery.agent.validation.automate;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentRuntime;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.FakeAgentRuntime;
import delivery.agent.validation.contract.FrameworkContractValidator;
import delivery.codegen.ProvenStep;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import delivery.portal.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;

/** Phase 6.5 acceptance safety — promotion boundaries without live Cursor. */
public class FrameworkRepairAcceptanceSafetyTest {

    private String priorCompileCheck;

    @BeforeMethod
    public void setUp() {
        priorCompileCheck = System.getProperty("delivery.emit-compile-check");
        System.setProperty("delivery.emit-compile-check", "false");
    }

    @AfterMethod
    public void tearDown() {
        if (priorCompileCheck == null) {
            System.clearProperty("delivery.emit-compile-check");
        } else {
            System.setProperty("delivery.emit-compile-check", priorCompileCheck);
        }
    }

    @Test
    public void reconciliationFailure_neverPromotes() throws Exception {
        Path work = Files.createTempDirectory("safe-recon-");
        Path project = AutomateFrameworkAcceptanceGateTest.scaffoldProjectPublic(work, false);
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();
        AgentHarnessProperties props = flags(true);
        AgentHarnessCoordinator coordinator = new AgentHarnessCoordinator(
                null, null, null, props, cursorRuntimeMap(new CompletingCursorNoFileRuntime()));
        var gate = AutomateFrameworkAcceptanceGate.forTests(
                new AutomateFrameworkRepairBridge(props, coordinator, () -> false, Duration.ofMinutes(5)));
        String before = digestProject(project);
        var outcome = gate.run(work, project, template, work, "job-recon-fail", "proj");
        Assert.assertFalse(outcome.accepted());
        Assert.assertEquals(outcome.status(), AutomateFrameworkAcceptanceStatus.REVALIDATION_FAILED);
        Assert.assertEquals(digestProject(project), before);
        Assert.assertTrue(Files.isDirectory(work.resolve("framework-acceptance/original")));
    }

    @Test
    public void fakeProvider_neverWritesReconciliationManifest() throws Exception {
        Path work = Files.createTempDirectory("safe-fake-");
        Path project = AutomateFrameworkAcceptanceGateTest.scaffoldProjectPublic(work, false);
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();
        AgentHarnessProperties props = flags(false);
        AgentHarnessCoordinator coordinator = new AgentHarnessCoordinator(
                null, null, null, props,
                fakeRuntimeMap(new AutomateFrameworkAcceptanceGateTest.FixingFakeRuntime(project)));
        var gate = AutomateFrameworkAcceptanceGate.forTests(
                new AutomateFrameworkRepairBridge(props, coordinator, () -> false, Duration.ofMinutes(5)));
        var outcome = gate.run(work, project, template, work, "job-fake", "proj");
        Assert.assertTrue(outcome.accepted());
        Assert.assertFalse(Files.exists(work.resolve("framework-acceptance/reconciliation-manifest.json")));
    }

    @Test
    public void cursorReconcile_wrongTaskWorkspace_reconciliationFailed() throws Exception {
        Path work = Files.createTempDirectory("safe-id-");
        var layout = FrameworkRepairWorkspaceReconcilerTest.scaffoldLayoutPublic();
        Path wrong = Files.createTempDirectory("other-task");
        try {
            Assert.expectThrows(
                    FrameworkRepairReconciliationException.class,
                    () -> new FrameworkRepairWorkspaceReconciler().reconcile(
                            layout.workDir(), layout.projectRoot(), layout.parentJobId(), layout.taskId(),
                            layout.repairRoot(), wrong));
        } finally {
            deleteRec(wrong);
        }
    }

    private static String digestProject(Path project) throws Exception {
        Path page = project.resolve("src/main/java/project/pages/Login_Page.java");
        return Files.exists(page) ? Files.readString(page) : "";
    }

    private static AgentHarnessProperties flags(boolean cursor) {
        var p = new AgentHarnessProperties();
        p.setEnabled(true);
        p.setAutomateFrameworkRepairEnabled(true);
        p.setCursorEnabled(cursor);
        return p;
    }

    private static EnumMap<AgentProviderId, AgentRuntime> cursorRuntimeMap(AgentRuntime rt) {
        EnumMap<AgentProviderId, AgentRuntime> map = new EnumMap<>(AgentProviderId.class);
        map.put(AgentProviderId.FAKE, new FakeAgentRuntime());
        map.put(AgentProviderId.CURSOR, rt);
        return map;
    }

    private static EnumMap<AgentProviderId, AgentRuntime> fakeRuntimeMap(AgentRuntime rt) {
        EnumMap<AgentProviderId, AgentRuntime> map = new EnumMap<>(AgentProviderId.class);
        map.put(AgentProviderId.FAKE, rt);
        return map;
    }

    private static void deleteRec(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    /** Cursor completes without fixing mandatory violation — reconcile may no-op; revalidation fails; no promotion. */
    static final class CompletingCursorNoFileRuntime implements AgentRuntime {
        private final FakeAgentRuntime delegate = new FakeAgentRuntime();

        @Override
        public AgentProviderId providerId() {
            return AgentProviderId.CURSOR;
        }

        @Override
        public delivery.agent.harness.AgentRunHandle start(AgentTask task) {
            return delegate.start(task);
        }

        @Override
        public delivery.agent.harness.AgentRunHandle resume(String agentId, String runId) {
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
}