package project.validations;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;

public final class Validate {
    private Validate() {
    }

    private static Validation v() {
        return new Validation(WebDriverFactory.getDriver());
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

    public static void elementUnchecked(By locator) {
        v().elementUnchecked(locator);
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

    public static void elementAriaRole(By locator, String expectedRole) {
        v().elementAriaRole(locator, expectedRole);
    }

    public static void elementAriaLabel(By locator, String expectedLabel) {
        v().elementAriaLabel(locator, expectedLabel);
    }

    public static void elementRegion(By locator, String expectedRegion) {
        v().elementRegion(locator, expectedRegion);
    }

    public static void captureFrom(By locator, String slot, String rule) {
        v().captureFrom(locator, slot, rule);
    }

    public static void compareCapturedExact(By locator, String slot) {
        v().compareCapturedExact(locator, slot);
    }

    public static void signedOut(By locator, String expected) {
        v().signedOut(locator, expected);
    }

    public static void urlContains(String expected) {
        v().urlContains(expected);
    }

    public static void softTrue(boolean condition, String message) {
        v().softTrue(condition, message);
    }

    public static void Equals(String actual, String expected, String message) {
        v().Equals(actual, expected, message);
    }

    public static void assertAll() {
        Validation.assertAll();
    }

    public static void clearCapturedPhrases() {
        Validation.clearCapturedPhrases();
    }
}
