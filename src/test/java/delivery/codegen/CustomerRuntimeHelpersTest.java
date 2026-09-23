package delivery.codegen;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * F01 / F02 / F06: downloaded click/type/absence helpers must not go green on a dead session,
 * and typed values must not appear in routine logs.
 */
public class CustomerRuntimeHelpersTest {
    private static final Path ELEMENTS = Path.of(
            "customer-framework-template/src/main/java/project/utils/Actions/ElementsHandler.java");
    private static final Path VALIDATION = Path.of(
            "customer-framework-template/src/main/java/project/validations/Validation.java");
    private static final Path PROPERTY_READER = Path.of(
            "customer-framework-template/src/main/java/project/utils/dataReader/PropertyReader.java");

    private Path classesDir;
    private ClassLoader loader;
    private String previousDefaultWait;
    private String previousTextWait;
    private String previousNotVisibleWait;

    @BeforeMethod
    public void compileHelpers() throws Exception {
        classesDir = Files.createTempDirectory("customer-runtime-helpers");
        loader = CustomerTemplateCompiler.compile(classesDir, ELEMENTS, VALIDATION);
        previousDefaultWait = System.getProperty("DEFAULT_WAIT");
        previousTextWait = System.getProperty("TEXT_CONTAINS_WAIT_SECONDS");
        previousNotVisibleWait = System.getProperty("NOT_VISIBLE_WAIT_SECONDS");
        System.setProperty("DEFAULT_WAIT", "0");
        System.setProperty("TEXT_CONTAINS_WAIT_SECONDS", "0");
        System.setProperty("NOT_VISIBLE_WAIT_SECONDS", "0");
    }

    @AfterMethod
    public void resetValidation() throws Exception {
        restoreProperty("DEFAULT_WAIT", previousDefaultWait);
        restoreProperty("TEXT_CONTAINS_WAIT_SECONDS", previousTextWait);
        restoreProperty("NOT_VISIBLE_WAIT_SECONDS", previousNotVisibleWait);
        if (loader != null) {
            Class<?> validation = loader.loadClass("project.validations.Validation");
            validation.getMethod("reset").invoke(null);
        }
    }

    @Test
    public void clickOnDeadSessionThrowsTypedFailure() throws Exception {
        Object handler = newHandler(deadDriver());
        InvocationTargetException thrown = Assert.expectThrows(
                InvocationTargetException.class,
                () -> handler.getClass().getMethod("click", By.class).invoke(handler, By.id("save")));
        Assert.assertNotNull(thrown.getCause(), "click must not swallow the failure");
        Assert.assertTrue(
                thrown.getCause().getClass().getName().endsWith("ReplayActionException")
                        || thrown.getCause() instanceof RuntimeException,
                "failed click must throw a typed execution failure: " + thrown.getCause());
        assertAllDoesNotMaskActionFailure();
    }

    @Test
    public void typeOnDeadSessionThrowsTypedFailure() throws Exception {
        Object handler = newHandler(deadDriver());
        InvocationTargetException thrown = Assert.expectThrows(
                InvocationTargetException.class,
                () -> handler.getClass().getMethod("type", By.class, String.class)
                        .invoke(handler, By.id("email"), "review@example.invalid"));
        Assert.assertNotNull(thrown.getCause(), "type must not swallow the failure");
        Assert.assertTrue(thrown.getCause() instanceof RuntimeException, String.valueOf(thrown.getCause()));
        assertAllDoesNotMaskActionFailure();
    }

