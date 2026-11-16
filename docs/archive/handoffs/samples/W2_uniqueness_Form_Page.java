// uniqueness: bare role rejected; proven id kept
package project.pages;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;

/**
 * Generated page for Form (fields + actions + assertions + isLoaded in one class).
 */
public class Form_Page {
    protected final WebDriverFactory driver;

    public Form_Page(WebDriverFactory driver) {
        this.driver = driver;
    }

    protected final By start_Date_Btn_Locator = By.id("start-date");

    public void click_Start_Date_Button() {
        AllureSteps.run("click_Start_Date_Button", () -> {
            driver.element().click(start_Date_Btn_Locator);
        });
    }

    /** True when a stable page marker is visible (preferred hook / id / test id). */
    public boolean isLoaded() {
        try {
            return driver.element().findElement(start_Date_Btn_Locator).isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

}
