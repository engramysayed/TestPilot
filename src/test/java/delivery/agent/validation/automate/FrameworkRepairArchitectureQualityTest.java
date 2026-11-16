package delivery.agent.validation.automate;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRuntime;
import delivery.agent.harness.FakeAgentRuntime;
import delivery.agent.validation.contract.FrameworkExpectedInventory;
import delivery.codegen.CodeWriter;
import delivery.codegen.CodegenEmitGate;
import delivery.codegen.CodegenNaming;
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
import java.util.regex.Pattern;

/** Architecture checks for artifacts produced by deterministic Cursor-shaped repair. */
public class FrameworkRepairArchitectureQualityTest {

    private static final Pattern PAGE_CLASS = Pattern.compile("public class (\\w+_Page)");
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
    public void cursorShapedRepair_pageNamingAndStructure() throws Exception {
        Path work = Files.createTempDirectory("arch-quality-");
        Path project = work.resolve("project");
        ProvenStep step = new ProvenStep("TC_1", "Login", "elementAction", "click",
                "css", "button", "", "", "", true, "ok");
        var prepared = CodegenEmitGate.prepare(List.of(new TcOutcome(
                "TC_1", "login", TcStatus.PASSED, List.of(step), "", null, false, List.of())));
        new CodeWriter(Path.of("customer-framework-template/templates")).write(project, prepared.outcomes());
        Files.deleteIfExists(project.resolve("src/main/java/project/pages/Login_Page.java"));
        AgentHarnessProperties props = flags();
        AgentHarnessCoordinator coordinator = new AgentHarnessCoordinator(
                null, null, null, props,
                cursorMap(new FrameworkAdaptationCursorReconcileIT.IsolatedPageRestoringRuntime()));
        var gate = AutomateFrameworkAcceptanceGate.forTests(
                new AutomateFrameworkRepairBridge(props, coordinator, () -> false, Duration.ofMinutes(5)));
        var outcome = gate.run(
                work, project, Path.of("customer-framework-template").toAbsolutePath().normalize(),
                work, "arch", "proj", FrameworkExpectedInventory.fromOutcomes(prepared.outcomes()));
        Assert.assertTrue(outcome.accepted());
        Path page = project.resolve("src/main/java/project/pages/Login_Page.java");
        String body = Files.readString(page);
        Assert.assertTrue(body.contains("package project.pages;"));
        Assert.assertTrue(body.contains("import org.openqa.selenium.By;"));
        var m = PAGE_CLASS.matcher(body);
        Assert.assertTrue(m.find());
        Assert.assertEquals(m.group(1), CodegenNaming.pageClassName("Login"));
        Assert.assertTrue(body.contains("protected final By"), "page must declare locators, not a skeleton class");
        Assert.assertTrue(body.contains("click_") || body.contains("void click"), "page must expose actions");
    }

    private static AgentHarnessProperties flags() {
        var p = new AgentHarnessProperties();
        p.setEnabled(true);
        p.setAutomateFrameworkRepairEnabled(true);
        p.setCursorEnabled(true);
        return p;
    }

    private static EnumMap<AgentProviderId, AgentRuntime> cursorMap(AgentRuntime rt) {
        EnumMap<AgentProviderId, AgentRuntime> map = new EnumMap<>(AgentProviderId.class);
        map.put(AgentProviderId.FAKE, new FakeAgentRuntime());
        map.put(AgentProviderId.CURSOR, rt);
        return map;
    }
}