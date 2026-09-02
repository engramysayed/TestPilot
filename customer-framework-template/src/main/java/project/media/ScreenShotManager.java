package project.media;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import project.utils.Logs.LogsManager;
import project.utils.TimeManager;
import project.utils.reports.AllureAttachmentManager;

import java.io.File;

import static project.utils.reports.AllureAttachmentManager.attachScreenShot;

public class ScreenShotManager {

    public static final String screenShotPath="test-output/screenshots/";

    public static void takeFullPageScreenshot(WebDriver driver, String screenshotName) {
        try {
            File screenshotSrc = ((TakesScreenshot) driver)
                    .getScreenshotAs(OutputType.FILE);

             screenshotName = (screenshotName).replaceAll("[\\\\/:*?\"<>|]", "_");

            File screenshotFile = new File(screenShotPath
                    + screenshotName + "-" + TimeManager.getTimeStamp() + ".png");

            FileUtils.copyFile(screenshotSrc, screenshotFile);

            // Attach the screenshot to Allure
            attachScreenShot(screenshotName, screenshotFile.getPath());

            LogsManager.info("Capturing Screenshot Succeeded");
        } catch (Exception e) {
            LogsManager.error("Failed to Capture Screenshot " + e.getMessage());
        }
    }

    public static void takeScreenShotForElement(WebDriver driver, String screenshotName, By locator) {
        try {

            File screenShotSrc=driver.findElement(locator).getScreenshotAs(OutputType.FILE);
              screenshotName = (screenshotName).replaceAll("[\\\\/:*?\"<>|]", "_");


             File screenshotFile = new File(screenShotPath
                    + screenshotName + "-" + TimeManager.getTimeStamp() + ".png");
            FileUtils.copyFile(screenShotSrc, screenshotFile);

            // Attach the screenshot to Allure
              attachScreenShot(screenshotName, screenshotFile.getPath());

            LogsManager.info("Capturing Screenshot Succeeded for element "+locator);
        } catch (Exception e) {
            LogsManager.error("Failed to Capture Screenshot for locator "+locator+"  " + e.getMessage());
        }
    }


}
