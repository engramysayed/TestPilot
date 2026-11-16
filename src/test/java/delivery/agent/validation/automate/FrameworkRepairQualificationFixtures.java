package delivery.agent.validation.automate;

import delivery.codegen.CodeWriter;
import delivery.codegen.CodegenEmitGate;
import delivery.codegen.CodegenTestClasses;
import delivery.codegen.ProvenStep;
import delivery.agent.validation.contract.FrameworkExpectedInventory;
import delivery.packager.FrameworkPackager;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic violation fixtures for Phase 6.5 live repair qualification. */
public final class FrameworkRepairQualificationFixtures {
    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");
    private static final Path TEMPLATE_ROOT = Path.of("customer-framework-template").toAbsolutePath().normalize();

    public enum Scenario {
        MISSING_PAGE_LEGACY,
        MISSING_TEST,
        MULTI_PAGE_BAD_IMPORT,
        MISSING_PAGE_LOCATORS,
        MISSING_PAGE_ACTIONS,
        MISSING_PAGE_ASSERTIONS,
        EMPTY_TEST_STUB,
        MIXED_INVENTORY_BAD_IMPORT
    }

    public record Prepared(Path workDir, Path project, FrameworkExpectedInventory inventory, String scenarioId) {
    }

    public static Prepared prepareMissingPageLegacy() throws Exception {
        Path work = Files.createTempDirectory("qual-missing_page-");
        Path project = work.resolve("project");
        var prepared = CodegenEmitGate.prepare(List.of(passedLogin("TC_1")));
        seedCompilableProject(project);
        new CodeWriter(TEMPLATES).write(project, prepared.outcomes());
        Files.deleteIfExists(project.resolve("src/main/java/project/pages/Login_Page.java"));
        return new Prepared(
                work,
                project,
                FrameworkExpectedInventory.fromOutcomes(prepared.outcomes()),
                "MISSING_PAGE");
    }

    public static Prepared prepare(Scenario scenario) throws Exception {
        Path work = Files.createTempDirectory("qual-" + scenario.name().toLowerCase(Locale.ROOT) + "-");
        Path project = work.resolve("project");
        CodegenEmitGate.Prepared prepared = switch (scenario) {
            case MULTI_PAGE_BAD_IMPORT -> multiPageTodo();
            case MIXED_INVENTORY_BAD_IMPORT -> mixedInventory();
            case MISSING_PAGE_ASSERTIONS -> CodegenEmitGate.prepare(List.of(outcomeWithAssertOnly("TC_A")));
            default -> CodegenEmitGate.prepare(List.of(passedLogin("TC_1")));
        };
        seedCompilableProject(project);
        new CodeWriter(TEMPLATES).write(project, prepared.outcomes());
        applyViolation(project, prepared, scenario);
        FrameworkExpectedInventory inventory = FrameworkExpectedInventory.fromOutcomes(prepared.outcomes());
        return new Prepared(work, project, inventory, scenario.name());
    }

    private static void applyViolation(Path project, CodegenEmitGate.Prepared prepared, Scenario scenario)
            throws Exception {
        Path page = project.resolve("src/main/java/project/pages/Login_Page.java");
        String testClass = CodegenTestClasses.classNameFor(prepared.outcomes().get(0), true);
        Path genTest = project.resolve("src/test/java/project/tests/generated/" + testClass + ".java");
        switch (scenario) {
            case MISSING_TEST -> Files.deleteIfExists(genTest);
            case MULTI_PAGE_BAD_IMPORT -> {
                String name = CodegenTestClasses.classNameFor(prepared.outcomes().getFirst(), false);
                stripCartImport(project, name);
            }
            case MIXED_INVENTORY_BAD_IMPORT -> {
                TcOutcome todo = prepared.outcomes().stream()
                        .filter(o -> o.status() == TcStatus.TODO)
                        .findFirst()
                        .orElseThrow();
                String name = CodegenTestClasses.classNameFor(todo, false);
                stripCartImport(project, name);
            }
            case MISSING_PAGE_LOCATORS -> {
                String body = Files.readString(page).replaceAll("protected\\s+final\\s+By\\s+\\w+\\s*=[^;]+;", "");
                Files.writeString(page, body);
            }
            case MISSING_PAGE_ACTIONS -> {
                String body = Files.readString(page);
                body = body.replaceAll("public\\s+void\\s+(?!assert_)[^{]+\\{[^}]*}", "");
                Files.writeString(page, body);
            }
            case MISSING_PAGE_ASSERTIONS -> {
                String body = Files.readString(page);
                body = body.replaceAll("public\\s+void\\s+assert_[^{]+\\{[^}]*}", "");
                Files.writeString(page, body);
            }
            case EMPTY_TEST_STUB -> Files.writeString(genTest, """
                    package project.tests.generated;
                    import org.testng.annotations.Test;
                    import project.tests.BaseTest;
                    import project.pages.Login_Page;
                    public class Login_Test extends BaseTest {
                        private Login_Page loginPage;
                        @Test public void login() { }
                    }
                    """);
        }
    }

    private static CodegenEmitGate.Prepared multiPageTodo() {
        ProvenStep cart = new ProvenStep("TC_P", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "ok");
        ProvenStep checkout = new ProvenStep("TC_P", "CheckoutStepOne", "elementAction", "type",
                "data-test", "firstName", "Test", "", "", true, "ok");
        return CodegenEmitGate.prepare(List.of(new TcOutcome(
                "TC_P", "partial", TcStatus.TODO, List.of(cart, checkout), "stop", null, false, List.of())));
    }

    private static CodegenEmitGate.Prepared mixedInventory() {
        List<TcOutcome> outcomes = new ArrayList<>();
        outcomes.add(passedLogin("TC_OK"));
        outcomes.add(multiPageTodo().outcomes().getFirst());
        outcomes.add(dumpStem("TC_D"));
        return CodegenEmitGate.prepare(outcomes);
    }

    private static TcOutcome outcomeWithAssertOnly(String tcId) {
        ProvenStep click = new ProvenStep(tcId, "Login", "elementAction", "click",
                "css", "button", "", "", "", true, "ok");
        ProvenStep assertStep = new ProvenStep(tcId, "Login", "elementAction", "assert",
                "xpath", "//h1", "", "textContains", "Welcome", true, "intent:ASSERT_VISIBLE");
        return new TcOutcome(tcId, "assert", TcStatus.PASSED, List.of(click, assertStep), "", null, false, List.of());
    }

    private static TcOutcome passedLogin(String tcId) {
        ProvenStep step = new ProvenStep(tcId, "Login", "elementAction", "click",
                "css", "button[data-test='login-button']", "", "", "", true, "ok");
        return new TcOutcome(tcId, "login", TcStatus.PASSED, List.of(step), "", null, false, List.of());
    }

    private static TcOutcome dumpStem(String tcId) {
        ProvenStep dump = new ProvenStep(tcId, "Page", "elementAction", "click", "id", "x", "", "", "", true, "ok");
        return new TcOutcome(tcId, "dump", TcStatus.PASSED, List.of(dump), "", null, false, List.of());
    }

    static void seedCompilableProject(Path project) throws Exception {
        new FrameworkPackager().copyTemplate(TEMPLATE_ROOT, project);
    }

    private static void stripCartImport(Path project, String testClassName) throws Exception {
        Path todo = project.resolve("src/test/java/project/tests/todo/" + testClassName + ".java");
        String body = Files.readString(todo).replace("import project.pages.Cart_Page;", "");
        Files.writeString(todo, body);
    }

    private FrameworkRepairQualificationFixtures() {
    }
}