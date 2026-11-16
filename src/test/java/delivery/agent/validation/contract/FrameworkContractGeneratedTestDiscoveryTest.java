package delivery.agent.validation.contract;

import delivery.codegen.CodeWriter;
import delivery.codegen.CodegenEmitGate;
import delivery.codegen.CodegenTestClasses;
import delivery.codegen.ProvenStep;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import delivery.packager.FrameworkPackager;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Phase 6.9 — inventory v2 test discovery scope (template BaseTest vs generated tier).
 */
public class FrameworkContractGeneratedTestDiscoveryTest {

    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");
    private static final Path TEMPLATE_ROOT = Path.of("customer-framework-template").toAbsolutePath().normalize();

    @Test
    public void fullTemplatePackaged_emit_passesWithoutBaseTestOrphan() throws Exception {
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(passedLogin("TC_1")));
        Path root = Files.createTempDirectory("p69-packaged-");
        new FrameworkPackager().copyTemplate(TEMPLATE_ROOT, root);
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        Assert.assertTrue(Files.isRegularFile(root.resolve("src/test/java/project/tests/BaseTest.java")));
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inventory);
        Assert.assertFalse(
                report.findings().stream().anyMatch(f -> "FW_ORPHAN_TEST_CLASS".equals(f.ruleId())),
                report.findings().toString());
        Assert.assertTrue(report.passed(), report.findings().toString());
    }

    @Test
    public void strayGeneratedTest_stillFailsOrphanMandatory() throws Exception {
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(passedLogin("TC_1")));
        Path root = Files.createTempDirectory("p69-stray-gen-");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        Path gen = root.resolve("src/test/java/project/tests/generated");
        Files.writeString(gen.resolve("Stray_Test.java"), """
                package project.tests.generated;
                import org.testng.annotations.Test;
                import project.tests.BaseTest;
                import project.pages.Login_Page;
                public class Stray_Test extends BaseTest {
                    private Login_Page loginPage;
                    @Test public void t() {
                        loginPage = new Login_Page(driver);
                        loginPage.click_Login_Btn();
                    }
                }
                """);
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inventory);
        Assert.assertTrue(
                report.findings().stream().anyMatch(f ->
                        "FW_ORPHAN_TEST_CLASS".equals(f.ruleId()) && "Stray_Test".equals(f.affectedArtifact())),
                report.findings().toString());
    }

    @Test
    public void strayTodoTierTest_stillFailsOrphanMandatory() throws Exception {
        ProvenStep step = new ProvenStep("TC_T", "Login", "elementAction", "click",
                "css", "button", "", "", "", true, "ok");
        TcOutcome todo = new TcOutcome("TC_T", "todo", TcStatus.TODO, List.of(step), "", null, false, List.of());
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(todo));
        Path root = Files.createTempDirectory("p69-stray-todo-");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        Path todoDir = root.resolve("src/test/java/project/tests/todo");
        String emitted = CodegenTestClasses.classNameFor(prepared.outcomes().getFirst(), false);
        Files.writeString(todoDir.resolve("Extra_TestTodo.java"), """
                package project.tests.todo;
                import org.testng.annotations.Test;
                import project.tests.BaseTest;
                import project.pages.Login_Page;
                public class Extra_TestTodo extends BaseTest {
                    private Login_Page loginPage;
                    @Test public void t() { loginPage = new Login_Page(driver); }
                }
                """);
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inventory);
        Assert.assertTrue(
                report.findings().stream().anyMatch(f ->
                        "FW_ORPHAN_TEST_CLASS".equals(f.ruleId()) && f.affectedArtifact().contains("Extra")),
                report.findings().toString());
        Assert.assertTrue(inventory.tests().stream().anyMatch(t -> t.testClassName().equals(emitted)));
    }

    @Test
    public void testLinkedToWrongPage_stillFailsMandatory() throws Exception {
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(passedLogin("TC_1")));
        Path root = Files.createTempDirectory("p69-wrong-page-");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        Files.writeString(
                root.resolve("src/main/java/project/pages/Wrong_Page.java"),
                "package project.pages;\npublic class Wrong_Page { public void action(){} }");
        Path testFile = root.resolve("src/test/java/project/tests/generated/Login_Test.java");
        String body = Files.readString(testFile)
                .replace("Login_Page", "Wrong_Page")
                .replace("loginPage", "wrongPage");
        Files.writeString(testFile, body);
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inventory);
        Assert.assertFalse(report.passed());
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "FW_TEST_USES_PAGE".equals(f.ruleId())
                || "FW_TEST_PAGE_FIELD".equals(f.ruleId())),
                report.findings().toString());
    }

    @Test
    public void relativeWorkspaceRoot_stillDiscoversGeneratedTestsAndIgnoresBaseTest() throws Exception {
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(passedLogin("TC_1")));
        Path relRoot = Path.of("keel-portal/target/p69-relative-framework-root");
        if (Files.exists(relRoot)) {
            deleteRecursively(relRoot);
        }
        Files.createDirectories(relRoot);
        new FrameworkPackager().copyTemplate(TEMPLATE_ROOT, relRoot);
        new CodeWriter(TEMPLATES).write(relRoot, prepared.outcomes());
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var report = FrameworkContractValidator.validateWorkspace(relRoot, TEMPLATE_ROOT, inventory);
        Assert.assertFalse(
                report.findings().stream().anyMatch(f -> "FW_ORPHAN_TEST_CLASS".equals(f.ruleId())),
                report.findings().toString());
        Assert.assertTrue(report.passed(), report.findings().toString());
        deleteRecursively(relRoot);
    }

    private static void deleteRecursively(Path root) throws Exception {
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

    @Test
    public void infrastructureTestAtTestsRoot_notCountedAsGeneratedOrphan() throws Exception {
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(passedLogin("TC_1")));
        Path root = Files.createTempDirectory("p69-infra-root-");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        Files.createDirectories(root.resolve("src/test/java/project/tests"));
        Files.writeString(root.resolve("src/test/java/project/tests/BaseTest.java"), """
                package project.tests;
                public class BaseTest { }
                """);
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inventory);
        Assert.assertFalse(
                report.findings().stream().anyMatch(f -> "FW_ORPHAN_TEST_CLASS".equals(f.ruleId())),
                report.findings().toString());
    }

    private static TcOutcome passedLogin(String tcId) {
        ProvenStep step = new ProvenStep(tcId, "Login", "elementAction", "click",
                "css", "button[data-test='login-button']", "", "", "", true, "ok");
        return new TcOutcome(tcId, "Valid login", TcStatus.PASSED, List.of(step), "", null, false, List.of());
    }
}