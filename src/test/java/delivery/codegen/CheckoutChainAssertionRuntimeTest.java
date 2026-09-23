package delivery.codegen;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Downloaded helpers: locator capture/compare, exact ids, isolation, signed-out from supplied locators.
 */
public class CheckoutChainAssertionRuntimeTest {
    private static final Path VALIDATION = Path.of(
            "customer-framework-template/src/main/java/project/validations/Validation.java");
    private static final Path ELEMENTS = Path.of(
            "customer-framework-template/src/main/java/project/utils/Actions/ElementsHandler.java");

    private Path classesDir;
    private ClassLoader loader;
    private String previousTextWait;

    @BeforeMethod
    public void compileHelpers() throws Exception {
        classesDir = Files.createTempDirectory("explicit-assert-runtime");
        loader = CustomerTemplateCompiler.compile(classesDir, ELEMENTS, VALIDATION);
        previousTextWait = System.getProperty("TEXT_CONTAINS_WAIT_SECONDS");
        System.setProperty("TEXT_CONTAINS_WAIT_SECONDS", "0");
    }

    @AfterMethod
    public void reset() throws Exception {
        if (previousTextWait == null) {
            System.clearProperty("TEXT_CONTAINS_WAIT_SECONDS");
        } else {
            System.setProperty("TEXT_CONTAINS_WAIT_SECONDS", previousTextWait);
        }
        if (loader != null) {
            Class<?> validation = loader.loadClass("project.validations.Validation");
            validation.getMethod("reset").invoke(null);
            validation.getMethod("clearCapturedPhrases").invoke(null);
        }
    }

    @Test
    public void captureThenExactCompareRejectsLongerLookalikeId() throws Exception {
        Object validation = newValidation(elementsDriver(Map.of(
                "order-id", List.of("Order KLA-1001"),
                "order-list", List.of("Order KLA-10010", "Order KLA-1001"))));
        invoke(validation, "captureFrom", new Class<?>[]{By.class, String.class, String.class},
                By.id("order-id"), "orderId", "exactText");
        invoke(validation, "compareCapturedExact", new Class<?>[]{By.class, String.class},
                By.id("order-list"), "orderId");
        assertAllOk();

        Object onlyLonger = newValidation(elementsDriver(Map.of(
                "order-list", List.of("Order KLA-10010"))));
        invoke(onlyLonger, "compareCapturedExact", new Class<?>[]{By.class, String.class},
                By.id("order-list"), "orderId");
        Assert.expectThrows(AssertionError.class, this::assertAllOk);
    }

    @Test
    public void missingCaptureAndAmbiguousExtractionFail() throws Exception {
        Object missing = newValidation(elementsDriver(Map.of("order-list", List.of("Order KLA-1001"))));
        invoke(missing, "compareCapturedExact", new Class<?>[]{By.class, String.class},
                By.id("order-list"), "orderId");
        Assert.expectThrows(AssertionError.class, this::assertAllOk);

        Object ambiguous = newValidation(elementsDriver(Map.of(
                "order-id", List.of("Order KLA-1001", "Order KLA-1002"))));
        invoke(ambiguous, "captureFrom", new Class<?>[]{By.class, String.class, String.class},
                By.id("order-id"), "orderId", "exactText");
        Assert.expectThrows(AssertionError.class, this::assertAllOk);
    }

    @Test
    public void twoIndependentTestsDoNotShareCaptures() throws Exception {
        Object first = newValidation(elementsDriver(Map.of("order-id", List.of("Order KLA-1"))));
        invoke(first, "captureFrom", new Class<?>[]{By.class, String.class, String.class},
                By.id("order-id"), "orderId", "exactText");
        loader.loadClass("project.validations.Validation").getMethod("assertAll").invoke(null);
        loader.loadClass("project.validations.Validation").getMethod("clearCapturedPhrases").invoke(null);

        Object second = newValidation(elementsDriver(Map.of(
                "order-id", List.of("Order KLA-2"),
                "order-list", List.of("Order KLA-2"))));
        invoke(second, "captureFrom", new Class<?>[]{By.class, String.class, String.class},
                By.id("order-id"), "orderId", "exactText");
        invoke(second, "compareCapturedExact", new Class<?>[]{By.class, String.class},
                By.id("order-list"), "orderId");
        assertAllOk();
    }

    @Test
    public void ordinaryTextContainsStillPassesOnPrefix() throws Exception {
        Object validation = newValidation(bodyDriver("Order KLA-10010"));
        invoke(validation, "bodyTextContains", new Class<?>[]{String.class}, "Order KLA-");
        assertAllOk();
    }

