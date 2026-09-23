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

    public boolean waitUntilTextContains(By locator, String expected) {
        try {
            new WebDriverWait(driver, durationSeconds("TEXT_CONTAINS_WAIT_SECONDS", 15))
                    .until(d -> {
                        String actual = d.findElement(locator).getText();
                        return actual != null && actual.contains(expected);
                    });
            return true;
        } catch (TimeoutException e) {
            LogsManager.error("Text did not contain '" + expected + "' within time: " + locator);
            return false;
        }
    }

    public boolean waitUntilBodyTextContains(String expected) {
        try {
            new WebDriverWait(driver, durationSeconds("TEXT_CONTAINS_WAIT_SECONDS", 15))
                    .ignoring(StaleElementReferenceException.class)
                    .until(d -> {
                        String body = d.findElement(By.tagName("body")).getText();
                        return body != null && body.contains(expected);
                    });
            return true;
        } catch (TimeoutException e) {
            LogsManager.error("Body text did not contain '" + expected + "' within time");
            return false;
        }
    }

    public boolean waitUntilElementNotVisible(By locator) {
        try {
            new WebDriverWait(driver, durationSeconds("NOT_VISIBLE_WAIT_SECONDS", 10))
                    .until(d -> {
                        java.util.List<WebElement> els = d.findElements(locator);
                        if (els == null || els.isEmpty()) {
                            return true;
                        }
                        return els.stream().noneMatch(el -> {
                            try {
                                return el.isDisplayed();
                            } catch (StaleElementReferenceException stale) {
                                return false;
                            }
                        });
                    });
            return true;
        } catch (TimeoutException e) {
            LogsManager.error("Element still visible within time: " + locator);
            return false;
        }
    }

    public void waitForPageReady() {
        try {
            getWait().until(driver -> "complete".equals(String.valueOf(
                    ((JavascriptExecutor) driver).executeScript("return document.readyState"))));
            Thread.sleep(500);
        } catch (Exception e) {
            LogsManager.error("waitForPageReady: " + e.getMessage());
        }
    }

    private static Duration durationSeconds(String key, long defaultSeconds) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            raw = PropertyReader.getProperty(key);
        }
        if (raw == null || raw.isBlank()) {
            return Duration.ofSeconds(defaultSeconds);
        }
        return Duration.ofSeconds(Long.parseLong(raw.trim()));
    }
}