    @Test
    public void elementNotVisibleFailsOnDeadSession() throws Exception {
        Class<?> validationClass = loader.loadClass("project.validations.Validation");
        Object validation = validationClass.getConstructor(WebDriver.class).newInstance(deadDriver());
        try {
            validationClass.getMethod("elementNotVisible", By.class).invoke(validation, By.id("error"));
        } catch (InvocationTargetException ignored) {
            // helper may fail immediately; assertAll must still not pass
        }
        Assert.expectThrows(AssertionError.class, () -> {
            try {
                validationClass.getMethod("assertAll").invoke(null);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof AssertionError ae) {
                    throw ae;
                }
                throw e;
            }
        });
    }

    @Test
    public void elementNotVisibleFailsWhenAnyMatchIsVisible() throws Exception {
        assertAbsence(List.of(element(false), element(true)), true);
    }

    @Test
    public void elementNotVisiblePassesWhenEveryMatchIsHidden() throws Exception {
        assertAbsence(List.of(element(false), element(false)), false);
    }

    @Test
    public void elementNotVisiblePassesWhenThereAreNoMatches() throws Exception {
        assertAbsence(List.of(), false);
    }

    @Test
    public void elementNotVisibleWaitsUntilAllMatchesDisappear() throws Exception {
        System.setProperty("NOT_VISIBLE_WAIT_SECONDS", "2");
        AtomicInteger polls = new AtomicInteger();
        WebDriver driver = matchesDriver(() -> polls.incrementAndGet() < 2
                ? List.of(element(true))
                : List.of());
        invokeNotVisible(driver);
        Assert.assertTrue(polls.get() >= 2, "must poll until later matches disappear");
        loader.loadClass("project.validations.Validation").getMethod("assertAll").invoke(null);
    }

    @Test
    public void bodyTextWaitRefindsBodyAfterNavigationStalesIt() throws Exception {
        System.setProperty("TEXT_CONTAINS_WAIT_SECONDS", "2");
        AtomicInteger finds = new AtomicInteger();
        WebDriver driver = matchesDriver(() -> {
            int attempt = finds.incrementAndGet();
            return List.of((WebElement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{WebElement.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getText")) {
                            if (attempt == 1) throw new org.openqa.selenium.StaleElementReferenceException("navigation");
                            return "Order KLA-1001";
                        }
                        return null;
                    }));
        });
        Assert.assertTrue(bodyTextWait(driver));
        Assert.assertTrue(finds.get() >= 2, "must locate the replacement body");
    }

    @Test
    public void bodyTextWaitDoesNotHideFatalBrowserErrors() throws Exception {
        for (RuntimeException failure : List.of(new NoSuchSessionException("dead"),
                new org.openqa.selenium.InvalidSelectorException("invalid"),
                new org.openqa.selenium.WebDriverException("transport"))) {
            WebDriver driver = matchesDriver(() -> { throw failure; });
            InvocationTargetException thrown = Assert.expectThrows(InvocationTargetException.class,
                    () -> bodyTextWait(driver));
            Assert.assertSame(thrown.getCause(), failure);
        }
    }

    @Test
    public void bodyTextWaitTimesOutWhenBodyStaysStale() throws Exception {
        Assert.assertFalse(bodyTextWait(matchesDriver(() -> {
            throw new org.openqa.selenium.StaleElementReferenceException("still navigating");
        })));
    }

    private boolean bodyTextWait(WebDriver driver) throws Exception {
        Class<?> type = loader.loadClass("project.utils.WaitHandler");
        Object wait = type.getConstructor(WebDriver.class).newInstance(driver);
        return (boolean) type.getMethod("waitUntilBodyTextContains", String.class)
                .invoke(wait, "Order KLA-");
    }

    @Test
    public void typeHelperSourceOmitsTypedValueFromLogs() throws Exception {
        String source = Files.readString(ELEMENTS);
        int typeAt = source.indexOf("public void type(");
        Assert.assertTrue(typeAt >= 0, source);
        int next = source.indexOf("public void ", typeAt + 1);
        String typeMethod = source.substring(typeAt, next > typeAt ? next : source.length());
        Assert.assertFalse(typeMethod.contains("with text:"), typeMethod);
        Assert.assertFalse(typeMethod.contains("+ text"), typeMethod);
        Assert.assertFalse(typeMethod.contains("\" with text\""), typeMethod);
    }

    @Test
    public void propertyReaderLoadsUtf8ReaderNotLatin1Stream() throws Exception {
        String source = Files.readString(PROPERTY_READER);
        Assert.assertTrue(
                source.contains("UTF_8") || source.contains("UTF-8"),
                "PropertyReader must load properties as UTF-8: " + source);
        Assert.assertFalse(
                source.contains("properties.load(FileUtils.openInputStream"),
                "ISO-8859-1 InputStream load cannot round-trip Unicode");
    }

    @Test
    public void propertyReaderDoesNotCopyLoadedKeysIntoSystemProperties() throws Exception {
        String source = Files.readString(PROPERTY_READER);
        Assert.assertFalse(
                source.contains("System.getProperties().putAll"),
                "copying testdata into System properties leaks values into Surefire XML: " + source);
    }

    private void assertAbsence(List<WebElement> matches, boolean expectFailure) throws Exception {
        invokeNotVisible(matchesDriver(() -> matches));
        Class<?> validation = loader.loadClass("project.validations.Validation");
        if (expectFailure) {
            Assert.expectThrows(AssertionError.class, () -> {
                try {
                    validation.getMethod("assertAll").invoke(null);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof AssertionError ae) {
                        throw ae;
                    }
                    throw e;
                }
            });
        } else {
            validation.getMethod("assertAll").invoke(null);
        }
    }

    private void invokeNotVisible(WebDriver driver) throws Exception {
        Class<?> validationClass = loader.loadClass("project.validations.Validation");
        Object validation = validationClass.getConstructor(WebDriver.class).newInstance(driver);
        try {
            validationClass.getMethod("elementNotVisible", By.class).invoke(validation, By.cssSelector(".error"));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError) {
                throw e;
            }
        }
    }

    private static WebDriver matchesDriver(java.util.function.Supplier<List<WebElement>> matches) {
        return (WebDriver) Proxy.newProxyInstance(
                CustomerRuntimeHelpersTest.class.getClassLoader(),
                new Class[]{WebDriver.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findElement" -> {
                        List<WebElement> els = matches.get();
                        if (els.isEmpty()) {
                            throw new org.openqa.selenium.NoSuchElementException("no .error");
                        }
                        yield els.get(0);
                    }
                    case "findElements" -> matches.get();
                    case "toString" -> "multi-match-driver";
                    default -> null;
                });
    }

    private static WebElement element(boolean displayed) {
        return (WebElement) Proxy.newProxyInstance(
                CustomerRuntimeHelpersTest.class.getClassLoader(),
                new Class[]{WebElement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isDisplayed" -> displayed;
                    case "toString" -> "review-element-" + displayed;
                    default -> null;
                });
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private Object newHandler(WebDriver driver) throws Exception {
        Class<?> handler = loader.loadClass("project.utils.Actions.ElementsHandler");
        return handler.getConstructor(WebDriver.class).newInstance(driver);
    }

    private void assertAllDoesNotMaskActionFailure() throws Exception {
        Class<?> validation = loader.loadClass("project.validations.Validation");
        validation.getMethod("assertAll").invoke(null);
    }

    private static WebDriver deadDriver() {
        AtomicReference<WebDriver> self = new AtomicReference<>();
        WebDriver driver = (WebDriver) Proxy.newProxyInstance(
                CustomerRuntimeHelpersTest.class.getClassLoader(),
                new Class[]{WebDriver.class, JavascriptExecutor.class},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "dead-session-driver";
                    }
                    throw new NoSuchSessionException("REVIEW_DEAD_SESSION");
                });
        self.set(driver);
        return driver;
    }
}