    @Test
    public void signedOutUsesSuppliedLocatorAndExpectedText() throws Exception {
        Object gone = newValidation(headerDriver("", "Not signed in"));
        invoke(gone, "signedOut", new Class<?>[]{By.class, String.class}, By.id("session-email"), "empty");
        invoke(gone, "signedOut", new Class<?>[]{By.class, String.class},
                By.id("account-email"), "text:Not signed in");
        assertAllOk();

        Object stillIn = newValidation(headerDriver("buyer.a@example.test", "buyer.a@example.test"));
        invoke(stillIn, "signedOut", new Class<?>[]{By.class, String.class}, By.id("session-email"), "empty");
        Assert.expectThrows(AssertionError.class, this::assertAllOk);
    }

    @Test
    public void unavailableBrowserStateFailsClearly() throws Exception {
        Object validation = newValidation(deadDriver());
        invoke(validation, "captureFrom", new Class<?>[]{By.class, String.class, String.class},
                By.id("order-id"), "orderId", "exactText");
        Assert.expectThrows(AssertionError.class, this::assertAllOk);
    }

    private Object newValidation(WebDriver driver) throws Exception {
        Class<?> validationClass = loader.loadClass("project.validations.Validation");
        return validationClass.getConstructor(WebDriver.class).newInstance(driver);
    }

    private void invoke(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        try {
            target.getClass().getMethod(method, types).invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    private void assertAllOk() throws Exception {
        try {
            loader.loadClass("project.validations.Validation").getMethod("assertAll").invoke(null);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError ae) {
                throw ae;
            }
            throw e;
        }
    }

    private static WebDriver bodyDriver(String bodyText) {
        WebElement body = textElement(bodyText);
        return (WebDriver) Proxy.newProxyInstance(
                CheckoutChainAssertionRuntimeTest.class.getClassLoader(),
                new Class[]{WebDriver.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findElement" -> body;
                    case "findElements" -> List.of(body);
                    case "toString" -> "body-driver";
                    default -> null;
                });
    }

    private static WebDriver elementsDriver(Map<String, List<String>> textsById) {
        return (WebDriver) Proxy.newProxyInstance(
                CheckoutChainAssertionRuntimeTest.class.getClassLoader(),
                new Class[]{WebDriver.class},
                (proxy, method, args) -> {
                    if ("findElements".equals(method.getName())) {
                        String key = idOf((By) args[0]);
                        List<String> texts = textsById.getOrDefault(key, List.of());
                        return texts.stream().map(CheckoutChainAssertionRuntimeTest::textElement).toList();
                    }
                    if ("toString".equals(method.getName())) {
                        return "elements-driver";
                    }
                    return null;
                });
    }

    private static WebDriver headerDriver(String sessionEmail, String accountEmail) {
        Map<String, String> texts = new ConcurrentHashMap<>();
        texts.put("session-email", sessionEmail);
        texts.put("account-email", accountEmail);
        return (WebDriver) Proxy.newProxyInstance(
                CheckoutChainAssertionRuntimeTest.class.getClassLoader(),
                new Class[]{WebDriver.class},
                (proxy, method, args) -> {
                    if (!"findElements".equals(method.getName())) {
                        if ("toString".equals(method.getName())) {
                            return "header-driver";
                        }
                        return null;
                    }
                    String key = idOf((By) args[0]);
                    if (!texts.containsKey(key)) {
                        return List.of();
                    }
                    return List.of(textElement(texts.get(key)));
                });
    }

    private static WebDriver deadDriver() {
        AtomicReference<WebDriver> unused = new AtomicReference<>();
        WebDriver driver = (WebDriver) Proxy.newProxyInstance(
                CheckoutChainAssertionRuntimeTest.class.getClassLoader(),
                new Class[]{WebDriver.class},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "dead";
                    }
                    throw new org.openqa.selenium.NoSuchSessionException("dead");
                });
        unused.set(driver);
        return driver;
    }

    private static String idOf(By by) {
        String s = String.valueOf(by);
        if (s.contains("session-email")) {
            return "session-email";
        }
        if (s.contains("account-email")) {
            return "account-email";
        }
        if (s.contains("order-list")) {
            return "order-list";
        }
        if (s.contains("order-id")) {
            return "order-id";
        }
        return s;
    }

    private static WebElement textElement(String text) {
        return (WebElement) Proxy.newProxyInstance(
                CheckoutChainAssertionRuntimeTest.class.getClassLoader(),
                new Class[]{WebElement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getText" -> text;
                    case "isDisplayed" -> true;
                    case "toString" -> "el:" + text;
                    default -> null;
                });
    }
}
