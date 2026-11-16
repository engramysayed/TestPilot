package delivery.agent.validation.contract;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class FrameworkContractValidatorTest {

    @Test
    public void pairedPageAndTest_pass() throws Exception {
        Path root = Files.createTempDirectory("fw-contract-");
        Path pages = root.resolve("src/test/java/project/pages");
        Path tests = root.resolve("src/test/java/project/tests");
        Files.createDirectories(pages);
        Files.createDirectories(tests);
        Files.writeString(pages.resolve("Login_Page.java"), "public class Login_Page {}");
        Files.writeString(tests.resolve("Login_Test.java"), "public class Login_Test {}");
        var report = FrameworkContractValidator.validateWorkspace(root);
        Assert.assertTrue(report.passed(), report.findings().toString());
    }

    @Test
    public void missingBaseTest_failsMandatory() throws Exception {
        Path root = Files.createTempDirectory("fw-base-");
        Path tests = root.resolve("src/test/java/project/tests/generated");
        Files.createDirectories(tests);
        Files.writeString(tests.resolve("Login_Test.java"), """
                package project.tests.generated;
                public class Login_Test {
                }
                """);
        var report = FrameworkContractValidator.validateWorkspace(root, null);
        Assert.assertFalse(report.passed());
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "FW_TEST_BASE_CLASS".equals(f.ruleId())));
    }

    @Test
    public void threadSleep_reportsRecommendedHelperRule() throws Exception {
        Path root = Files.createTempDirectory("fw-sleep-");
        Path pages = root.resolve("src/main/java/project/pages");
        Path tests = root.resolve("src/test/java/project/tests/generated");
        Files.createDirectories(pages);
        Files.createDirectories(tests);
        Files.writeString(pages.resolve("Login_Page.java"), "public class Login_Page { void x(){ Thread.sleep(1);} }");
        Files.writeString(tests.resolve("Login_Test.java"), """
                package project.tests.generated;
                import project.tests.BaseTest;
                import project.pages.Login_Page;
                public class Login_Test extends BaseTest { @org.testng.annotations.Test void t(){} }
                """);
        Path template = Path.of("customer-framework-template").toAbsolutePath().normalize();
        var report = FrameworkContractValidator.validateWorkspace(root, template);
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "FW_HELPER_THREADSLEEP".equals(f.ruleId())));
        Assert.assertTrue(report.passed(), "recommended findings do not block");
    }

    @Test
    public void missingTest_failsPairRule() throws Exception {
        Path root = Files.createTempDirectory("fw-contract-miss-");
        Path pages = root.resolve("project/pages");
        Files.createDirectories(pages);
        Files.writeString(pages.resolve("Cart_Page.java"), "public class Cart_Page {}");
        var report = FrameworkContractValidator.validateWorkspace(root);
        Assert.assertFalse(report.passed());
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "FW_ARCH_PAGE_TEST_PAIR".equals(f.ruleId())));
    }
}