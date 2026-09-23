package delivery.codegen;

import delivery.ir.TcIdentity;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Emit-side acceptance for the 2026-09-21 codegen review: identity, data keys,
 * case IDs, merge, page files, and properties round-trip.
 */
public class CodegenReviewFixesTest {
    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");

    @Test
    public void repeatedEditsKeepBothValuesUnderOccurrenceKeys() throws Exception {
        Path dir = Files.createTempDirectory("cg01-repeated");
        ProvenStep first = step("TC_EDIT", "Profile", "type", "id", "email", "first@example.invalid");
        ProvenStep second = step("TC_EDIT", "Profile", "type", "id", "email", "second@example.invalid");
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_EDIT", first, second)));

        String data = Files.readString(dir.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        Assert.assertTrue(data.contains("first@example.invalid"), data);
        Assert.assertTrue(data.contains("second@example.invalid"), data);
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_EDIT.java"));
        Assert.assertTrue(test.contains(".1"), test);
        Assert.assertTrue(test.contains(".2"), test);
        Assert.assertFalse(test.contains("\"first@example.invalid\""), test);
        Assert.assertFalse(test.contains("\"second@example.invalid\""), test);
    }

    @Test
    public void distinctValidCaseIdsEmitDistinctJavaFiles() throws Exception {
        Path dir = Files.createTempDirectory("cg02-ids");
        ProvenStep a = step("TC_A_B", "PageOne", "click", "id", "one", "");
        ProvenStep b = step("TC_A__B", "PageOne", "click", "id", "two", "");
        Assert.assertTrue(TcIdentity.isValid("TC_A_B"));
        Assert.assertTrue(TcIdentity.isValid("TC_A__B"));
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_A_B", a), outcome("TC_A__B", b)));
        Path generated = dir.resolve("src/test/java/project/tests/generated");
        Assert.assertTrue(Files.isRegularFile(generated.resolve("TC_A_B.java")));
        Assert.assertTrue(Files.isRegularFile(generated.resolve("TC_A__B.java")));
        try (var list = Files.list(generated)) {
            Assert.assertEquals(list.filter(p -> p.getFileName().toString().endsWith(".java")).count(), 2L);
        }
        String first = Files.readString(generated.resolve("TC_A_B.java"));
        String second = Files.readString(generated.resolve("TC_A__B.java"));
        Assert.assertTrue(first.contains("click_One_Button") || first.contains("one"), first);
        Assert.assertTrue(second.contains("click_Two_Button") || second.contains("two"), second);
        Assert.assertFalse(first.contains("click_Two_Button"), first);
    }

    @Test
    public void selectedAssertionsShareParameterizedMethodAndKeepBothExpectations() throws Exception {
        Path dir = Files.createTempDirectory("cg03-assert");
        ProvenStep france = new ProvenStep("TC_ASSERT", "Cart", "elementAction", "assert",
                "id", "country", "", "selected", "France", true, "");
        ProvenStep germany = new ProvenStep("TC_ASSERT", "Cart", "elementAction", "assert",
                "id", "country", "", "selected", "Germany", true, "");
        PageAccumulator acc = new PageAccumulator();
        acc.addAll(List.of(france, germany));
        Assert.assertEquals(acc.pages().get("Cart").assertions().size(), 1,
                "same control + selected must share one parameterized assertion");
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_ASSERT", france, germany)));
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_ASSERT.java"));
        Assert.assertTrue(test.contains("\"France\""), test);
        Assert.assertTrue(test.contains("\"Germany\""), test);
        String actions = Files.readString(dir.resolve("src/main/java/project/pages/Cart_Actions.java"));
        Assert.assertTrue(actions.contains("assert_Country_Is_Selected(String expected)"), actions);
        Assert.assertTrue(actions.contains("elementSelected("), actions);
    }

    @Test
    public void distinctLocatorsKeepDistinctTypeMethods() {
        ProvenStep hyphen = step("TC_TYPE", "Profile", "type", "id", "first-name", "Alice");
        ProvenStep underscore = step("TC_TYPE", "Profile", "type", "id", "first_name", "Bob");
        PageAccumulator acc = new PageAccumulator();
        acc.addAll(List.of(hyphen, underscore));
        PageAccumulator.PageModel page = acc.pages().get("Profile");
        Assert.assertEquals(page.fields().size(), 2, String.valueOf(page.fields()));
        Assert.assertEquals(page.methods().size(), 2, String.valueOf(page.methods()));
        Set<String> names = page.methods().stream().map(PageAccumulator.MethodModel::name).collect(Collectors.toSet());
        Assert.assertEquals(names.size(), 2, String.valueOf(names));
        CodegenSmellCheck.verify(acc.pages());
    }

    @Test
    public void promoIdDoesNotMergeWithSubmitSelector() throws Exception {
        Path dir = Files.createTempDirectory("cg05-merge");
        ProvenStep promo = step("TC_SUBMIT", "Cart", "click", "id", "promo", "");
        ProvenStep submit = new ProvenStep("TC_SUBMIT", "Cart", "elementAction", "click",
                "css", "button[type='submit']", "", "", "", true, "");
        PageAccumulator acc = new PageAccumulator();
        acc.addAll(List.of(promo, submit));
        Assert.assertEquals(acc.pages().get("Cart").fields().size(), 2, String.valueOf(acc.pages().get("Cart").fields()));
        Assert.assertEquals(acc.pages().get("Cart").methods().size(), 2, String.valueOf(acc.pages().get("Cart").methods()));
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_SUBMIT", promo, submit)));
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_SUBMIT.java"));
        String actions = Files.readString(dir.resolve("src/main/java/project/pages/Cart_Actions.java"));
        String locators = Files.readString(dir.resolve("src/main/java/project/pages/Cart_Locators.java"));
        Assert.assertTrue(test.contains("click_Promo_Button"), test);
        Assert.assertTrue(actions.contains("public void click_Promo_Button"), actions);
        Assert.assertTrue(locators.contains("button[type='submit']"), locators);
        long clickCalls = test.lines().filter(l -> l.contains(".click_")).count();
        Assert.assertEquals(clickCalls, 2L, test);
    }

    @Test
    public void unicodeAndWhitespaceRoundTripThroughUtf8Properties() throws Exception {
        Path dir = Files.createTempDirectory("cg06-props");
        ProvenStep unicode = step("TC_UNICODE", "Profile", "type", "id", "name", "مرحبا café");
        ProvenStep spaces = new ProvenStep("TC_SPACES", "Profile", "elementAction", "type",
                "id", "note", "  leading\rnext", "", "", true, "");
        new CodeWriter(TEMPLATES).write(dir, List.of(
                outcome("TC_UNICODE", unicode),
                outcome("TC_SPACES", spaces)));
        Path propsFile = dir.resolve("src/test/resources/test-data/delivery-testdata.properties");
        Properties loaded = new Properties();
        try (Reader reader = Files.newBufferedReader(propsFile, StandardCharsets.UTF_8)) {
            loaded.load(reader);
        }
        Assert.assertEquals(
                loaded.getProperty(findKey(loaded, "مرحبا café")),
                "مرحبا café");
        Assert.assertEquals(
                loaded.getProperty(findKey(loaded, "leading")),
                "  leading\rnext");
    }

    @Test
    public void collidingPageNamesGetUniqueStemsAndKeepBothMethods() throws Exception {
        Path dir = Files.createTempDirectory("cg08-pages");
        ProvenStep first = step("TC_PAGES", "Order-Details", "click", "id", "first", "");
        ProvenStep second = step("TC_PAGES", "OrderDetails", "click", "id", "second", "");
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_PAGES", first, second)));
        Path pages = dir.resolve("src/main/java/project/pages");
        try (var list = Files.list(pages)) {
            long actionFiles = list.filter(p -> p.getFileName().toString().endsWith("_Actions.java")).count();
            Assert.assertEquals(actionFiles, 2L, "distinct page identities must not overwrite one Actions file");
        }
        String combined = Files.readString(pages.resolve("OrderDetails_Actions.java"))
                + Files.readString(findOtherActions(pages, "OrderDetails_Actions.java"));
        Assert.assertTrue(combined.contains("click_First_Button"), combined);
        Assert.assertTrue(combined.contains("click_Second_Button"), combined);
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_PAGES.java"));
        Assert.assertTrue(test.contains("click_First_Button"), test);
        Assert.assertTrue(test.contains("click_Second_Button"), test);
    }

    @Test
    public void generatedTypeMethodsHideAllureParameters() throws Exception {
        Path dir = Files.createTempDirectory("cg-allure-hide");
        ProvenStep secret = step("TC_CANARY", "Profile", "type", "id", "secret", "CANARY_PW_zipreplay_7f2c9a");
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_CANARY", secret)));
        String actions = Files.readString(dir.resolve("src/main/java/project/pages/Profile_Actions.java"));
        Assert.assertTrue(actions.contains("public void type_Secret(String value)"), actions);
        Assert.assertTrue(actions.contains("AllureSteps.run(\"type_Secret\""), actions);
        Assert.assertTrue(actions.contains("driver.element().type"), actions);
        Assert.assertFalse(actions.contains("private void step_type_Secret()"), actions);
        Assert.assertFalse(actions.contains("@Step(\"type_Secret\")"), actions);
        Assert.assertFalse(actions.contains("CANARY_PW_zipreplay_7f2c9a"), actions);
    }

    @Test
    public void generatedAssertionMethodsHideAllureExpectedParameters() throws Exception {
        Path dir = Files.createTempDirectory("cg-allure-assert");
        ProvenStep heading = new ProvenStep("TC_READY", "Home", "elementAction", "assert",
                "id", "status", "", "textContains", "CANARY_ASSERT_zipreplay_7f2c9a", true, "");
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_READY", heading)));
        String actions = Files.readString(dir.resolve("src/main/java/project/pages/Home_Actions.java"));
        Assert.assertTrue(actions.contains("public void assert_Status_Text_Contains(String expected)")
                        || actions.contains("(String expected)"),
                actions);
        Assert.assertTrue(actions.contains("AllureSteps.run("), actions);
        Assert.assertTrue(actions.contains("driver.validation()"), actions);
        Assert.assertFalse(actions.contains("private void step_"), actions);
        Assert.assertFalse(actions.contains("@Param"), actions);
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_READY.java"));
        Assert.assertTrue(test.contains("CANARY_ASSERT_zipreplay_7f2c9a"),
                "assertion expected remains in generated test source:\n" + test);
    }

    @Test
    public void conflictingValuesForTheSameKeyFailInsteadOfOverwrite() throws Exception {
        Path dir = Files.createTempDirectory("v01-conflict");
        ProvenStep first = step("TC_DUP", "LoginPage", "type", "id", "email", "alpha@example.invalid");
        ProvenStep second = step("TC_DUP", "LoginPage", "type", "id", "email", "beta@example.invalid");
        PageAccumulator pages = new PageAccumulator();
        pages.addAll(List.of(first, second));
        CodegenDataKeys keys = CodegenDataKeys.forcingKey(pages, "TC_DUP.body.type_Email.1");
        try {
            TestDataPropertiesWriter.write(dir, List.of(outcome("TC_DUP", first, second)), pages, keys);
            Assert.fail("expected conflicting testdata values to fail closed");
        } catch (IllegalStateException ex) {
            String msg = String.valueOf(ex.getMessage());
            Assert.assertTrue(msg.contains("Conflicting test data"), msg);
            Assert.assertTrue(msg.contains("TC_DUP.body.type_Email.1"), msg);
            Assert.assertTrue(msg.contains("alpha@example.invalid"), msg);
            Assert.assertTrue(msg.contains("beta@example.invalid"), msg);
        }
        Assert.assertFalse(
                Files.exists(dir.resolve("src/test/resources/test-data/delivery-testdata.properties")),
                "must not publish testdata after a key conflict");
    }

    @Test
    public void prerequisiteLoginValueSurvivesLeafSetupInlining() throws Exception {
        Path dir = Files.createTempDirectory("v01-prereq-keys");
        ProvenStep setupLogin = step("TC_SETUP", "LoginPage", "type", "id", "email", "login@example.invalid");
        ProvenStep setupBody = step("TC_SETUP", "LoginPage", "type", "id", "email", "changed@example.invalid");
        ProvenStep leafLogin = step("TC_LEAF", "LoginPage", "type", "id", "email", "leaf@example.invalid");
        TcOutcome prerequisite = new TcOutcome(
                "TC_SETUP", "Setup", TcStatus.PASSED, List.of(setupBody), "", null, true, List.of(setupLogin));
        TcOutcome leaf = new TcOutcome(
                "TC_LEAF", "Leaf", TcStatus.PASSED, List.of(), "", null, true, List.of(leafLogin), List.of(setupBody));
        new CodeWriter(TEMPLATES).write(dir, List.of(prerequisite, leaf));

        Properties data = loadProps(dir);
        String loginKey = findKey(data, "login@example.invalid");
        String bodyKey = findKey(data, "changed@example.invalid");
        String leafKey = findKey(data, "leaf@example.invalid");
        Assert.assertNotEquals(loginKey, bodyKey, data.toString());
        Assert.assertEquals(data.getProperty(loginKey), "login@example.invalid");
        Assert.assertEquals(data.getProperty(bodyKey), "changed@example.invalid");
        Assert.assertEquals(data.getProperty(leafKey), "leaf@example.invalid");

        String setupTest = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_SETUP.java"));
        String leafTest = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_LEAF.java"));
        Assert.assertTrue(setupTest.contains(loginKey), setupTest);
        Assert.assertTrue(setupTest.contains(bodyKey), setupTest);
        Assert.assertTrue(leafTest.contains(leafKey), leafTest);
        Assert.assertTrue(leafTest.contains(bodyKey), leafTest);
        Assert.assertFalse(leafTest.contains(loginKey), leafTest);
    }

    @Test
    public void prerequisiteLoginValueSurvivesWhenLeafInlinesLoginAndBody() throws Exception {
        Path dir = Files.createTempDirectory("v01-prereq-no-elision");
        ProvenStep setupLogin = step("TC_SETUP", "LoginPage", "type", "id", "email", "login@example.invalid");
        ProvenStep setupBody = step("TC_SETUP", "LoginPage", "type", "id", "email", "changed@example.invalid");
        ProvenStep leafClick = step("TC_LEAF", "Home", "click", "id", "go", "");
        TcOutcome prerequisite = new TcOutcome(
                "TC_SETUP", "Setup", TcStatus.PASSED, List.of(setupBody), "", null, true, List.of(setupLogin));
        TcOutcome leaf = new TcOutcome(
                "TC_LEAF", "Leaf", TcStatus.PASSED, List.of(leafClick), "", null, false, List.of())
                .withSetup(List.of(setupLogin, setupBody));
        new CodeWriter(TEMPLATES).write(dir, List.of(prerequisite, leaf));

        Properties data = loadProps(dir);
        Assert.assertEquals(data.getProperty(findKey(data, "login@example.invalid")), "login@example.invalid");
        Assert.assertEquals(data.getProperty(findKey(data, "changed@example.invalid")), "changed@example.invalid");
        String leafTest = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_LEAF.java"));
        Assert.assertTrue(leafTest.contains(findKey(data, "login@example.invalid")), leafTest);
        Assert.assertTrue(leafTest.contains(findKey(data, "changed@example.invalid")), leafTest);
    }

    @Test
    public void locatorFreeTextContainsEmitsBodyAssertionAndPageClass() throws Exception {
        Path dir = Files.createTempDirectory("cg07-body");
        ProvenStep body = new ProvenStep("TC_BODY", "Cart", "elementAction", "assert",
                "", "", "", "textContains", "Saved", true, "");
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_BODY", body)));
        Assert.assertTrue(Files.exists(dir.resolve("src/main/java/project/pages/Cart_Actions.java")));
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_BODY.java"));
        Assert.assertTrue(test.contains("assert_"), test);
        String actions = Files.readString(dir.resolve("src/main/java/project/pages/Cart_Actions.java"));
        Assert.assertTrue(actions.contains("bodyTextContains"), actions);
        Assert.assertTrue(test.contains("\"Saved\"") || actions.contains("Saved"), test + "\n" + actions);
    }

    private static Properties loadProps(Path dir) throws Exception {
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(
                dir.resolve("src/test/resources/test-data/delivery-testdata.properties"),
                StandardCharsets.UTF_8)) {
            data.load(reader);
        }
        return data;
    }

    private static Path findOtherActions(Path pages, String skip) throws Exception {
        try (var list = Files.list(pages)) {
            return list.filter(p -> p.getFileName().toString().endsWith("_Actions.java"))
                    .filter(p -> !p.getFileName().toString().equals(skip))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static String findKey(Properties props, String valueNeedle) {
        for (String name : props.stringPropertyNames()) {
            String v = props.getProperty(name);
            if (v != null && v.contains(valueNeedle)) {
                return name;
            }
        }
        Assert.fail("missing value containing " + valueNeedle + " in " + props);
        return "";
    }

    private static ProvenStep step(
            String id, String page, String action, String strategy, String locator, String value) {
        return new ProvenStep(id, page, "elementAction", action, strategy, locator, value, "", "", true, "");
    }

    private static TcOutcome outcome(String id, ProvenStep... steps) {
        return new TcOutcome(id, "Review fixture", TcStatus.PASSED, List.of(steps), "", null, false, List.of());
    }
}
