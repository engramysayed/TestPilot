package delivery.agent.validation.automate;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRunHandle;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentRuntime;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.FakeAgentRuntime;
import delivery.agent.validation.contract.FrameworkContractValidator;
import delivery.agent.validation.contract.FrameworkExpectedInventory;
import delivery.codegen.CodeWriter;
import delivery.codegen.CodegenEmitGate;
import delivery.codegen.ProvenStep;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import delivery.agent.harness.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;

/**
 * Cursor-shaped repair lifecycle using {@link FakeAgentRuntime} injected as CURSOR provider:
 * edits land in {@code taskDir/workspace/}, reconcile promotes into repair-candidate, then acceptance.
 */
public class FrameworkAdaptationCursorReconcileIT {

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
    public void cursorWorkspaceLifecycle_missingPage_promotedAfterReconcile() throws Exception {
        Path work = Files.createTempDirectory("cursor-reconcile-");
        Path project = work.resolve("project");
        ProvenStep step = new ProvenStep("TC_1", "Login", "elementAction", "click",
                "css", "button", "", "", "", true, "ok");
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(new TcOutcome(
                "TC_1", "login", TcStatus.PASSED, List.of(step), "", null, false, List.of())));
        new CodeWriter(Path.of("customer-framework-template/templates")).write(project, prepared.outcomes());
        Files.deleteIfExists(project.resolve("src/main/java/project/pages/Login_Page.java"));
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();

        AgentHarnessProperties props = flagsCursorRepair();
        AgentHarnessCoordinator coordinator = new AgentHarnessCoordinator(
                null, null, null, props, cursorRuntimeMap(new IsolatedPageRestoringRuntime()));
        AutomateFrameworkRepairBridge bridge =
                new AutomateFrameworkRepairBridge(props, coordinator, () -> false, Duration.ofMinutes(5));
        var gate = AutomateFrameworkAcceptanceGate.forTests(bridge);
        String originalPage = Files.exists(project.resolve("src/main/java/project/pages/Login_Page.java"))
                ? Files.readString(project.resolve("src/main/java/project/pages/Login_Page.java"))
                : "";
        var outcome = gate.run(work, project, template, work, "job-cursor", "proj", inventory);

        Assert.assertTrue(outcome.accepted());
        Assert.assertTrue(Files.exists(project.resolve("src/main/java/project/pages/Login_Page.java")));
        Assert.assertTrue(
                Files.readString(project.resolve("src/main/java/project/pages/Login_Page.java")).contains("Login_Page"));
        Path repairRoot = work.resolve("framework-acceptance/repair-candidate");
        Assert.assertTrue(Files.exists(repairRoot.resolve("src/main/java/project/pages/Login_Page.java")));
        Assert.assertEquals(originalPage, "");
    }

    @Test
    public void fakeProvider_unchanged_writesDirectlyToRepairCandidate() throws Exception {
        Path work = Files.createTempDirectory("fake-unchanged-");
        Path project = work.resolve("project");
        Path pages = project.resolve("src/main/java/project/pages");
        Path tests = project.resolve("src/test/java/project/tests/generated");
        Files.createDirectories(pages);
        Files.createDirectories(tests);
        Files.writeString(pages.resolve("Login_Page.java"), "package project.pages;\npublic class Login_Page {}");
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();
        AgentHarnessProperties props = flagsFakeRepair();
        AgentHarnessCoordinator coordinator = new AgentHarnessCoordinator(
                null, null, null, props, fakeRuntimeMap(new AutomateFrameworkAcceptanceGateTest.FixingFakeRuntime(project)));
        AutomateFrameworkRepairBridge bridge =
                new AutomateFrameworkRepairBridge(props, coordinator, () -> false, Duration.ofMinutes(5));
        var gate = AutomateFrameworkAcceptanceGate.forTests(bridge);
        var outcome = gate.run(work, project, template, work, "job-fake", "proj");
        Assert.assertTrue(outcome.accepted());
    }

    private static AgentHarnessProperties flagsCursorRepair() {
        var p = new AgentHarnessProperties();
        p.setEnabled(true);
        p.setAutomateFrameworkRepairEnabled(true);
        p.setCursorEnabled(true);
        return p;
    }

    private static AgentHarnessProperties flagsFakeRepair() {
        var p = new AgentHarnessProperties();
        p.setEnabled(true);
        p.setAutomateFrameworkRepairEnabled(true);
        p.setCursorEnabled(false);
        return p;
    }

    private static EnumMap<AgentProviderId, AgentRuntime> fakeRuntimeMap(AgentRuntime runtime) {
        EnumMap<AgentProviderId, AgentRuntime> map = new EnumMap<>(AgentProviderId.class);
        map.put(AgentProviderId.FAKE, runtime);
        return map;
    }

    private static EnumMap<AgentProviderId, AgentRuntime> cursorRuntimeMap(AgentRuntime runtime) {
        EnumMap<AgentProviderId, AgentRuntime> map = new EnumMap<>(AgentProviderId.class);
        map.put(AgentProviderId.FAKE, new FakeAgentRuntime());
        map.put(AgentProviderId.CURSOR, runtime);
        return map;
    }

    /** Writes the missing page into the task-isolated workspace (Cursor path). */
    static final class IsolatedPageRestoringRuntime implements AgentRuntime {
        private final FakeAgentRuntime delegate = new FakeAgentRuntime();

        @Override
        public AgentProviderId providerId() {
            return AgentProviderId.CURSOR;
        }

        @Override
        public AgentRunHandle start(AgentTask task) {
            try {
                Path ws = Path.of(task.workspacePath());
                Path pages = ws.resolve("src/main/java/project/pages");
                Files.createDirectories(pages);
                Files.writeString(pages.resolve("Login_Page.java"), """
                        package project.pages;
                        import org.openqa.selenium.By;
                        public class Login_Page {
                            protected final By login_Btn_Locator = By.cssSelector("button");
                            public void click_login_Btn() {}
                        }
                        """);
            } catch (Exception ignored) {
            }
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
}