package project.utils.Actions;

import org.openqa.selenium.*;
import project.utils.Logs.LogsManager;
import project.utils.WaitHandler;

import java.io.File;

public class ElementsHandler {
    /** ARIA / HTML roles a dropdown option can carry, most specific first. */
    private static final java.util.List<String> OPTION_SELECTORS = java.util.List.of(
            "[role='option']", "[role='menuitemradio']", "[role='menuitemcheckbox']",
            "[role='menuitem']", "[role='treeitem']", "[role='listbox'] li", "li", "option");
    private static final long OPTION_WAIT_MS = 5000;

    private final WebDriver driver;
    private WaitHandler waitHandler;

    public ElementsHandler(WebDriver driver) {
        this.driver = driver;
        this.waitHandler = new WaitHandler(driver);
    }


    public void scrollToElement(By locator) {
        try {
            ((JavascriptExecutor) driver)
                    .executeScript("""
                            arguments[0].scrollIntoView({behavior: "auto", block: "center",inline: "center"});""", findElement(locator));

        }  catch (Exception e) {
            LogsManager.error("Failed to scroll to element: " + locator);
        }
    }

    public WebElement findElement(By locator) {
        return driver.findElement(locator);
    }



    public void uploadFile(By locator,String path){

      try {
          String filePath = System.getProperty("user.dir") + File.separator + path;
          waitHandler.waitForElementToBeVisible(locator);
          findElement(locator).sendKeys(filePath);
          LogsManager.info("File uploaded successfully: " + path);
      }catch (Exception e) {
          LogsManager.error("Failed to upload file: " + path);
          throw new RuntimeException(e);
      }
    }

    public void click(By locator) {
        try {
            scrollToElement(locator);
            waitHandler.waitForElementToBeVisible(locator);
            findElement(locator).click();

        } catch (Exception e) {
            LogsManager.error("Failed to click  element: " + locator);
        }
    }


    public void type(By locator, String text) {
        try {
            waitHandler.waitForElementToBeVisible(locator);
            findElement(locator).clear();
            findElement(locator).sendKeys(text);
            LogsManager.info("Text typed successfully into element: " + locator + " with text: " + text);
        } catch (Exception e) {
            LogsManager.error("Failed to type into element: " + locator);
        }
    }

    public void selectFromDD(By locator, String option) {
        try {
            scrollToElement(locator);
            waitHandler.waitForElementToBeVisible(locator);
            WebElement control = findElement(locator);
            if ("select".equalsIgnoreCase(control.getTagName())) {
                org.openqa.selenium.support.ui.Select select =
                        new org.openqa.selenium.support.ui.Select(control);
                try {
                    select.selectByVisibleText(option);
                } catch (Exception ignored) {
                    select.selectByValue(option);
                }
            } else {
                selectFromCustomDropdown(control, option);
            }
            LogsManager.info("Option selected from dropdown: " + locator + " option: " + option);
        } catch (Exception e) {
            LogsManager.error("Failed to select option from dropdown: " + locator);
            throw new RuntimeException(e);
        }
    }

    /**
     * A dropdown built from divs has no {@code <select>} to drive, so it is operated the way a
     * person does: open the control, then click the option. Options are matched by ARIA role and
     * visible text anywhere in the document, because popups are often rendered outside the trigger.
     */
    private void selectFromCustomDropdown(WebElement control, String option) {
        String wanted = normalizeText(option);
        clickHard(control);
        WebElement match = waitForOption(wanted);
        if (match == null) {
            try {
                driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
            } catch (Exception ignored) {
                // nothing to dismiss
            }
            throw new NoSuchElementException(
                    "Option '" + option + "' never appeared after opening the dropdown");
        }
        clickHard(match);
    }

    private WebElement waitForOption(String wanted) {
        long deadline = System.currentTimeMillis() + OPTION_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            WebElement closestPartial = null;
            int closestLength = Integer.MAX_VALUE;
            for (String selector : OPTION_SELECTORS) {
                for (WebElement el : driver.findElements(By.cssSelector(selector))) {
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
            if (closestPartial != null) {
                return closestPartial;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private void clickHard(WebElement el) {
        try {
            el.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }


    public String getText(By locator) {
        try {
            waitHandler.waitForElementToBeVisible(locator);
            LogsManager.info("Text retrieved from element: " + locator);
             return !(findElement(locator).getText()).isEmpty() ? findElement(locator).getText() : null;
        } catch (Exception e) {
            LogsManager.error("Failed to get text from: " + locator);
             return null;
        }
    }
}
