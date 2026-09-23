package project.validations;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import project.utils.Actions.ElementsHandler;
import project.utils.CaptureCompare;
import project.utils.CapturedValues;
import project.utils.Logs.LogsManager;
import project.utils.WaitHandler;

import java.util.ArrayList;
import java.util.List;

public abstract class  Assertion {
    protected final WebDriver driver;
    protected final WaitHandler wait;
    protected ElementsHandler element;

    protected Assertion(WebDriver driver){
        this.driver=driver;
        this.wait=new WaitHandler(driver);
        this.element=new ElementsHandler(driver);
    }


    protected abstract void assertTrue(boolean Condition,String message);
    protected abstract void assertFalse(boolean Condition,String message);
    protected abstract void assertEquals(String actual,String expected,String message);


    public void Equals(String actual,String expected,String message){
        assertEquals( actual, expected, message);
    }

    public void elementVisable(By locator){

        try {
            wait.waitForElementToBeVisible(locator);
        } catch (Exception e) {
            LogsManager.error("Couldn't find element "+locator);
        }
        assertTrue(element.findElement(locator).isDisplayed(), "Element not visable");

    }

    public void verifyUrl(String expected){
        String currentUrl=driver.getCurrentUrl();
        assertEquals(currentUrl,expected,"Url Doesn't match "+expected +"  "+ currentUrl);

    }

    public void verifyTitle(String expected){
        String currentUrl=driver.getTitle();
        assertEquals(currentUrl,expected,"Title Doesn't match "+expected +"  "+ currentUrl);

    }

    /** Soft assert helper used by delivery-generated page assertion methods. */
    public void softTrue(boolean condition, String message) {
        if (!condition) {
            LogsManager.error("Soft assertion failed: " + message);
        }
        assertTrue(condition, message);
    }

    public void textContains(By locator, String expected) {
        boolean ok = false;
        try {
            ok = wait.waitUntilTextContains(locator, expected);
        } catch (WebDriverException e) {
            LogsManager.error("textContains query failed: " + e.getMessage());
            softTrue(false, "textContains query failed: " + e.getClass().getSimpleName());
            return;
        }
        if (!ok) {
            LogsManager.error("soft assert textContains failed. expected contains='" + expected + "'");
        }
        softTrue(ok, "textContains '" + expected + "'");
    }

    public void bodyTextContains(String expected) {
        boolean ok = false;
        try {
            ok = wait.waitUntilBodyTextContains(expected);
        } catch (WebDriverException e) {
            LogsManager.error("bodyTextContains query failed: " + e.getMessage());
            softTrue(false, "bodyTextContains query failed: " + e.getClass().getSimpleName());
            return;
        }
        if (!ok) {
            LogsManager.error("soft assert bodyTextContains failed. expected contains='" + expected + "'");
        }
        softTrue(ok, "textContains '" + expected + "'");
    }

    public void urlContains(String expected) {
        String url = driver.getCurrentUrl();
        boolean ok = url != null && url.contains(expected);
        if (!ok) {
            LogsManager.error("soft assert urlContains failed. expected contains='" + expected
                    + "' actual='" + url + "'");
        }
        softTrue(ok, "urlContains '" + expected + "'");
    }

    public void elementSelected(By locator, String expectedOptionOrEmpty) {
        boolean ok = false;
        try {
            WebElement el = element.findElement(locator);
            if (el != null) {
                String tag = el.getTagName() == null ? "" : el.getTagName().toLowerCase();
                String role = el.getAttribute("role");
                String hasPopup = el.getAttribute("aria-haspopup");
                boolean customPicker = ("combobox".equalsIgnoreCase(role) || "listbox".equalsIgnoreCase(role)
                        || "listbox".equalsIgnoreCase(hasPopup) || "menu".equalsIgnoreCase(hasPopup)
                        || "true".equalsIgnoreCase(hasPopup))
                        && !"select".equals(tag) && !"option".equals(tag);
                if (expectedOptionOrEmpty != null && !expectedOptionOrEmpty.isEmpty()) {
                    if (customPicker) {
                        String shown = el.getText();
                        String valueText = el.getAttribute("aria-valuetext");
                        ok = (shown != null && shown.contains(expectedOptionOrEmpty))
                                || (valueText != null && valueText.contains(expectedOptionOrEmpty));
                    } else if ("select".equals(tag)) {
                        org.openqa.selenium.support.ui.Select sel =
                                new org.openqa.selenium.support.ui.Select(el);
                        String selectedText = sel.getFirstSelectedOption().getText();
                        ok = selectedText != null && selectedText.contains(expectedOptionOrEmpty);
                    } else {
                        ok = el.isSelected() && el.getText() != null
                                && el.getText().contains(expectedOptionOrEmpty);
                    }
                } else {
                    ok = el.isSelected();
                }
            }
        } catch (Exception e) {
            LogsManager.error("soft assert selected failed: " + e.getMessage());
        }
        softTrue(ok, "selected '" + expectedOptionOrEmpty + "'");
    }

