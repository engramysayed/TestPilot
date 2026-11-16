package delivery.agent.validation.automate;

import delivery.agent.validation.contract.FrameworkExpectedInventory;
import delivery.codegen.CodegenEmitGate;
import delivery.codegen.CodeWriter;
import delivery.codegen.ProvenStep;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FrameworkRepairArchitectureAssessorTest {

    @Test
    public void emptyStub_notFunctionallyComplete_butContractMayPass() throws Exception {
        Path root = Files.createTempDirectory("arch-assess-");
        var prepared = CodegenEmitGate.prepare(List.of(passedLogin("TC_1")));
        new CodeWriter(Path.of("customer-framework-template/templates")).write(root, prepared.outcomes());
        Path test = root.resolve("src/test/java/project/tests/generated/Login_Test.java");
        Files.writeString(test, """
                package project.tests.generated;
                import org.testng.annotations.Test;
                import project.tests.BaseTest;
                import project.pages.Login_Page;
                public class Login_Test extends BaseTest {
                    private Login_Page loginPage;
                    @Test public void login() { }
                }
                """);
        var inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var contract = delivery.agent.validation.contract.FrameworkContractValidator.validateWorkspace(
                root, Path.of("customer-framework-template").toAbsolutePath().normalize(), inventory);
        var arch = FrameworkRepairArchitectureAssessor.assess(root, inventory);
        Assert.assertFalse(arch.functionallyComplete(), "architecture must flag empty stub");
        Assert.assertTrue(
                arch.findings().stream().anyMatch(f -> "TEST_STUB_EMPTY".equals(f.code())),
                "shallow stub must not be reported as fully repaired");
        Assert.assertFalse(arch.functionallyComplete());
        Assert.assertFalse(arch.functionallyComplete() && contract.passed(),
                "shallow stub must not qualify as fully repaired even if contract passed");
    }

    @Test
    public void codegenShapedProject_functionallyComplete() throws Exception {
        Path root = Files.createTempDirectory("arch-good-");
        var prepared = CodegenEmitGate.prepare(List.of(passedLogin("TC_1")));
        new CodeWriter(Path.of("customer-framework-template/templates")).write(root, prepared.outcomes());
        var inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var arch = FrameworkRepairArchitectureAssessor.assess(root, inventory);
        Assert.assertTrue(arch.functionallyComplete());
    }

    private static TcOutcome passedLogin(String tcId) {
        ProvenStep step = new ProvenStep(tcId, "Login", "elementAction", "click",
                "css", "button[data-test='login-button']", "", "", "", true, "ok");
        return new TcOutcome(tcId, "login", TcStatus.PASSED, List.of(step), "", null, false, List.of());
    }
}