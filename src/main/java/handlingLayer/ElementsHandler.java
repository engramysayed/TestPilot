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
import java.util.List;

public class ElementsHandler {
    /** ARIA / HTML roles a dropdown option can carry, most specific first. */
    private static final List<String> OPTION_SELECTORS = List.of(
            "[role='option']", "[role='menuitemradio']", "[role='menuitemcheckbox']",
            "[role='menuitem']", "[role='treeitem']", "[role='listbox'] li", "li", "option");
    private static final long OPTION_WAIT_MS = 5000;

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
            String result = selectOn(findElement(locator), option);
            if (!result.startsWith("false")) {
                LogsManager.info("Option selected successfully from dropdown: " + locator
                        + " option: " + option);
            }
            return result;
        } catch (Exception e) {
            LogsManager.error("Failed to select option from dropdown: " + locator + " " + e);
            return "false -> Failed to select option from dropdown: " + locator + " " + e;
        }
    }

    /** Drives a native {@code <select>} or an ARIA combobox, whichever the control turns out to be. */
    public String selectOn(WebElement control, String option) {
        if (control == null) {
            return "false -> no control to select from";
        }
        if ("select".equalsIgnoreCase(control.getTagName())) {
            try {
                Select select = new Select(control);
                try {
                    select.selectByVisibleText(option);
                } catch (Exception ignored) {
                    select.selectByValue(option);
                }
                return "true";
            } catch (Exception e) {
                return "false -> Failed to select option: " + e;
            }
        }
        return selectFromCustomDropdown(control, option);
    }

    /**
     * A widget built from divs has no {@code <select>} to drive, so it is operated the way a person
     * does: open the control, then click the option. Options are found by ARIA role and visible
     * text anywhere in the document, because popups are frequently portalled out of the trigger.
     */
    public String selectFromCustomDropdown(WebElement control, String option) {
        String wanted = normalizeText(option);
        if (wanted.isEmpty()) {
            return "false -> no option text to select";
        }
        clickHard(control);
        WebElement match = waitForOption(wanted);
        if (match == null && acceptsTypedText(control)) {
            // Typeahead comboboxes only render their list once the text narrows it.
            try {
                control.sendKeys(option);
                match = waitForOption(wanted);
            } catch (Exception ignored) {
                // fall through to the failure below
            }
        }
        if (match == null) {
            dismissPopup();
            return "false -> option '" + option + "' never appeared after opening the dropdown";
        }
        clickHard(match);
        if (!selectionVisible(control, wanted)) {
            LogsManager.warn("Clicked option '" + option
                    + "' but the control does not show it — the widget may render the value elsewhere");
        }
        return "true";
    }

    private WebElement waitForOption(String wanted) {
        long deadline = System.currentTimeMillis() + OPTION_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            WebElement hit = findOption(wanted);
            if (hit != null) {
                return hit;
            }
            pause(200);
        }
        return null;
    }

    private WebElement findOption(String wanted) {
        WebElement closestPartial = null;
        int closestLength = Integer.MAX_VALUE;
        for (String selector : OPTION_SELECTORS) {
            List<WebElement> found;
            try {
                found = driver.findElements(By.cssSelector(selector));
            } catch (Exception e) {
                continue;
            }
            for (WebElement el : found) {
                String text;
                try {
                    if (!el.isDisplayed()) {
                        continue;
                    }
                    text = normalizeText(el.getText());
                } catch (Exception stale) {
                    continue;
                }
                if (text.isEmpty()) {
                    continue;
                }
                // An ancestor's text concatenates its children, so only a leaf can match exactly.
                if (text.equals(wanted)) {
                    return el;
                }
                if (text.contains(wanted) && text.length() < closestLength) {
                    closestPartial = el;
                    closestLength = text.length();
                }
            }
        }
        return closestPartial;
    }

    private boolean selectionVisible(WebElement control, String wanted) {
        try {
            String shown = normalizeText(control.getText());
            if (shown.contains(wanted)) {
                return true;
            }
            String value = control.getAttribute("value");
            return value != null && normalizeText(value).contains(wanted);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean acceptsTypedText(WebElement control) {
        try {
            String tag = control.getTagName();
            return "input".equalsIgnoreCase(tag) || "textarea".equalsIgnoreCase(tag)
                    || "true".equalsIgnoreCase(control.getAttribute("contenteditable"));
        } catch (Exception e) {
            return false;
        }
    }

    /** An open popup swallows later clicks, so a failed attempt must not leave one behind. */
    private void dismissPopup() {
        try {
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        } catch (Exception ignored) {
            // nothing to dismiss
        }
    }

    private void clickHard(WebElement el) {
        try {
            el.click();
        } catch (Exception e) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
            } catch (Exception js) {
                LogsManager.warn("Could not click element: " + js.getMessage());
            }
        }
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
            forceClear(el);
            el.sendKeys(text);
            LogsManager.info("Text typed into element: " + locator);
            return "true";
        } catch (Exception e) {
            LogsManager.error("Failed to type into element: " + locator + " " + e);
            return "false -> Failed to type into element: " + locator + " " + e;
        }
    }

    /**
     * Frameworks that own the input value (React and friends) restore it after {@code clear()},
     * so the next sendKeys appends and values from separate steps pile up in one box.
     */
    private void forceClear(WebElement el) {
        try {
            String remaining = el.getAttribute("value");
            if (remaining == null || remaining.isEmpty()) {
                return;
            }
            el.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
            remaining = el.getAttribute("value");
            if (remaining != null && !remaining.isEmpty()) {
                LogsManager.warn("Input kept its value after clear — typing may append: " + remaining);
            }
        } catch (Exception e) {
            LogsManager.warn("Could not verify the field was cleared: " + e.getMessage());
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
            WebElement el = findElement(locator);
            el.clear();
            forceClear(el);
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
