package project.pages;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;

/**
 * Generated page for Inventory (fields + actions + assertions + isLoaded in one class).
 */
public class Inventory_Page {
    protected final WebDriverFactory driver;

    public Inventory_Page(WebDriverFactory driver) {
        this.driver = driver;
    }

    /** Parameterized row/list locator factory. */
    protected By productRow(String name) {
        return By.xpath("//div[@class='inventory_list']//div[@class='inventory_item'][.//div[contains(normalize-space(.),'" + name.replace("'", "").replace("\"", "") + "')]]//button");
    }
    /** Parameterized row/list locator factory. */
    protected By productRow_2(String name) {
        return By.xpath("//div[@class='inventory_item'][.//div[contains(normalize-space(.),'" + name.replace("'", "").replace("\"", "") + "')]]//div[@class='inventory_item_price']");
    }
    protected final By inventory_Container_Lbl_Locator = By.cssSelector("[data-test='inventory-container']");

    public void click_Sauce_Labs_Backpack_Button() {
        AllureSteps.run("click_Sauce_Labs_Backpack_Button", () -> {
            driver.element().click(productRow("Sauce Labs Backpack"));
        });
    }

    /** True when a stable page marker is visible (preferred hook / id / test id). */
    public boolean isLoaded() {
        try {
            return driver.element().findElement(inventory_Container_Lbl_Locator).isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void assert_29_99_Value(String expected) {
        AllureSteps.run("assert_29_99_Value", () -> {
            driver.validation().elementValue(productRow_2("Sauce Labs Backpack"), expected);
        });
    }

    public void assert_Inventory_Container_Is_Visible() {
        AllureSteps.run("assert_Inventory_Container_Is_Visible", () -> {
            driver.validation().elementVisable(inventory_Container_Lbl_Locator);
        });
    }

}
