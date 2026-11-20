package delivery.agent.validation.automate;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentRunHandle;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentRuntime;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.FakeAgentRuntime;
import delivery.agent.harness.AgentTaskStatus;
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

public class AutomateFrameworkAcceptanceGateTest {

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
    public void validFramework_acceptedWithoutRepair() throws Exception {
        Path work = Files.createTempDirectory("af-accept-");
        Path project = scaffoldProject(work, true);
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();
        var gate = AutomateFrameworkAcceptanceGate.forConversionJob(flags(false), () -> false, Duration.ofMinutes(5));
        var outcome = gate.run(work, project, template, work, "job-1", "proj");
        Assert.assertTrue(outcome.accepted());
        Assert.assertEquals(outcome.status(), AutomateFrameworkAcceptanceStatus.ACCEPTED);
    }

    @Test
    public void mandatoryViolation_repairDisabled_rejected() throws Exception {
        Path work = Files.createTempDirectory("af-reject-");
        Path project = scaffoldProject(work, false);
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();
        var gate = AutomateFrameworkAcceptanceGate.forConversionJob(flags(false), () -> false, Duration.ofMinutes(5));
        var outcome = gate.run(work, project, template, work, "job-2", "proj");
        Assert.assertFalse(outcome.accepted());
        Assert.assertEquals(outcome.status(), AutomateFrameworkAcceptanceStatus.REPAIR_NOT_ENABLED);
        Assert.assertFalse(Files.exists(work.resolve("framework-acceptance").resolve("repair-candidate")));
    }

    @Test
    public void repairSuccess_promotesCandidate() throws Exception {
        Path work = Files.createTempDirectory("af-repair-");
        Path project = scaffoldProject(work, false);
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();
        AgentHarnessProperties props = flags(true);
        AgentHarnessCoordinator coordinator = new AgentHarnessCoordinator(
                null, null, null, props, runtimeMap(new FixingFakeRuntime(project)));
        AutomateFrameworkRepairBridge bridge =
                new AutomateFrameworkRepairBridge(props, coordinator, () -> false, Duration.ofMinutes(5));
        var gate = AutomateFrameworkAcceptanceGate.forTests(bridge);
        var outcome = gate.run(work, project, template, work, "job-3", "proj");
        Assert.assertTrue(outcome.accepted());
        Assert.assertTrue(Files.exists(project.resolve("src/test/java/project/tests/generated/Login_Test.java")));
    }

