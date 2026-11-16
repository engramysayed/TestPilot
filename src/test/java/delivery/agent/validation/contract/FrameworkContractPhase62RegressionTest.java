package delivery.agent.validation.contract;

import delivery.codegen.CodeWriter;
import delivery.codegen.CodegenEmitGate;
import delivery.codegen.CodegenTestClasses;
import delivery.codegen.ProvenStep;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Phase 6.2 regressions for D-6.1-01 and D-6.1-02. */
public class FrameworkContractPhase62RegressionTest {

    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");
    private static final Path TEMPLATE_ROOT = Path.of("customer-framework-template").toAbsolutePath().normalize();
    private static final Pattern PAGE_IMPORT = Pattern.compile("import project\\.pages\\.(\\w+);");

    @Test
    public void d601_multiPageTodo_inventoryMatchesCodegenUnion_andPassesValidation() throws Exception {
        ProvenStep cart = new ProvenStep("TC_P", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        ProvenStep checkout = new ProvenStep("TC_P", "CheckoutStepOne", "elementAction", "type",
                "data-test", "firstName", "Test", "", "", true, "auto-fill:type");
        TcOutcome outcome = new TcOutcome(
                "TC_P", "Checkout flow", TcStatus.TODO, List.of(cart, checkout),
                "stopped", null, false, List.of());
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(outcome));
        Path root = Files.createTempDirectory("p62-mp");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());

        String testName = CodegenTestClasses.classNameFor(prepared.outcomes().getFirst(), false);
        String java = Files.readString(root.resolve(
                "src/test/java/project/tests/todo/" + testName + ".java"));
        List<String> emittedImports = parsePageImports(java);
        Assert.assertTrue(emittedImports.contains("Cart_Page"), emittedImports.toString());
        Assert.assertTrue(emittedImports.contains("CheckoutStepOne_Page"), emittedImports.toString());

