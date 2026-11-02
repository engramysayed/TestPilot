package project.pages;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;

/**
 * Generated page for Login (fields + actions + assertions + isLoaded in one class).
 */
public class Login_Page {
    protected final WebDriverFactory driver;

    public Login_Page(WebDriverFactory driver) {
        this.driver = driver;
    }

    protected final By user_Name_Txt_Locator = By.cssSelector("[data-test='username']");
    protected final By password_Txt_Locator = By.id("password");

    public void type_User_Name(String value) {
        AllureSteps.run("type_User_Name", () -> {
            driver.element().type(user_Name_Txt_Locator, value);
        });
    }

    public void type_Password(String value) {
        AllureSteps.run("type_Password", () -> {
            driver.element().type(password_Txt_Locator, value);
        });
    }

    /** True when a stable page marker is visible (preferred hook / id / test id). */
    public boolean isLoaded() {
        try {
            return driver.element().findElement(user_Name_Txt_Locator).isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

}
