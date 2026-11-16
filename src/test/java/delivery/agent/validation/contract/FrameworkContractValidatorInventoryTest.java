package delivery.agent.validation.contract;

import delivery.codegen.CodeWriter;
import delivery.codegen.ProvenStep;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FrameworkContractValidatorInventoryTest {

    @Test
    public void emittedFramework_passesInventoryValidation() throws Exception {
        Path root = Files.createTempDirectory("fw-inv-pass-");
        List<TcOutcome> outcomes = List.of(passedLoginOutcome());
        new CodeWriter(Path.of("customer-framework-template/templates")).write(root, outcomes);
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(outcomes);
        var report = FrameworkContractValidator.validateWorkspace(root, null, inventory);
        Assert.assertEquals(report.contractVersion(), FrameworkContractValidator.CONTRACT_VERSION_INVENTORY);
        Assert.assertTrue(report.passed(), report.findings().toString());
    }

    @Test
    public void missingExpectedPage_failsMandatory() throws Exception {
        Path root = Files.createTempDirectory("fw-inv-miss-page-");
        List<TcOutcome> outcomes = List.of(passedLoginOutcome());
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(outcomes);
        Path pages = root.resolve("src/main/java/project/pages");
        Path tests = root.resolve("src/test/java/project/tests/generated");
        Files.createDirectories(tests);
        Files.writeString(tests.resolve("Login_Test.java"), minimalTest("Login_Page", "loginPage"));
        var report = FrameworkContractValidator.validateWorkspace(root, null, inventory);
        Assert.assertFalse(report.passed());
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "FW_EXPECTED_PAGE_PRESENT".equals(f.ruleId())));
    }

    @Test
    public void orphanPage_failsMandatory() throws Exception {
        Path root = Files.createTempDirectory("fw-inv-orphan-");
        List<TcOutcome> outcomes = List.of(passedLoginOutcome());
        new CodeWriter(Path.of("customer-framework-template/templates")).write(root, outcomes);
        Files.writeString(
                root.resolve("src/main/java/project/pages/Stray_Page.java"),
                "package project.pages;\npublic class Stray_Page {}");
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(outcomes);
        var report = FrameworkContractValidator.validateWorkspace(root, null, inventory);
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "FW_ORPHAN_PAGE_CLASS".equals(f.ruleId())));
    }

    @Test
    public void testImportsPageButDoesNotUse_fails() throws Exception {
        Path root = Files.createTempDirectory("fw-inv-unused-");
        List<TcOutcome> outcomes = List.of(passedLoginOutcome());
        new CodeWriter(Path.of("customer-framework-template/templates")).write(root, outcomes);
        Path testFile = root.resolve("src/test/java/project/tests/generated/Login_Test.java");
        String broken = Files.readString(testFile)
                .replaceAll("(?s)\\s*loginPage\\s*=\\s*new Login_Page\\(driver\\);", "")
                .replaceAll("loginPage\\.[\\w_]+\\([^)]*\\);", "");
        Files.writeString(testFile, broken);
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(outcomes);
        var report = FrameworkContractValidator.validateWorkspace(root, null, inventory);
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "FW_TEST_PAGE_UNUSED".equals(f.ruleId())));
    }

    @Test
    public void emptyTestStub_fails() throws Exception {
        Path root = Files.createTempDirectory("fw-inv-stub-");
        List<TcOutcome> outcomes = List.of(passedLoginOutcome());
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(outcomes);
        Path pages = root.resolve("src/main/java/project/pages");
        Path tests = root.resolve("src/test/java/project/tests/generated");
        Files.createDirectories(pages);
        Files.createDirectories(tests);
        Files.writeString(pages.resolve("Login_Page.java"), pageWithLocator());
        Files.writeString(tests.resolve("Login_Test.java"), """
                package project.tests.generated;
                import org.testng.annotations.Test;
                import project.tests.BaseTest;
                import project.pages.Login_Page;
                public class Login_Test extends BaseTest {
                    private Login_Page loginPage;
                    @Test public void login() { }
                }
                """);
        var report = FrameworkContractValidator.validateWorkspace(root, null, inventory);
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "FW_TEST_STUB_EMPTY".equals(f.ruleId())));
    }

    @Test
    public void reportMetadata_includesInventory() {
        FrameworkExpectedInventory inventory =
                FrameworkExpectedInventory.fromOutcomes(List.of(passedLoginOutcome()));
        Assert.assertEquals(inventory.toReportMetadata().get("validationMode"), "inventory");
    }

    private static TcOutcome passedLoginOutcome() {
        ProvenStep step = new ProvenStep("TC_001", "Login", "elementAction", "click",
                "css", "button[data-test='login-button']", "", "", "", true, "ok");
        return new TcOutcome("TC_001", "Valid login", TcStatus.PASSED, List.of(step), "", null, false, List.of());
    }

    private static String minimalTest(String pageClass, String varName) {
        return """
                package project.tests.generated;
                import org.testng.annotations.Test;
                import project.tests.BaseTest;
                import project.pages.%s;
                public class Login_Test extends BaseTest {
                    private %s %s;
                    @Test public void login() {
                        %s = new %s(driver);
                        %s.click_Login_Btn();
                    }
                }
                """.formatted(pageClass, pageClass, varName, varName, pageClass, varName);
    }

    private static String pageWithLocator() {
        return """
                package project.pages;
                import org.openqa.selenium.By;
                public class Login_Page {
                    protected final By login_Btn_Locator = By.cssSelector("button");
                    public void click_Login_Btn() {}
                }
                """;
    }
}