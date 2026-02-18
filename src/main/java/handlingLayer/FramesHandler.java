package handlingLayer;

import utils.LogsManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import utils.WaitHandler;

public class FramesHandler {
    private final WebDriver driver;
    private final WaitHandler waitHandler;

    public FramesHandler(WebDriver driver) {
        this.driver = driver;
        this.waitHandler = new WaitHandler(driver);
    }

    public String switchToFrameByIndex(int index) {
        try{
            waitHandler.waitFrameByIndex(index);
            driver.switchTo().frame(index);
            LogsManager.info("Frame switched to by index: " + index);
            return "true";
        } catch (Exception e) {
            LogsManager.error("Frame not found at index: " + index);
            return "false";
        }
    }

    public String switchToFrameByNameOrId(String nameOrId) {
        try{
            waitHandler.waitFrameByNameOrId(nameOrId);
            driver.switchTo().frame(nameOrId);
            LogsManager.info("Frame switched to by name or id: " + nameOrId);
            return "true";
        } catch (Exception e) {
            LogsManager.error("Frame not found by name or id: " + nameOrId);
            return "false";
        }
    }

    public String switchToFrameByCssSelector(By locator) {
        try{
            waitHandler.waitFrameByCss(locator);
            driver.switchTo().frame(driver.findElement(locator));
            LogsManager.info("Frame switched to by css: " + locator);
            return "true";
        } catch (Exception e) {
            LogsManager.error("Frame not found by css: " + locator);
            return "false";
        }
    }

    public String switchToFrameByElement(By locator){
        try{
            waitHandler.waitFrameByElement(locator);
            driver.switchTo().frame(driver.findElement(locator));
            LogsManager.info("Frame switched to by element: " + locator);
            return "true";
        } catch (Exception e) {
            LogsManager.error("Frame not found by element: " + locator);
            return "false";
        }
    }

    public String switchToDefaultContent() {
        try{
            driver.switchTo().defaultContent();
            LogsManager.info("Switched back to default content");
            return "true";
        } catch (Exception e) {
            LogsManager.error("Failed to switch back to default content");
            return "false";
        }
    }
}
