package project.utils.Actions;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import project.utils.Logs.LogsManager;
import project.utils.WaitHandler;

public class FramesHandler {
    private final WebDriver driver;
    private WaitHandler waitHandler;

    public FramesHandler(WebDriver driver) {
        this.driver = driver;
        this.waitHandler = new WaitHandler(driver);
    }

    public void switchToFrameByIndex(int index) {
       try{
           waitHandler.waitFrameByIndex(index);
           driver.switchTo().frame(index);
           LogsManager.info("Frame switched to by index: " + index);
       }catch (Exception e) {
           LogsManager.error("Frame not found at index: " + index);
       }
    }

    public void switchToFrameByNameOrId(String nameOrId) {
        try{
            waitHandler.waitFrameByNameOrId(nameOrId);
            driver.switchTo().frame(nameOrId);
            LogsManager.info("Frame switched to by name or id: " + nameOrId);
        } catch (Exception e) {
            LogsManager.error("Frame not found by name or id: " + nameOrId);
        }
    }

    public void switchToFrameByElement(By locator){
        try{
            waitHandler.waitFrameByElement(locator);
            driver.switchTo().frame(driver.findElement(locator));
            LogsManager.info("Frame switched to by element: " + locator);
        } catch (Exception e) {
            LogsManager.error("Frame not found by element: " + locator);
        }
    }

    public void switchToDefaultContent() {
      try{
          driver.switchTo().defaultContent();
          LogsManager.info("Switched back to default content");
      } catch (Exception e) {
          LogsManager.error("Failed to switch back to default content");
          throw new RuntimeException(e);
      }
    }

}
