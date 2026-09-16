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
    private static final Path GENERATED_TEST = Path.of(
            "customer-framework-template/templates/GeneratedTest.java.ftl");
    private static final Path TODO_TEST = Path.of(
            "customer-framework-template/templates/TodoTest.java.ftl");
    private static final Path PAGE_ACTIONS = Path.of(
            "customer-framework-template/templates/PageActions.java.ftl");

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
    public void generatedTeardownQuitsInFinally() throws Exception {
        String generated = Files.readString(GENERATED_TEST);
        String todo = Files.readString(TODO_TEST);
        Assert.assertTrue(generated.contains("try {"), generated);
        Assert.assertTrue(generated.contains("Validation.assertAll();"), generated);
        Assert.assertTrue(generated.contains("} finally {"), generated);
        Assert.assertTrue(generated.contains("driver.quit();"), generated);
        Assert.assertTrue(todo.contains("} finally {"), todo);
        Assert.assertTrue(todo.contains("driver.quit();"), todo);
    }

    @Test
    public void pageAssertionsGoThroughValidationHelpers() throws Exception {
        String source = Files.readString(PAGE_ACTIONS);
        Assert.assertTrue(source.contains("driver.validation().textContains"), source);
        Assert.assertTrue(source.contains("driver.validation().bodyTextContains"), source);
        Assert.assertTrue(source.contains("driver.validation().urlContains"), source);
        Assert.assertTrue(source.contains("driver.validation().elementSelected"), source);
        Assert.assertTrue(source.contains("driver.validation().softTrue"), source);
    }
}
