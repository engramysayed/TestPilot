package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source contract for F00: customer assertion failures must fail TestNG and still quit the browser. */
public class ValidationLifecycleContractTest {

    private static final Path VALIDATION = Path.of(
            "customer-framework-template/src/main/java/project/validations/Validation.java");
    private static final Path LISTENER = Path.of(
            "customer-framework-template/src/main/java/project/listeners/TestNGListeners.java");
    private static final Path BASE_TEST = Path.of(
            "customer-framework-template/src/test/java/project/tests/BaseTest.java");
    private static final Path GENERATED_TEST = Path.of(
            "customer-framework-template/templates/GeneratedTest.java.ftl");
    private static final Path TODO_TEST = Path.of(
            "customer-framework-template/templates/TodoTest.java.ftl");
    private static final Path PAGE_FTL = Path.of(
            "customer-framework-template/templates/Page.java.ftl");

    @Test
    public void validationUsesPerThreadStateAndRethrows() throws Exception {
        String source = Files.readString(VALIDATION);
        Assert.assertTrue(source.contains("ThreadLocal<SoftAssert>"), "assertion state must be per-thread");
        Assert.assertTrue(source.contains("throw e"), "assertAll must rethrow AssertionError");
        Assert.assertFalse(
                source.contains("SoftAssert softAssert=new SoftAssert();")
                        && source.contains("catch (AssertionError e)"),
                "must not keep the F00 swallow + unused local SoftAssert");
    }

    @Test
    public void listenerRecordsAssertionFailureOnTestResult() throws Exception {
        String source = Files.readString(LISTENER);
        Assert.assertTrue(source.contains("ITestResult.FAILURE"), "listener must mark the TestNG result failed");
        Assert.assertTrue(source.contains("setThrowable"), "listener must attach the assertion error");
    }

    @Test
    public void baseTestOwnsLifecycleAndRequiredProperty() throws Exception {
        String base = Files.readString(BASE_TEST);
        Assert.assertTrue(base.contains("@BeforeMethod"), base);
        Assert.assertTrue(base.contains("@AfterMethod"), base);
        Assert.assertTrue(base.contains("try {"), base);
        Assert.assertTrue(base.contains("Validation.assertAll();"), base);
        Assert.assertTrue(base.contains("} finally {"), base);
        Assert.assertTrue(base.contains("driver.quit();"), base);
        Assert.assertTrue(base.contains("requiredProperty"), base);
        Assert.assertTrue(base.contains("new WebDriverFactory()"), base);
        // login-data.json must be gated, not hard-required
        Assert.assertTrue(base.contains("login-data.json"), base);
        Assert.assertTrue(base.contains("Files.isRegularFile"), base);
    }

    @Test
    public void generatedTestsStayThinNoDuplicatedLifecycle() throws Exception {
        String generated = Files.readString(GENERATED_TEST);
        String todo = Files.readString(TODO_TEST);
        String callSite = Files.readString(Path.of(
                "customer-framework-template/templates/test-call-site.ftl"));
        // Call sites load test data via PropertyReader (BaseTest still has requiredProperty for env)
        Assert.assertTrue(callSite.contains("PropertyReader.getProperty"), callSite);
        Assert.assertTrue(generated.contains("test-call-site.ftl"), generated);
        Assert.assertTrue(todo.contains("test-call-site.ftl"), todo);
        Assert.assertFalse(generated.contains("nullToEmpty"), generated);
        // No private requiredProperty / duplicated quit in FTLs
        Assert.assertFalse(generated.contains("private static String requiredProperty"), generated);
        Assert.assertFalse(todo.contains("private static String requiredProperty"), todo);
        Assert.assertFalse(generated.contains("driver.quit();"), generated);
        Assert.assertFalse(todo.contains("driver.quit();"), todo);
        Assert.assertFalse(generated.contains("@AfterMethod"), generated);
        Assert.assertFalse(todo.contains("@AfterMethod"), todo);
        // Override setUp only when login/setup chron calls exist (shared before-method include)
        String before = Files.readString(Path.of(
                "customer-framework-template/templates/test-before-method.ftl"));
        Assert.assertTrue(before.contains("super.setUp()"), before);
        Assert.assertTrue(generated.contains("test-before-method.ftl"), generated);
        Assert.assertTrue(todo.contains("test-before-method.ftl"), todo);
    }

    @Test
    public void pageAssertionsGoThroughValidationHelpers() throws Exception {
        String source = Files.readString(PAGE_FTL);
        Assert.assertTrue(source.contains("driver.validation().textContains"), source);
        Assert.assertTrue(source.contains("driver.validation().bodyTextContains"), source);
        Assert.assertTrue(source.contains("driver.validation().urlContains"), source);
        Assert.assertTrue(source.contains("driver.validation().elementSelected"), source);
        Assert.assertTrue(source.contains("driver.validation().softTrue"), source);
    }

    @Test
    public void generatedTypeMethodsHideAllureValueParameters() throws Exception {
        String source = Files.readString(PAGE_FTL);
        // Plain valued actions wrap Allure in FTL; smart valued actions use SettledActions (Allure inside).
        Assert.assertTrue(source.contains("AllureSteps.run(\"${assertion.name}\""), source);
        Assert.assertTrue(
                source.contains("driver.element().type") || source.contains("SettledActions.type"),
                source);
        Assert.assertTrue(
                source.contains("driver.element().selectFromDD") || source.contains("SettledActions.select"),
                source);
        Assert.assertFalse(source.contains("private void step_"),
                "empty @Step companions must not remain: " + source);
        Assert.assertFalse(source.contains("@Param"), source);
        Assert.assertFalse(
                source.contains("public void ${method.name}(@Param"),
                "typed values must not be @Step parameters: " + source);
        Assert.assertFalse(
                source.contains("@Step(\"${method.name}\")"),
                "valued actions must not use @Step on the public method: " + source);
        Assert.assertFalse(
                source.contains("@Step(\"${assertion.name}\")"),
                "valued assertions must not use @Step on the public method: " + source);
    }
}
