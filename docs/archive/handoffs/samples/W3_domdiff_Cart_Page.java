package project.pages;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;

/**
 * Generated page for Cart (fields + actions + assertions + isLoaded in one class).
 */
public class Cart_Page {
    protected final WebDriverFactory driver;

    public Cart_Page(WebDriverFactory driver) {
        this.driver = driver;
    }

    protected final By checkout_Btn_Locator = By.id("checkout");
    protected final By complete_Header_Lbl_Locator = By.cssSelector("[data-test='complete-header']");
    protected final By total_Lbl_Locator = By.cssSelector(".summary_total_label");
    protected final By spinner_Lbl_Locator = By.id("spinner");

    public void click_Checkout_Button() {
        AllureSteps.run("click_Checkout_Button", () -> {
            driver.element().click(checkout_Btn_Locator);
        });
    }

    /** True when a stable page marker is visible (preferred hook / id / test id). */
    public boolean isLoaded() {
        try {
            return driver.element().findElement(checkout_Btn_Locator).isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void assert_Complete_Header_Is_Visible() {
        AllureSteps.run("assert_Complete_Header_Is_Visible", () -> {
            driver.validation().elementVisable(complete_Header_Lbl_Locator);
        });
    }

    public void assert_Total_Text_Contains(String expected) {
        AllureSteps.run("assert_Total_Text_Contains", () -> {
            driver.validation().textContains(total_Lbl_Locator, expected);
        });
    }

    public void assert_Spinner_Is_Not_Visible() {
        AllureSteps.run("assert_Spinner_Is_Not_Visible", () -> {
            driver.validation().elementNotVisible(spinner_Lbl_Locator);
        });
    }

}
