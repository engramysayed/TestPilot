package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class LocatorValidatorTest {
    private final LocatorValidator validator = new LocatorValidator();

    @Test
    public void rejectsAbsoluteHtmlXpath() {
        var result = validator.validate(new LocatorCandidate("xpath", "/html/body/div", "Page", ""));
        Assert.assertFalse(result.valid());
    }

    @Test
    public void rejectsUuidLikeId() {
        var result = validator.validate(new LocatorCandidate(
                "id", "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "Page", ""));
        Assert.assertFalse(result.valid());
    }

    @Test
    public void acceptsAttrCss() {
        var result = validator.validate(new LocatorCandidate(
                "css", "button[data-test='login-button']", "Login", "stable"));
        Assert.assertTrue(result.valid(), result.reason());
    }

    @Test
    public void rejectsCssPseudo() {
        var result = validator.validate(new LocatorCandidate("css", "div:nth-child(2)", "Page", ""));
        Assert.assertFalse(result.valid());
    }

    @Test
    public void acceptsAttrXpath() {
        var result = validator.validate(new LocatorCandidate(
                "xpath", "//input[@id='user-name']", "Login", "stable"));
        Assert.assertTrue(result.valid(), result.reason());
    }

    @Test
    public void rejectsIndexXpath() {
        var result = validator.validate(new LocatorCandidate("xpath", "(//div)[3]", "Page", ""));
        Assert.assertFalse(result.valid());
    }

    @Test
    public void acceptsBodyScopedTextContainsXpath() {
        String xpath = StepIntentBinder.xpathContainsText("Logged In Successfully");
        var result = validator.validate(new LocatorCandidate("xpath", xpath, "Page", ""));
        Assert.assertTrue(result.valid(), result.reason());
    }

    @Test
    public void acceptsContainsLabelDescendantXpath() {
        var result = validator.validate(new LocatorCandidate(
                "xpath",
                "//label[contains(normalize-space(.),'First name')]//input",
                "Page",
                ""));
        Assert.assertTrue(result.valid(), result.reason());
    }

    @Test
    public void acceptsLabelForIdXpath() {
        var result = validator.validate(new LocatorCandidate(
                "xpath",
                "//input[@id=//label[normalize-space(.)='Email address']/@for]",
                "Page",
                ""));
        Assert.assertTrue(result.valid(), result.reason());
    }

    @Test
    public void acceptsFollowingSiblingLabelAnchor() {
        var result = validator.validate(new LocatorCandidate(
                "xpath",
                "//label[normalize-space(.)='Surname']/following-sibling::input[1]",
                "Page",
                ""));
        Assert.assertTrue(result.valid(), result.reason());
    }
}
