package project.pages;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;
import project.utils.locators.KeelBy;
import project.utils.locators.SettledActions;

/**
 * Generated page for Checkout (fields + actions + assertions + isLoaded in one class).
 */
public class Checkout_Page {
    protected final WebDriverFactory driver;

    public Checkout_Page(WebDriverFactory driver) {
        this.driver = driver;
    }

    /** Keel targets (contract v1), main first then fallbacks. */
    protected final By place_Order_Btn_Locator = KeelBy.of("place_Order_Btn_Locator",
            "css=button[role='button'][aria-label='Place order']",
            "css=button[data-testid='place-order']",
            "css=button.submit");
    /** Multi-locator bag fallback 1 for place_Order_Btn_Locator. */
    protected final By place_Order_Btn_Locator_fb1 = By.css("button[data-testid='place-order']");
    /** Multi-locator bag fallback 2 for place_Order_Btn_Locator. */
    protected final By place_Order_Btn_Locator_fb2 = By.css("button.submit");

    public void click_Place_Order_Button() {
        SettledActions.click(driver, place_Order_Btn_Locator, "click", "click_Place_Order_Button");
    }

    /** True when a stable page marker is visible (preferred hook / id / test id). */
    public boolean isLoaded() {
        try {
            return driver.element().findElement(place_Order_Btn_Locator).isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

}
