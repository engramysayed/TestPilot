package handlingLayer;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import utils.LogsManager;
import org.openqa.selenium.support.ui.Select;
import utils.WaitHandler;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ElementsHandler {
    private final WebDriver driver;
    private final WaitHandler waitHandler;

    public ElementsHandler(WebDriver driver) {
        this.driver = driver;
        this.waitHandler = new WaitHandler(driver);
    }

    public String scrollToElement(By locator) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({behavior: 'auto', block: 'center', inline: 'center'});",
                    findElement(locator)
            );
            return "true";
        } catch (Exception e) {
            LogsManager.error("Failed to scroll to element: " + locator);
            return "false -> Failed to scroll to element: " + locator;
        }
    }

    public WebElement findElement(By locator) {
        return driver.findElement(locator);
    }

    public String selectFromDD(By locator, String option) {
        try {
            scrollToElement(locator);
            waitHandler.waitForElementToBeVisible(locator);
            new Select(findElement(locator)).selectByValue(option);
            LogsManager.info("Option selected successfully from dropdown: " + locator + " option: " + option);
            return "true";
        } catch (Exception e) {
            LogsManager.error("Failed to select option from dropdown: " + locator + " " + e);
            return "false -> Failed to select option from dropdown: " + locator + " " + e;
        }
    }

    public String uploadFile(By locator, String path){
        try {
            String filePath = System.getProperty("user.dir") + File.separator + path;
            waitHandler.waitForElementToBeVisible(locator);
            findElement(locator).sendKeys(filePath);
            LogsManager.info("File uploaded successfully: " + path);
            return "true";
        } catch (Exception e) {
            LogsManager.error("Failed to upload file: " + path + " " + e);
            return "false -> Failed to upload file: " + path + " " + e;
        }
    }

    public String click(By locator) {
        try {
            scrollToElement(locator);
            waitHandler.waitForElementToBeVisible(locator);
            findElement(locator).click();
            return "true";
        } catch (Exception e) {
            LogsManager.error("Failed to click element: " + locator + " " + e);
            return "false -> Failed to click element: " + locator + " " + e;
        }
    }

    public String type(By locator, String text) {
        try {
            waitHandler.waitForElementToBeVisible(locator);
            WebElement el = findElement(locator);
            el.clear();
            el.sendKeys(text);
            LogsManager.info("Text typed into element: " + locator);
            return "true";
        } catch (Exception e) {
            LogsManager.error("Failed to type into element: " + locator + " " + e);
            return "false -> Failed to type into element: " + locator + " " + e;
        }
    }

    public String getText(By locator) {
        try {
            waitHandler.waitForElementToBeVisible(locator);
            String text = findElement(locator).getText();
            if (text != null) {
                if (!text.isEmpty()) {
                    return text;
                }
            }
            return null;
        } catch (Exception e) {
            LogsManager.error("Failed to get text from: " + locator + " " + e);
            return "false -> Failed to get text from: " + locator + " " + e;
        }
    }

    public String clear(By locator) {
        try {
            waitHandler.waitForElementToBeVisible(locator);
            findElement(locator).clear();
            return "true";
        } catch (Exception e) {
            LogsManager.error("Failed to clear element: " + locator + " " + e);
            return "false -> Failed to clear element: " + locator + " " + e;
        }
    }

    public String getAttributeValue(By locator, String attributeName) {
        try {
            waitHandler.waitForElementToBeVisible(locator);
            String val = findElement(locator).getAttribute(attributeName);
            if (val != null) {
                if (!val.isEmpty()) {
                    return val;
                }
            }
            return "";
        } catch (Exception e) {
            LogsManager.error("Failed to get attribute value: " + locator + " " + e);
            return "false -> Failed to get attribute value: " + locator + " " + e;
        }
    }

    public void capture(Path runFolder, int stepId) {
        capture(runFolder, "step_" + stepId);
    }

    public void capture(Path runFolder, String nameWithoutExtension) {
        try {
            Path screenshotsDir = runFolder.resolve("screenshots");
            Files.createDirectories(screenshotsDir);

            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path target = screenshotsDir.resolve(nameWithoutExtension + ".png");

            Files.copy(src.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            LogsManager.info("Screenshot saved: " + target.toAbsolutePath());

        } catch (Exception e) {
            LogsManager.error("Failed to capture screenshot: " + e);
        }
    }



    public String dragDrop(By sourceLocator, By targetLocator) {
        try {
            scrollToElement(sourceLocator);
            waitHandler.waitForElementToBeVisible(sourceLocator);
            waitHandler.waitForElementToBeVisible(targetLocator);

            WebElement source = findElement(sourceLocator);
            WebElement target = findElement(targetLocator);

            Actions actions = new Actions(driver);
            actions
                    .moveToElement(source)
                    .clickAndHold(source)
                    .pause(200)
                    .moveToElement(target)
                    .pause(200)
                    .release(target)
                    .build()
                    .perform();

            LogsManager.info("DragDrop success: " + sourceLocator + " -> " + targetLocator);
            return "true";
        } catch (Exception e) {
            LogsManager.error("DragDrop failed: " + sourceLocator + " -> " + targetLocator + " " + e);
            return "false -> DragDrop failed: " + sourceLocator + " -> " + targetLocator + " " + e;
        }
    }




}
