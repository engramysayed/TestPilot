package project.utils.locators;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * MobileFirstOf walks slots in order, returns the first non-empty match, and records a WIN/MISS
 * line per slot via {@link MobileLocatorOutcomes}. No actual Appium or device required.
 */
public class MobileFirstOfTest {

    @Test
    public void singlePrimaryReturnsThePrimaryUnwrapped() {
        By primary = AppiumBy.accessibilityId("login_submit");
        By result = MobileFirstOf.of("LoginScreen", "signInButton", primary);
        // No fallbacks: unwraps so the simple-locator path stays zero-overhead.
        Assert.assertSame(result, primary);
    }

    @Test
    public void firstMatchingSlotWins() {
        By bad = new NeverFound();
        By good = new AlwaysFound("btn-good");
        By result = MobileFirstOf.of("LoginScreen", "signInButton", bad, good);
        WebElement el = result.findElement(new EmptyContext());
        Assert.assertNotNull(el);
        Assert.assertEquals(((AlwaysFound.El) el).marker, "btn-good");
    }

    @Test(expectedExceptions = NoSuchElementException.class)
    public void allSlotsMissThrows() {
        By a = new NeverFound();
        By b = new NeverFound();
        By result = MobileFirstOf.of("LoginScreen", "ghost", a, b);
        result.findElement(new EmptyContext());
    }

    @Test
    public void findElementsReturnsFirstNonEmptyList() {
        By empty = new EmptyList();
        By two = new TwoMatches();
        By result = MobileFirstOf.of("Cart", "rows", empty, two);
        List<WebElement> all = result.findElements(new EmptyContext());
        Assert.assertEquals(all.size(), 2);
    }

    private static final class EmptyContext implements SearchContext {
        public WebElement findElement(By by) { throw new NoSuchElementException("empty"); }
        public List<WebElement> findElements(By by) { return List.of(); }
    }

    private static final class NeverFound extends By {
        @Override public WebElement findElement(SearchContext context) { throw new NoSuchElementException("miss"); }
        @Override public List<WebElement> findElements(SearchContext context) { return List.of(); }
    }

    private static final class AlwaysFound extends By {
        private final String marker;
        AlwaysFound(String marker) { this.marker = marker; }
        @Override public WebElement findElement(SearchContext context) { return new El(marker); }
        @Override public List<WebElement> findElements(SearchContext context) { return List.of(new El(marker)); }
        static final class El implements WebElement {
            final String marker;
            El(String marker) { this.marker = marker; }
            public void click() {}
            public void submit() {}
            public void sendKeys(CharSequence... keysToSend) {}
            public void clear() {}
            public String getTagName() { return "button"; }
            public String getAttribute(String name) { return null; }
            public String getDomAttribute(String name) { return null; }
            public String getDomProperty(String name) { return null; }
            public org.openqa.selenium.SearchContext getShadowRoot() { return null; }
            public String getAriaRole() { return null; }
            public String getAccessibleName() { return null; }
            public boolean isSelected() { return false; }
            public boolean isEnabled() { return true; }
            public String getText() { return ""; }
            public List<WebElement> findElements(By by) { return List.of(); }
            public WebElement findElement(By by) { throw new NoSuchElementException(""); }
            public boolean isDisplayed() { return true; }
            public org.openqa.selenium.Point getLocation() { return new org.openqa.selenium.Point(0, 0); }
            public org.openqa.selenium.Dimension getSize() { return new org.openqa.selenium.Dimension(0, 0); }
            public org.openqa.selenium.Rectangle getRect() { return new org.openqa.selenium.Rectangle(0, 0, 0, 0); }
            public String getCssValue(String propertyName) { return ""; }
            public <X> X getScreenshotAs(org.openqa.selenium.OutputType<X> target) { return null; }
        }
    }

    private static final class EmptyList extends By {
        @Override public WebElement findElement(SearchContext context) { throw new NoSuchElementException("empty"); }
        @Override public List<WebElement> findElements(SearchContext context) { return List.of(); }
    }

    private static final class TwoMatches extends By {
        @Override public WebElement findElement(SearchContext context) { return new AlwaysFound.El("r1"); }
        @Override public List<WebElement> findElements(SearchContext context) { return List.of(new AlwaysFound.El("r1"), new AlwaysFound.El("r2")); }
    }
}