    @Test
    public void inventoryV2_emitPath_acceptanceAndRepairUseSameInventory() throws Exception {
        Path work = Files.createTempDirectory("af-inv-v2");
        Path project = work.resolve("project");
        ProvenStep step = new ProvenStep("TC_1", "Login", "elementAction", "click",
                "css", "button", "", "", "", true, "ok");
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(new TcOutcome(
                "TC_1", "login", TcStatus.PASSED, List.of(step), "", null, false, List.of())));
        new CodeWriter(Path.of("customer-framework-template/templates")).write(project, prepared.outcomes());
        Files.deleteIfExists(project.resolve("src/main/java/project/pages/Login_Page.java"));
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();
        AgentHarnessProperties props = flags(true);
        AgentHarnessCoordinator coordinator = new AgentHarnessCoordinator(
                null, null, null, props, runtimeMap(new PageRestoringFakeRuntime(project)));
        AutomateFrameworkRepairBridge bridge =
                new AutomateFrameworkRepairBridge(props, coordinator, () -> false, Duration.ofMinutes(5));
        var gate = AutomateFrameworkAcceptanceGate.forTests(bridge);
        var outcome = gate.run(work, project, template, work, "job-inv", "proj", inventory);
        Assert.assertTrue(outcome.accepted());
        Assert.assertEquals(
                outcome.validationReport().contractVersion(),
                FrameworkContractValidator.CONTRACT_VERSION_INVENTORY);
        Assert.assertEquals(
                outcome.revalidationReport().contractVersion(),
                FrameworkContractValidator.CONTRACT_VERSION_INVENTORY);
        Assert.assertTrue(Files.exists(project.resolve("src/main/java/project/pages/Login_Page.java")));
        var v1Only = FrameworkContractValidator.validateWorkspace(project, template);
        Assert.assertEquals(v1Only.contractVersion(), "1", "v1 path must stay separate from v2 acceptance");
    }

    @Test
    public void repairFails_originalSnapshotPreserved() throws Exception {
        Path work = Files.createTempDirectory("af-fail-");
        Path project = scaffoldProject(work, false);
        String before = Files.readString(project.resolve("src/main/java/project/pages/Login_Page.java"));
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();
        var gate = AutomateFrameworkAcceptanceGate.forConversionJob(flags(true), () -> false, Duration.ofMinutes(5));
        var outcome = gate.run(work, project, template, work, "job-4", "proj");
        Assert.assertFalse(outcome.accepted());
        Assert.assertEquals(outcome.status(), AutomateFrameworkAcceptanceStatus.REVALIDATION_FAILED);
        Assert.assertEquals(Files.readString(project.resolve("src/main/java/project/pages/Login_Page.java")), before);
    }

    static Path scaffoldProjectPublic(Path work, boolean withTest) throws Exception {
        return scaffoldProject(work, withTest);
    }

  private static Path scaffoldProject(Path work, boolean withTest) throws Exception {
        Path project = work.resolve("project");
        Path pages = project.resolve("src/main/java/project/pages");
        Path tests = project.resolve("src/test/java/project/tests/generated");
        Files.createDirectories(pages);
        Files.createDirectories(tests);
        Files.writeString(pages.resolve("Login_Page.java"), "package project.pages;\npublic class Login_Page {}");
        if (withTest) {
            Files.writeString(tests.resolve("Login_Test.java"), """
                    package project.tests.generated;
                    import org.testng.annotations.Test;
                    import project.tests.BaseTest;
                    import project.pages.Login_Page;
                    public class Login_Test extends BaseTest {
                        private Login_Page login_Page;
                        @Test(description = "login flow")
                        public void should_login_flow() {}
                    }
                    """);
        }
        Files.writeString(project.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");
        return project;
    }

    private static AgentHarnessProperties flags(boolean repair) {
        var p = new AgentHarnessProperties();
        p.setEnabled(repair);
        p.setAutomateFrameworkRepairEnabled(repair);
        p.setCursorEnabled(false);
        return p;
    }

    private static EnumMap<delivery.agent.harness.AgentProviderId, AgentRuntime> runtimeMap(AgentRuntime rt) {
        EnumMap<delivery.agent.harness.AgentProviderId, AgentRuntime> map =
                new EnumMap<>(delivery.agent.harness.AgentProviderId.class);
        map.put(delivery.agent.harness.AgentProviderId.FAKE, rt);
        return map;
    }

    static final class PageRestoringFakeRuntime implements AgentRuntime {
        private final FakeAgentRuntime delegate = new FakeAgentRuntime();
        private final Path projectRoot;

        PageRestoringFakeRuntime(Path projectRoot) {
            this.projectRoot = projectRoot;
        }

        @Override
        public delivery.agent.harness.AgentProviderId providerId() {
            return delegate.providerId();
        }

        @Override
        public AgentRunHandle start(AgentTask task) {
            try {
                Path ws = task.workspacePath() == null || task.workspacePath().isBlank()
                        ? projectRoot
                        : Path.of(task.workspacePath());
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

    static final class FixingFakeRuntime implements AgentRuntime {
        private final FakeAgentRuntime delegate = new FakeAgentRuntime();
        private final Path projectRoot;

        FixingFakeRuntime(Path projectRoot) {
            this.projectRoot = projectRoot;
        }

        @Override
        public delivery.agent.harness.AgentProviderId providerId() {
            return delegate.providerId();
        }

        @Override
        public AgentRunHandle start(AgentTask task) {
            try {
                Path ws = task.workspacePath() == null || task.workspacePath().isBlank()
                        ? projectRoot
                        : Path.of(task.workspacePath());
                Path tests = ws.resolve("src/test/java/project/tests/generated");
                Files.createDirectories(tests);
                Files.writeString(tests.resolve("Login_Test.java"), """
                        package project.tests.generated;
                        import org.testng.annotations.Test;
                        import project.tests.BaseTest;
                        import project.pages.Login_Page;
                        public class Login_Test extends BaseTest {
                            @Test(description = "login flow")
                            public void should_login_flow() {}
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