package project.pages;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;

/**
 * Generated page for Dashboard (fields + actions + assertions + isLoaded in one class).
 */
public class Dashboard_Page {
    protected final WebDriverFactory driver;

    public Dashboard_Page(WebDriverFactory driver) {
        this.driver = driver;
    }

    protected final By primary_Lbl_Locator = By.cssSelector("nav[aria-label='Primary']");
    protected final By main_Lbl_Locator = By.cssSelector("main");
    protected final By search_Lbl_Locator = By.cssSelector("[aria-label='Search']");

    /** True when a stable page marker is visible (preferred hook / id / test id). */
    public boolean isLoaded() {
        try {
            return driver.element().findElement(primary_Lbl_Locator).isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void assert_Primary_AriaRole(String expected) {
        AllureSteps.run("assert_Primary_AriaRole", () -> {
            driver.validation().elementAriaRole(primary_Lbl_Locator, expected);
        });
    }

    public void assert_Main_Region(String expected) {
        AllureSteps.run("assert_Main_Region", () -> {
            driver.validation().elementRegion(main_Lbl_Locator, expected);
        });
    }

    public void assert_Search_AriaLabel(String expected) {
        AllureSteps.run("assert_Search_AriaLabel", () -> {
            driver.validation().elementAriaLabel(search_Lbl_Locator, expected);
        });
    }

}
