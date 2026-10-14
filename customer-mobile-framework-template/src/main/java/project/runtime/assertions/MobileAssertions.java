package project.runtime.assertions;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import project.runtime.capture.CapturedValues;
import project.runtime.waits.MobileWait;
import project.utils.reports.AllureSteps;

/** visible / exactText / capture / compare — parity with prove semantics. No blocking sleep. */
public final class MobileAssertions {
    private MobileAssertions() {}

    public static void assertVisible(AndroidDriver driver, By locator) {
        AllureSteps.step("assertVisible " + locator, () -> {
            WebElement el = MobileWait.untilPresent(driver, locator);
            if (!el.isDisplayed()) {
                throw new AssertionError("Element not visible: " + locator);
            }
            return null;
        });
    }

    public static void assertExactText(AndroidDriver driver, By locator, String expected) {
        AllureSteps.step("assertExactText " + locator, () -> {
            WebElement el = MobileWait.untilPresent(driver, locator);
            String actual = textOf(el);
            if (expected == null ? actual != null : !expected.equals(actual)) {
                throw new AssertionError("exact text mismatch for " + locator
                        + " expected=[" + expected + "] actual=[" + actual + "]");
            }
            return null;
        });
    }

    public static String captureText(AndroidDriver driver, By locator, String variableName) {
        return AllureSteps.step("captureText " + variableName, () -> {
            WebElement el = MobileWait.untilPresent(driver, locator);
            String text = textOf(el);
            CapturedValues.put(variableName, text);
            return text;
        });
    }

    /**
     * COMPARE_CAPTURED prove semantics: compare current element text to a prior capture.
     * Modes: EQUALS (default), CONTAINS, EQUALS_IGNORE_CASE.
     */
    public static void compareCaptured(AndroidDriver driver, By locator, String variableName, String compareMode) {
        String mode = compareMode == null || compareMode.isBlank() ? "EQUALS" : compareMode.trim().toUpperCase();
        AllureSteps.step("compareCaptured " + variableName + " mode=" + mode, () -> {
            String expected = CapturedValues.get(variableName);
            WebElement el = MobileWait.untilPresent(driver, locator);
            String actual = textOf(el);
            boolean ok = switch (mode) {
                case "CONTAINS" -> actual != null && actual.contains(expected);
                case "EQUALS_IGNORE_CASE" -> expected.equalsIgnoreCase(actual == null ? "" : actual);
                default -> expected.equals(actual);
            };
            if (!ok) {
                throw new AssertionError("compareCaptured failed var=" + variableName
                        + " mode=" + mode + " expected=[" + expected + "] actual=[" + actual + "]");
            }
            return null;
        });
    }

    private static String textOf(WebElement el) {
        String t = el.getText();
        if (t == null || t.isEmpty()) {
            String attr = el.getAttribute("content-desc");
            if (attr != null && !attr.isEmpty()) {
                return attr;
            }
            attr = el.getAttribute("text");
            return attr == null ? "" : attr;
        }
        return t;
    }
}
