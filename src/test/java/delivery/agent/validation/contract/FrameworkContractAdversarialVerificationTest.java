package delivery.agent.validation.contract;

import delivery.codegen.CodeWriter;
import delivery.codegen.CodegenEmitGate;
import delivery.codegen.CodegenTestClasses;
import delivery.codegen.PageAccumulator;
import delivery.codegen.ProvenStep;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase 6.1 adversarial matrix — documents expected rule IDs vs actual validator output.
 * Read-only verification; does not alter production validators.
 */
public class FrameworkContractAdversarialVerificationTest {

    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");
    private static final Path TEMPLATE_ROOT = Path.of("customer-framework-template").toAbsolutePath().normalize();

    // --- 1. Inventory consistency ---

    @Test
    public void inventory_pagesMatchPreparedAccumulator_notRawPageAccumulator() {
        List<TcOutcome> raw = List.of(
                passedLogin("TC_1"),
                dumpStemOutcome("TC_DUMP"));
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(raw);
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        PageAccumulator emitPages = prepared.pages();
        PageAccumulator viaAccumulate = CodeWriter.accumulate(prepared.outcomes());
        Assert.assertEquals(inv.pages().size(), emitPages.pages().size(),
                "inventory pages must match prepared.pages() used at write");
        Assert.assertEquals(viaAccumulate.pages().keySet(), emitPages.pages().keySet(),
                "CodeWriter.accumulate must agree with CodegenEmitGate.prepare pages");
        Set<String> invClasses = inv.expectedPageClassNames();
        for (var e : emitPages.pages().values()) {
            Assert.assertTrue(invClasses.contains(e.pageClassName()));
        }
        Assert.assertFalse(invClasses.contains("Page_Page"), "demoted dump stem must not appear in inventory");
    }