        FrameworkExpectedInventory.ExpectedTest expectedTest = inv.tests().stream()
                .filter(t -> t.testClassName().equals(testName))
                .findFirst()
                .orElseThrow();
        Assert.assertEquals(expectedTest.linkedPageClassNames(), emittedImports);

        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inv);
        Assert.assertFalse(
                report.findings().stream().anyMatch(f -> "FW_EXPECTED_PAGE_TEST_LINK".equals(f.ruleId())),
                report.findings().toString());
        Assert.assertTrue(report.passed(), report.findings().toString());
    }

    @Test
    public void d601_missingPageImport_stillFailsMandatory() throws Exception {
        ProvenStep cart = new ProvenStep("TC_P", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        ProvenStep checkout = new ProvenStep("TC_P", "CheckoutStepOne", "elementAction", "type",
                "data-test", "firstName", "Test", "", "", true, "auto-fill:type");
        TcOutcome outcome = new TcOutcome(
                "TC_P", "Checkout flow", TcStatus.TODO, List.of(cart, checkout),
                "stopped", null, false, List.of());
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(outcome));
        Path root = Files.createTempDirectory("p62-link");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        String testName = CodegenTestClasses.classNameFor(prepared.outcomes().getFirst(), false);
        Path testFile = root.resolve("src/test/java/project/tests/todo/" + testName + ".java");
        String body = Files.readString(testFile).replace("import project.pages.Cart_Page;", "");
        Files.writeString(testFile, body);
        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inv);
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "FW_TEST_USES_PAGE".equals(f.ruleId())));
        Assert.assertFalse(report.passed());
    }

    @Test
    public void d602_demotedDumpStem_noPhantomPagePage_andPasses() throws Exception {
        ProvenStep dump = new ProvenStep("TC_D", "Page", "elementAction", "click", "id", "x", "", "", "", true, "ok");
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(new TcOutcome(
                "TC_D", "dump", TcStatus.PASSED, List.of(dump), "", null, false, List.of())));
        Path root = Files.createTempDirectory("p62-dump");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        Assert.assertFalse(inv.expectedPageClassNames().contains("Page_Page"));
        FrameworkExpectedInventory.ExpectedTest todo = inv.tests().stream()
                .filter(t -> t.testClassName().endsWith("TestTodo"))
                .findFirst()
                .orElseThrow();
        Assert.assertTrue(todo.linkedPageClassNames().isEmpty());

        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inv);
        Assert.assertFalse(report.findings().stream().anyMatch(f -> "FW_TEST_USES_PAGE".equals(f.ruleId())));
        Assert.assertTrue(report.passed(), report.findings().toString());
    }

    @Test
    public void mixedFixture_generatedMultiPageTodoAndDemotedDump() throws Exception {
        ProvenStep login = new ProvenStep("TC_1", "Login", "elementAction", "click",
                "css", "button", "", "", "", true, "ok");
        TcOutcome ok = new TcOutcome("TC_1", "ok", TcStatus.PASSED, List.of(login), "", null, false, List.of());

        ProvenStep cart = new ProvenStep("TC_P", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "ok");
        ProvenStep checkout = new ProvenStep("TC_P", "CheckoutStepOne", "elementAction", "type",
                "data-test", "n", "x", "", "", true, "ok");
        TcOutcome partial = new TcOutcome(
                "TC_P", "partial", TcStatus.TODO, List.of(cart, checkout), "stop", null, false, List.of());

        ProvenStep dump = new ProvenStep("TC_D", "Page", "elementAction", "click", "id", "x", "", "", "", true, "ok");
        TcOutcome dumpStem = new TcOutcome("TC_D", "dump", TcStatus.PASSED, List.of(dump), "", null, false, List.of());

        ProvenStep inv = new ProvenStep("TC_2", "InventoryPage", "elementAction", "click",
                "id", "i", "", "", "", true, "ok");
        TcOutcome ok2 = new TcOutcome("TC_2", "inv", TcStatus.PASSED, List.of(inv), "", null, false, List.of());

        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(ok, partial, dumpStem, ok2));
        Path root = Files.createTempDirectory("p62-mixed");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        Assert.assertEquals(inventory.tests().size(), 4, "Login + Inventory + multi-page todo + dump todo");

        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inventory);
        Assert.assertTrue(report.passed(), report.findings().toString());
    }

    @Test
    public void d602_twoLoginsShareOneTest_inventoryListsBothTcIds() throws Exception {
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(
                passedLogin("TC_A"), passedLogin("TC_B")));
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        Assert.assertEquals(inv.tests().size(), 1);
        Assert.assertEquals(inv.tests().getFirst().tcIds().size(), 2);
        Assert.assertEquals(inv.tests().getFirst().linkedPageClassNames(), List.of("Login_Page"));
    }

    private static List<String> parsePageImports(String java) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        Matcher m = PAGE_IMPORT.matcher(java);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    public void codegenUnionHelper_matchesEmittedImports() throws Exception {
        ProvenStep cart = new ProvenStep("TC_P", "Cart", "elementAction", "click",
                "data-test", "c", "", "", "", true, "ok");
        ProvenStep checkout = new ProvenStep("TC_P", "CheckoutStepOne", "elementAction", "type",
                "data-test", "n", "v", "", "", true, "ok");
        TcOutcome outcome = new TcOutcome(
                "TC_P", "x", TcStatus.TODO, List.of(cart, checkout), "s", null, false, List.of());
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(outcome));
        Path root = Files.createTempDirectory("p62-parity");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        String cls = CodegenTestClasses.classNameFor(prepared.outcomes().getFirst(), false);
        String java = Files.readString(root.resolve("src/test/java/project/tests/todo/" + cls + ".java"));
        List<String> emitted = parsePageImports(java);
        List<String> expected = CodeWriter.expectedUnionPageImportsForGroup(
                prepared.outcomes(), prepared.pages(), "");
        Assert.assertEquals(expected, emitted, "expected=" + expected + " emitted=" + emitted);
    }

    private static TcOutcome passedLogin(String tcId) {
        ProvenStep step = new ProvenStep(tcId, "Login", "elementAction", "click",
                "css", "button[data-test='login-button']", "", "", "", true, "ok");
        return new TcOutcome(tcId, "login", TcStatus.PASSED, List.of(step), "", null, false, List.of());
    }
}