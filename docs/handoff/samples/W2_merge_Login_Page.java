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

    protected final By username_Txt_Locator = By.id("username");
    protected final By password_Txt_Locator = By.id("password");
    protected final By login_Button_Btn_Locator = By.id("login-button");

    public void type_Username(String value) {
        AllureSteps.run("type_Username", () -> {
            driver.element().type(username_Txt_Locator, value);
        });
    }

    public void type_Password(String value) {
        AllureSteps.run("type_Password", () -> {
            driver.element().type(password_Txt_Locator, value);
        });
    }

    public void click_Login_Button() {
        AllureSteps.run("click_Login_Button", () -> {
            driver.element().click(login_Button_Btn_Locator);
        });
    }

    /** True when a stable page marker is visible (preferred hook / id / test id). */
    public boolean isLoaded() {
        try {
            return driver.element().findElement(username_Txt_Locator).isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Preferred login API: type credentials then submit. */
    public void login(String username, String password) {
        type_Username(username);
        type_Password(password);
        click_Login_Button();
    }

}
