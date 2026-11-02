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

    protected final By login_Button_Btn_Locator = By.id("login-button");
    /** NL-cached validated By: click the login button */
    protected final By nl_click_the_login_button = By.id("login-button");

    public void click_Login_Button() {
        AllureSteps.run("click_Login_Button", () -> {
            driver.element().click(login_Button_Btn_Locator);
        });
    }

    /** True when a stable page marker is visible (preferred hook / id / test id). */
    public boolean isLoaded() {
        try {
            return driver.element().findElement(login_Button_Btn_Locator).isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

}
