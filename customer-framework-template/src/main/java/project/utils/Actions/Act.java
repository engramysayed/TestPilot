package project.utils.Actions;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import project.drivers.WebDriverFactory;

public final class Act {
    private Act() {
    }

    private static ElementsHandler h() {
        return new ElementsHandler(WebDriverFactory.getDriver());
    }

    public static void click(By locator) {
        h().click(locator);
    }

    public static void type(By locator, String text) {
        h().type(locator, text);
    }

    public static void selectFromDD(By locator, String option) {
        h().selectFromDD(locator, option);
    }

    public static void scrollToElement(By locator) {
        h().scrollToElement(locator);
    }

    public static void uploadFile(By locator, String path) {
        h().uploadFile(locator, path);
    }

    public static WebElement findElement(By locator) {
        return h().findElement(locator);
    }

    public static String getText(By locator) {
        return h().getText(locator);
    }
}