    public void elementUnchecked(By locator) {
        boolean ok = false;
        try {
            WebElement el = element.findElement(locator);
            ok = el != null && !el.isSelected();
        } catch (Exception e) {
            LogsManager.error("soft assert unchecked failed: " + e.getMessage());
        }
        softTrue(ok, "unchecked");
    }

    public void elementNotVisible(By locator) {
        boolean ok = false;
        try {
            ok = wait.waitUntilElementNotVisible(locator);
        } catch (WebDriverException e) {
            LogsManager.error("notVisible query failed: " + e.getMessage());
            softTrue(false, "notVisible query failed: " + e.getClass().getSimpleName());
            return;
        }
        if (!ok) {
            LogsManager.error("soft assert notVisible failed for " + locator);
        }
        softTrue(ok, "notVisible");
    }

    /**
     * Capture exact text from the supplied locator using the IR extraction rule.
     * Multiple matches or an empty locator result fail clearly.
     */
    public void captureFrom(By locator, String slot, String rule) {
        try {
            CaptureCompare.ExtractResult extracted = CaptureCompare.extract(elementTexts(locator), rule);
            if (extracted.ok()) {
                CapturedValues.put(slot, extracted.value());
                LogsManager.info("captured phrase slot=" + slot + " value=" + extracted.value());
            } else {
                LogsManager.error(extracted.error());
            }
            softTrue(extracted.ok(), extracted.error());
        } catch (WebDriverException e) {
            LogsManager.error("captureFrom query failed: " + e.getMessage());
            softTrue(false, "unavailable browser state: " + e.getClass().getSimpleName());
        }
    }

    public void compareCapturedExact(By locator, String slot) {
        String captured = CapturedValues.get(slot);
        try {
            CaptureCompare.CompareResult compared = CaptureCompare.compareExact(elementTexts(locator), captured);
            if (compared.ok()) {
                LogsManager.info("compared captured slot=" + slot + " value=" + captured);
            } else {
                LogsManager.error(compared.error());
            }
            softTrue(compared.ok(), compared.error());
        } catch (WebDriverException e) {
            LogsManager.error("compareCapturedExact query failed: " + e.getMessage());
            softTrue(false, "unavailable browser state: " + e.getClass().getSimpleName());
        }
    }

    public void signedOut(By locator, String expected) {
        try {
            List<WebElement> els = driver.findElements(locator);
            boolean anyDisplayed = false;
            List<String> displayed = new ArrayList<>();
            for (WebElement el : els) {
                if (el.isDisplayed()) {
                    anyDisplayed = true;
                    displayed.add(el.getText());
                }
            }
            CaptureCompare.CompareResult result = CaptureCompare.signedOut(displayed, anyDisplayed, expected);
            if (result.ok()) {
                LogsManager.info("signed-out locator=" + locator + " expected=" + expected);
            } else {
                LogsManager.error(result.error());
            }
            softTrue(result.ok(), result.error());
        } catch (WebDriverException e) {
            LogsManager.error("signedOut query failed: " + e.getMessage());
            softTrue(false, "unavailable browser state: " + e.getClass().getSimpleName());
        }
    }

    private List<String> elementTexts(By locator) {
        List<String> texts = new ArrayList<>();
        for (WebElement el : driver.findElements(locator)) {
            texts.add(el.getText());
        }
        return texts;
    }
}
