package project.validations;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import project.utils.Actions.ElementsHandler;
import project.utils.Logs.LogsManager;
import project.utils.WaitHandler;

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
        String actual = element.getText(locator);
        boolean ok = actual != null && actual.contains(expected);
        if (!ok) {
            LogsManager.error("soft assert textContains failed. expected contains='" + expected
                    + "' actual='" + actual + "'");
        }
        softTrue(ok, "textContains '" + expected + "'");
    }

    public void bodyTextContains(String expected) {
        String bodyText = "";
        try {
            bodyText = driver.findElement(By.tagName("body")).getText();
        } catch (Exception e) {
            LogsManager.error("bodyTextContains: failed to read body text: " + e.getMessage());
        }
        boolean ok = bodyText != null && bodyText.contains(expected);
        if (!ok) {
            LogsManager.error("soft assert bodyTextContains failed. expected contains='" + expected
                    + "' body='" + bodyText + "'");
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
            java.util.List<WebElement> els = driver.findElements(locator);
            ok = els == null || els.isEmpty() || !els.get(0).isDisplayed();
        } catch (Exception e) {
            ok = true;
        }
        if (!ok) {
            LogsManager.error("soft assert notVisible failed for " + locator);
        }
        softTrue(ok, "notVisible");
    }
}
