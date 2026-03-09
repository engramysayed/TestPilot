package executionLayer;

import drivers.WebDriverFactory;
import org.openqa.selenium.By;
import utils.LogsManager;
import utils.RuntimeSettings;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static buildersLayer.stateBuilders.StateVars.setLastScreenshotRef;
import static java.lang.Integer.parseInt;

public class actionExecute {
    private final WebDriverFactory driver;

    public actionExecute(WebDriverFactory driver) {
        this.driver = driver;
    }

    public String checkAction(String option, String type,
                              String url, String tab, By locator, String value)
    {
        if ("elementAction".equals(option) && locator == null) {
            return "false -> selector required for element action";
        }
        if ("frameAction".equals(option) && ("switchFrameByName".equals(type) || "switchFrameByCssSelector".equals(type)) && locator == null) {
            return "false -> selector required for frame switch";
        }
        return switch (option) {
            case "browserAction" -> browserAction(type, url, tab);

            case "elementAction" -> elementAction(type, locator, value);

            case "frameAction" -> frameAction(type, value, locator);

            default -> null;
        };
    }



    public String elementAction(String type, By locator, String value) {
        return switch (type) {
            case "click" -> driver.element().click(locator);
            case "type" -> driver.element().type(locator, value);
            case "clear" -> driver.element().clear(locator);
            case "select" -> driver.element().selectFromDD(locator, value);
            case "getText" -> driver.element().getText(locator);
            case "getAttr" -> driver.element().getAttributeValue(locator, value);
            case "scroll" -> driver.element().scrollToElement(locator);
            case "upload" -> driver.element().uploadFile(locator, value);
            case "dragDrop" -> driver.element().dragDrop(locator, SelectorParser.toBy(value));
            default -> null;
        };
    }

    public String browserAction(String type, String url, String tab) {
        return switch (type) {
            case "navigate" -> driver.browser().navigateToUrl(url);
            case "refresh" -> driver.browser().refreshPage();
            case "back" -> driver.browser().navigateBack();
            case "maximize" -> driver.browser().maximizeWindow();
            case "getUrl" -> driver.browser().getCurrentUrl();
            case "close" -> driver.browser().closeWindow();
            case "openNewWindow" -> driver.browser().newWindow();
            case "getCustomTab" -> driver.browser().customTab(tab);
            default -> null;
        };
    }

    public String frameAction(String frameType, String frameValue, By locator){
        return switch (frameType) {
            case "switchFrameById" ->
                    driver.frames().switchToFrameByNameOrId(frameValue);
            case "switchFrameByIndex" ->
                    driver.frames().switchToFrameByIndex(parseInt(frameValue));
            case "switchFrameByName" ->
                     driver.frames().switchToFrameByElement(locator);
            case "switchFrameByCssSelector" ->
                     driver.frames().switchToFrameByCssSelector(locator);
            case "switchToParent" ->
                    driver.frames().switchToDefaultContent();
            case "switchToDefaultContent" ->
                    driver.frames().switchToDefaultContent();
            default -> null;
        };
    }




    public void takeScreenshot(Path runFolder, int stepId){
        driver.element().capture(runFolder, stepId);
    }

    public void takeScreenshot(boolean screenshot, Path runFolder, String nameWithoutExtension){
            try {
                if (screenshot) {
                    int screenshotWait=RuntimeSettings.getScreenshotWait();
                    if (screenshotWait > 0) {
                        TimeUnit.SECONDS.sleep(screenshotWait);
                    }
                    driver.element().capture(runFolder, nameWithoutExtension);
                    setLastScreenshotRef(nameWithoutExtension + ".png");
                    LogsManager.info("Screenshot taken: " + nameWithoutExtension + ".png");
                }
            } catch (Exception e) {
                LogsManager.error("Error taking screenshot " + e);
            }
    }

    public String getUrl(){return driver.browser().getCurrentUrl();}

    public String getHtml(){return driver.browser().getPageSource();}
}
