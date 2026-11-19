package project.validations;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;

public final class Verify {
    private Verify() {
    }

    private static Verification v() {
        return new Verification(WebDriverFactory.getDriver());
    }

    public static void elementVisable(By locator) {
        v().elementVisable(locator);
    }

    public static void elementNotVisible(By locator) {
        v().elementNotVisible(locator);
    }

    public static void elementEnabled(By locator) {
        v().elementEnabled(locator);
    }

    public static void elementDisabled(By locator) {
        v().elementDisabled(locator);
    }

    public static void textContains(By locator, String expected) {
        v().textContains(locator, expected);
    }

    public static void elementTextEquals(By locator, String expected) {
        v().elementTextEquals(locator, expected);
    }

    public static void elementValue(By locator, String expected) {
        v().elementValue(locator, expected);
    }

    public static void elementSelected(By locator, String expectedOptionOrEmpty) {
        v().elementSelected(locator, expectedOptionOrEmpty);
    }

    public static void Equals(String actual, String expected, String message) {
        v().Equals(actual, expected, message);
    }
}
