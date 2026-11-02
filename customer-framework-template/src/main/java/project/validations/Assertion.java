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

    public void elementEnabled(By locator) {
        boolean ok = false;
        try {
            WebElement el = element.findElement(locator);
            ok = el != null && el.isEnabled();
        } catch (Exception e) {
            LogsManager.error("soft assert enabled failed: " + e.getMessage());
        }
        softTrue(ok, "enabled");
    }

    public void elementDisabled(By locator) {
        boolean ok = false;
        try {
            WebElement el = element.findElement(locator);
            ok = el != null && !el.isEnabled();
        } catch (Exception e) {
            LogsManager.error("soft assert disabled failed: " + e.getMessage());
        }
        softTrue(ok, "disabled");
    }

    /** Assert-from-element: compare the element's value / input value. */
    public void elementValue(By locator, String expected) {
        boolean ok = false;
        String actual = "";
        try {
            WebElement el = element.findElement(locator);
            if (el != null) {
                actual = el.getAttribute("value");
                if (actual == null || actual.isBlank()) {
                    actual = el.getText();
                }
                ok = expected == null ? actual == null : expected.equals(actual);
            }
        } catch (WebDriverException | RuntimeException e) {
            LogsManager.error("soft assert elementValue failed: " + e.getMessage());
        }
        if (!ok) {
            LogsManager.error("soft assert elementValue failed. expected='" + expected + "' actual='" + actual + "'");
        }
        softTrue(ok, "elementValue '" + expected + "'");
    }

    /** Assert-from-element: exact visible text. */
    public void elementTextEquals(By locator, String expected) {
        boolean ok = false;
        String actual = "";
        try {
            WebElement el = element.findElement(locator);
            if (el != null) {
                actual = el.getText() == null ? "" : el.getText();
                ok = expected == null ? actual.isEmpty() : expected.equals(actual);
            }
        } catch (WebDriverException | RuntimeException e) {
            LogsManager.error("soft assert elementTextEquals failed: " + e.getMessage());
        }
        if (!ok) {
            LogsManager.error("soft assert elementTextEquals failed. expected='" + expected
                    + "' actual='" + actual + "'");
        }
        softTrue(ok, "elementTextEquals '" + expected + "'");
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

    /** Trimmed ARIA role assert — expects the element's {@code role} attribute. */
    public void elementAriaRole(By locator, String expectedRole) {
        boolean ok = false;
        String actual = "";
        try {
            wait.waitForElementToBeVisible(locator);
            actual = element.findElement(locator).getAttribute("role");
            ok = actual != null && expectedRole != null
                    && actual.trim().equalsIgnoreCase(expectedRole.trim());
        } catch (WebDriverException e) {
            LogsManager.error("soft assert elementAriaRole failed: " + e.getMessage());
            softTrue(false, "elementAriaRole query failed");
            return;
        }
        if (!ok) {
            LogsManager.error("soft assert elementAriaRole failed. expected='" + expectedRole
                    + "' actual='" + actual + "'");
        }
        softTrue(ok, "elementAriaRole '" + expectedRole + "'");
    }

    /** Trimmed ARIA accessible-name assert — {@code aria-label} (or name attr fallback). */
    public void elementAriaLabel(By locator, String expectedLabel) {
        boolean ok = false;
        String actual = "";
        try {
            wait.waitForElementToBeVisible(locator);
            var el = element.findElement(locator);
            actual = el.getAttribute("aria-label");
            if (actual == null || actual.isBlank()) {
                actual = el.getAttribute("name");
            }
            ok = actual != null && expectedLabel != null
                    && actual.trim().equalsIgnoreCase(expectedLabel.trim());
        } catch (WebDriverException e) {
            LogsManager.error("soft assert elementAriaLabel failed: " + e.getMessage());
            softTrue(false, "elementAriaLabel query failed");
            return;
        }
        if (!ok) {
            LogsManager.error("soft assert elementAriaLabel failed. expected='" + expectedLabel
                    + "' actual='" + actual + "'");
        }
        softTrue(ok, "elementAriaLabel '" + expectedLabel + "'");
    }

    /**
     * Trimmed region / landmark assert — element is displayed and carries the expected
     * landmark role (banner/main/navigation/…) or matching {@code aria-label}.
     */
    public void elementRegion(By locator, String expectedRegion) {
        boolean ok = false;
        String actual = "";
        try {
            wait.waitForElementToBeVisible(locator);
            var el = element.findElement(locator);
            String role = el.getAttribute("role");
            String label = el.getAttribute("aria-label");
            actual = (role == null ? "" : role) + "/" + (label == null ? "" : label);
            String want = expectedRegion == null ? "" : expectedRegion.trim();
            ok = el.isDisplayed() && !want.isEmpty()
                    && ((role != null && role.equalsIgnoreCase(want))
                    || (label != null && label.equalsIgnoreCase(want))
                    || actual.toLowerCase().contains(want.toLowerCase()));
        } catch (WebDriverException e) {
            LogsManager.error("soft assert elementRegion failed: " + e.getMessage());
            softTrue(false, "elementRegion query failed");
            return;
        }
        if (!ok) {
            LogsManager.error("soft assert elementRegion failed. expected='" + expectedRegion
                    + "' actual='" + actual + "'");
        }
        softTrue(ok, "elementRegion '" + expectedRegion + "'");
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
