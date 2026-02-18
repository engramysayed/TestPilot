package utils;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class WaitHandler {

    private final WebDriver driver;
    private int waitSeconds ;

    public WaitHandler(WebDriver driver) {
        this.driver = driver;
     }

    private int getWaitSeconds() {
        return waitSeconds=Integer.parseInt(PropertyReader.getProperty("globalWait"));
    }


    public WebDriverWait waitDriver() {
        return new WebDriverWait(driver, Duration.ofSeconds(getWaitSeconds()));
    }

    public WebElement waitForElementToBeClickable(By locator) {
        try {
            return waitDriver().until(ExpectedConditions.elementToBeClickable(locator));
        } catch (TimeoutException e) {
            LogsManager.error("Element not clickable within " + waitSeconds + "s: " + locator);
            return null;
        }
    }

    public WebElement waitForElementToBeVisible(By locator) {
        try {
            return waitDriver().until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            LogsManager.error("Element not visible within " + waitSeconds + "s: " + locator);
            return null;
        }
    }

    public Alert waitForAlert() {
        try {
            return waitDriver().until(ExpectedConditions.alertIsPresent());
        } catch (TimeoutException e) {
            LogsManager.error("No alert present within " + waitSeconds + "s");
            return null;
        }
    }

    public void waitFrameByIndex(int index) {
        try {
            waitDriver().until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(index));
        } catch (Exception e) {
            LogsManager.error("Failed to switch to frame index " + index + " within " + waitSeconds + "s");
        }
    }

    public void waitFrameByNameOrId(String nameOrId) {
        try {
            waitDriver().until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(nameOrId));
        } catch (Exception e) {
            LogsManager.error("Failed to switch to frame name/id " + nameOrId + " within " + waitSeconds + "s");
        }
    }

    public void waitFrameByElement(By locator) {
        try {
            waitDriver().until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(locator));
        } catch (Exception e) {
            LogsManager.error("Failed to switch to frame by element " + locator + " within " + waitSeconds + "s");
        }
    }
}

