package project.utils;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import project.utils.Logs.LogsManager;
import project.utils.dataReader.PropertyReader;

import java.awt.*;
import java.time.Duration;

public class WaitHandler {
    private final WebDriver driver;

    public WaitHandler(WebDriver driver) {
        this.driver = driver;
    }

    private WebDriverWait getWait() {
        return new WebDriverWait(driver, Duration.ofSeconds(
                Long.parseLong(PropertyReader.getProperty("DEFAULT_WAIT"))
        ));
    }

    public WebElement waitForElementToBeClickable(By locator) {
        try {
            return getWait().until(ExpectedConditions.elementToBeClickable(locator));
        } catch (TimeoutException e) {
            LogsManager.error("Element not clickable within time: " + locator);
            return null;
        }
    }

    public Alert waitForAlert() {
        try {
            return getWait().until(ExpectedConditions.alertIsPresent());
        } catch (TimeoutException e) {
            LogsManager.error("No alert present within time");
            return null;
        }
    }

    public void waitFrameByIndex(int index) {
        try {
            getWait().until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(index));
         } catch (Exception e) {
            LogsManager.error("Failed to switch to frame at index: " + index);
         }
    }

    public void waitFrameByNameOrId(String nameOrId) {
        try {
            getWait().until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(nameOrId));
        } catch (Exception e) {
            LogsManager.error("Failed to switch to frame with name/ID: " + nameOrId);
        }
    }
    public void waitFrameByElement(By locator) {
        try {
            getWait().until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(locator));
        } catch (Exception e) {
            LogsManager.error("Failed to switch to frame with element: " + locator);
        }
    }

    public WebElement waitForElementToBeVisible(By locator) {
        try {
            return getWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            LogsManager.error("Element not visible within time: " + locator);
            return null;
        }
    }
}