    @Test
    public void inventory_testListMatchesCodegenPerOutcome() {
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(
                passedLogin("TC_A"), passedLogin("TC_B")));
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        Assert.assertEquals(inv.tests().size(), 1, "merged Login tests");
        Assert.assertEquals(inv.tests().get(0).tcIds().size(), 2);
        for (TcOutcome o : prepared.outcomes()) {
            String expected = CodegenTestClasses.classNameFor(o, o.status() == TcStatus.PASSED);
            Assert.assertTrue(inv.expectedTestClassNames().contains(expected));
        }
    }

  @Test
    public void inventory_documentationUsesCodeWriterAccumulate_notDirectPageAccumulator() {
        // Handoff "PageAccumulator from outcomes" == CodeWriter.accumulate (filter + normalize + step fold).
        List<TcOutcome> raw = List.of(passedLogin("TC_X"));
        PageAccumulator manual = new PageAccumulator();
        for (TcOutcome o : raw) {
            manual.addAll(o.provenSteps());
        }
        PageAccumulator canonical = CodeWriter.accumulate(raw);
        Assert.assertEquals(canonical.pages().size(), manual.pages().size(),
                "for unfiltered outcomes without emit filters difference may be zero");
        Assert.assertNotNull(canonical.pages().get("Login"));
    }

    // --- 17 scenario matrix (via data provider + dedicated tests) ---

    @DataProvider(name = "adversarial")
    public Object[][] adversarial() {
        return new Object[][] {
                {"S01_valid_emit", List.of("PASS"), List.of(), true, Set.of()},
                {"S02_missing_page", List.of("MISSING_PAGE"), List.of("FW_EXPECTED_PAGE_PRESENT"), false, Set.of()},
                {"S03_missing_test", List.of("MISSING_TEST"), List.of("FW_EXPECTED_TEST_PRESENT"), false, Set.of()},
                {"S04_page_no_test_file", List.of("ORPHAN_EXTRA_PAGE"), List.of("FW_ORPHAN_PAGE_CLASS"), false, Set.of()},
                {"S05_test_no_page_file", List.of("MISSING_PAGE"), List.of("FW_EXPECTED_PAGE_PRESENT"), false, Set.of()},
                {"S06_dup_page", List.of("DUP_PAGE"), List.of("FW_ARCH_DUPLICATE_PAGE"), false, Set.of()},
                {"S07_dup_test", List.of("DUP_TEST"), List.of("FW_ARCH_DUPLICATE_TEST"), false, Set.of()},
                {"S08_page_no_locators", List.of("PAGE_NO_LOCATORS"), List.of("FW_PAGE_MISSING_LOCATORS"), false, Set.of()},
                {"S09_page_no_actions", List.of("PAGE_NO_ACTIONS"), List.of("FW_PAGE_MISSING_ACTIONS"), false, Set.of()},
                {"S10_page_no_asserts", List.of("PAGE_NO_ASSERTS"), List.of("FW_PAGE_MISSING_ASSERTIONS"), false, Set.of()},
                {"S11_import_no_use", List.of("IMPORT_NO_USE"), List.of("FW_TEST_PAGE_UNUSED"), false, Set.of()},
                {"S12_field_no_use", List.of("FIELD_NO_USE"), List.of("FW_TEST_PAGE_UNUSED"), false, Set.of()},
                {"S13_empty_stub", List.of("EMPTY_STUB"), List.of("FW_TEST_STUB_EMPTY"), false, Set.of()},
                {"S15_todo_tier", List.of("TODO_TIER"), List.of(), true, Set.of()},
                {"S16_demoted_dump", List.of("DUMP_DEMOTE"), List.of(), true,
                        Set.of("FW_EXPECTED_PAGE_PRESENT", "FW_TEST_USES_PAGE", "FW_EXPECTED_PAGE_TEST_LINK")},
                {"S17_real_templates", List.of("REAL_EMIT"), List.of(), true, Set.of()},
        };
    }

    @Test(dataProvider = "adversarial")
    public void matrixScenario(
            String id,
            List<String> flags,
            List<String> expectedMandatoryRules,
            boolean expectedPass,
            Set<String> forbiddenRules) throws Exception {
        ScenarioFixture fx = buildFixture(flags);
        var report = FrameworkContractValidator.validateWorkspace(fx.root, TEMPLATE_ROOT, fx.inventory);
        List<String> actualMandatory = report.findings().stream()
                .filter(f -> f.severity() == ValidationSeverity.MANDATORY)
                .map(ValidationFinding::ruleId)
                .distinct()
                .sorted()
                .toList();
        for (String rule : expectedMandatoryRules) {
            Assert.assertTrue(
                    actualMandatory.contains(rule),
                    id + ": expected mandatory " + rule + " but got " + actualMandatory);
        }
        if (expectedPass) {
            Assert.assertTrue(report.passed(), id + ": " + report.findings());
        } else if (expectedMandatoryRules.isEmpty()) {
            Assert.assertFalse(report.passed(), id);
        }
        for (String forbidden : forbiddenRules) {
            Assert.assertFalse(actualMandatory.contains(forbidden), id + ": unexpected " + forbidden);
        }
    }

    @Test
    public void s14_twoTcsShareOnePageTestPair_passes() throws Exception {
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(
                passedLogin("TC_01"), passedLogin("TC_02")));
        Path root = Files.createTempDirectory("adv-s14");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inv);
        Assert.assertTrue(report.passed(), report.findings().toString());
        Assert.assertEquals(inv.tests().size(), 1);
    }

    @Test
    public void falsePositive_multiPageTodoWithCartAndCheckout() throws Exception {
        ProvenStep cart = new ProvenStep("TC_P", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        ProvenStep checkout = new ProvenStep("TC_P", "CheckoutStepOne", "elementAction", "type",
                "data-test", "firstName", "Test", "", "", true, "auto-fill:type");
        TcOutcome outcome = new TcOutcome(
                "TC_P", "Checkout flow", TcStatus.TODO, List.of(cart, checkout),
                "stopped", null, false, List.of());
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(List.of(outcome));
        Path root = Files.createTempDirectory("adv-fp-multipage");
        new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        var report = FrameworkContractValidator.validateWorkspace(root, TEMPLATE_ROOT, inv);
        Assert.assertTrue(report.passed(), "D-6.1-01 fixed: " + report.findings());
        Assert.assertTrue(
                inv.tests().getFirst().linkedPageClassNames().contains("Cart_Page")
                        && inv.tests().getFirst().linkedPageClassNames().contains("CheckoutStepOne_Page"));
    }

    @Test
    public void contractV2_distinguishableFromV1() throws Exception {
        Path root = Files.createTempDirectory("adv-ver");
        var v1 = FrameworkContractValidator.validateWorkspace(root, null, null);
        Assert.assertEquals(v1.contractVersion(), ValidationReport.VERSION);
        Assert.assertTrue(v1.metadata().isEmpty());
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(List.of(passedLogin("TC_1")));
        var v2 = FrameworkContractValidator.validateWorkspace(root, null, inv);
        Assert.assertEquals(v2.contractVersion(), FrameworkContractValidator.CONTRACT_VERSION_INVENTORY);
        Assert.assertEquals(v2.metadata().get("validationMode"), "inventory");
        Assert.assertEquals(v2.contractId(), ValidationContractIds.FRAMEWORK_CONTRACT_V1);
    }

    @Test
    public void repairObjective_usesMandatoryOnly() throws Exception {
        List<ValidationFinding> mix = List.of(
                new ValidationFinding("FW_EXPECTED_PAGE_PRESENT", "x", ValidationSeverity.MANDATORY,
                        "a", "f", null, "", ""),
                new ValidationFinding("FW_HELPER_THREADSLEEP", "y", ValidationSeverity.RECOMMENDED,
                        "a", "f", null, "", ""));
        ValidationReport rep = ValidationReport.of(
                ValidationContractIds.FRAMEWORK_CONTRACT_V1,
                FrameworkContractValidator.CONTRACT_VERSION_INVENTORY,
                mix,
                FrameworkExpectedInventory.fromOutcomes(List.of()).toReportMetadata());
        Assert.assertEquals(rep.mandatoryMessages().size(), 1);
        Assert.assertTrue(rep.toApiMap().containsKey("contractVersion"));
        Assert.assertEquals(rep.toApiMap().get("contractVersion"),
                FrameworkContractValidator.CONTRACT_VERSION_INVENTORY);
    }

    // --- fixture builder ---

    private record ScenarioFixture(Path root, FrameworkExpectedInventory inventory) {
    }

    private static ScenarioFixture buildFixture(List<String> flags) throws Exception {
        Path root = Files.createTempDirectory("adv-" + flags.get(0).toLowerCase(Locale.ROOT));
        List<TcOutcome> outcomes = new ArrayList<>();
        if (flags.contains("DUMP_DEMOTE")) {
            outcomes.add(dumpStemOutcome("TC_D"));
        } else if (flags.contains("TODO_TIER") || flags.contains("REAL_EMIT")) {
            outcomes.add(passedLogin("TC_1"));
        } else if (flags.contains("PAGE_NO_ASSERTS")) {
            outcomes.add(outcomeWithAssertOnly("TC_A"));
        } else {
            outcomes.add(passedLogin("TC_1"));
        }
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(outcomes);
        if (flags.contains("REAL_EMIT") || flags.contains("PASS") || flags.contains("TODO_TIER")
                || flags.contains("DUMP_DEMOTE") || flags.contains("IMPORT_NO_USE")
                || flags.contains("MISSING_PAGE") || flags.contains("MISSING_TEST")
                || flags.contains("ORPHAN_EXTRA_PAGE") || flags.contains("DUP_PAGE")
                || flags.contains("DUP_TEST") || flags.contains("PAGE_NO_LOCATORS")
                || flags.contains("PAGE_NO_ACTIONS") || flags.contains("PAGE_NO_ASSERTS")
                || flags.contains("FIELD_NO_USE") || flags.contains("EMPTY_STUB")) {
            if (!flags.contains("MISSING_PAGE") && !flags.contains("PAGE_NO_LOCATORS")
                    && !flags.contains("PAGE_NO_ACTIONS") && !flags.contains("PAGE_NO_ASSERTS")) {
                new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
            } else if (flags.contains("MISSING_PAGE")) {
                new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
                Files.deleteIfExists(pageFile(root, prepared, "Login_Page"));
            } else {
                new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
                mutatePage(root, flags);
            }
            if (flags.stream().anyMatch(f -> f.startsWith("IMPORT_") || f.startsWith("FIELD_") || f.equals("EMPTY_STUB")
                    || f.equals("MISSING_TEST"))) {
                mutateTest(root, prepared, flags);
            }
        }
        if (flags.contains("ORPHAN_EXTRA_PAGE")) {
            Path pages = root.resolve("src/main/java/project/pages");
            Files.createDirectories(pages);
            Files.writeString(pages.resolve("Stray_Page.java"), "package project.pages;\npublic class Stray_Page {}");
        }
        if (flags.contains("DUP_PAGE")) {
            Path pages = root.resolve("src/main/java/project/pages");
            Path dupDir = pages.resolve("dup");
            Files.createDirectories(dupDir);
            Files.writeString(dupDir.resolve("Login_Page.java"), Files.readString(pageFile(root, prepared, "Login_Page")));
        }
        if (flags.contains("DUP_TEST")) {
            Path gen = root.resolve("src/test/java/project/tests/generated");
            Files.createDirectories(gen.resolve("dup"));
            Path test = gen.resolve("Login_Test.java");
            if (Files.exists(test)) {
                Files.writeString(gen.resolve("dup/Login_Test.java"), Files.readString(test));
            }
        }
        if (flags.contains("TODO_TIER")) {
            outcomes = List.of(new TcOutcome(
                    "TC_T", "partial", TcStatus.TODO,
                    List.of(new ProvenStep("TC_T", "Cart", "elementAction", "click",
                            "id", "c", "", "", "", true, "ok")),
                    "fail", null, false, List.of()));
            prepared = CodegenEmitGate.prepare(outcomes);
            new CodeWriter(TEMPLATES).write(root, prepared.outcomes());
        }
        FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        return new ScenarioFixture(root, inv);
    }

    private static void mutatePage(Path root, List<String> flags) throws Exception {
        Path page = root.resolve("src/main/java/project/pages/Login_Page.java");
        if (!Files.exists(page)) {
            return;
        }
        String body = Files.readString(page);
        if (flags.contains("PAGE_NO_LOCATORS")) {
            body = body.replaceAll("protected\\s+final\\s+By\\s+\\w+\\s*=[^;]+;", "");
        }
        if (flags.contains("PAGE_NO_ACTIONS")) {
            body = body.replaceAll("public\\s+void\\s+(?!assert_)[^{]+\\{[^}]*}", "");
        }
        if (flags.contains("PAGE_NO_ASSERTS")) {
            body = body.replaceAll("public\\s+void\\s+assert_[^{]+\\{[^}]*}", "");
        }
        Files.writeString(page, body);
    }

    private static void mutateTest(Path root, CodegenEmitGate.Prepared prepared, List<String> flags) throws Exception {
        String testClass = CodegenTestClasses.classNameFor(prepared.outcomes().get(0), true);
        Path test = root.resolve("src/test/java/project/tests/generated/" + testClass + ".java");
        if (!Files.exists(test) && flags.contains("TODO_TIER")) {
            test = root.resolve("src/test/java/project/tests/todo/" + testClass + ".java");
        }
        if (!Files.exists(test)) {
            if (flags.contains("MISSING_TEST")) {
                return;
            }
        }
        if (flags.contains("MISSING_TEST")) {
            Files.deleteIfExists(test);
            return;
        }
        String body = Files.readString(test);
        if (flags.contains("IMPORT_NO_USE")) {
            body = body.replaceAll("loginPage\\.[\\w_]+\\([^)]*\\);", "");
            body = body.replaceAll("loginPage\\s*=\\s*new Login_Page\\(driver\\);", "");
        }
        if (flags.contains("FIELD_NO_USE")) {
            body = body.replaceAll("loginPage\\.[\\w_]+\\([^)]*\\);", "");
            body = body.replaceAll("loginPage\\s*=\\s*new Login_Page\\(driver\\);", "");
        }
        if (flags.contains("EMPTY_STUB")) {
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
            return;
        }
        Files.writeString(test, body);
    }

    private static Path pageFile(Path root, CodegenEmitGate.Prepared prepared, String name) {
        return root.resolve("src/main/java/project/pages/" + name + ".java");
    }

    private static TcOutcome passedLogin(String tcId) {
        ProvenStep step = new ProvenStep(tcId, "Login", "elementAction", "click",
                "css", "button[data-test='login-button']", "", "", "", true, "ok");
        return new TcOutcome(tcId, "login", TcStatus.PASSED, List.of(step), "", null, false, List.of());
    }

    private static TcOutcome dumpStemOutcome(String tcId) {
        ProvenStep dump = new ProvenStep(tcId, "Page", "elementAction", "click", "id", "x", "", "", "", true, "ok");
        return new TcOutcome(tcId, "dump", TcStatus.PASSED, List.of(dump), "", null, false, List.of());
    }

    private static TcOutcome outcomeWithAssertOnly(String tcId) {
        ProvenStep click = new ProvenStep(tcId, "Login", "elementAction", "click",
                "css", "button", "", "", "", true, "ok");
        ProvenStep assertStep = new ProvenStep(tcId, "Login", "elementAction", "assert",
                "xpath", "//h1", "", "textContains", "Welcome", true, "intent:ASSERT_VISIBLE");
        return new TcOutcome(tcId, "assert", TcStatus.PASSED, List.of(click, assertStep), "", null, false, List.of());
    }
}