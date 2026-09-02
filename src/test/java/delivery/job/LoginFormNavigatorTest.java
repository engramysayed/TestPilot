package delivery.job;

import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LoginFormNavigatorTest {
    @Test
    public void scoresLoginLinkHigherThanUnrelated() {
        WebElement login = element("a", "Sign In", "https://example.com/login");
        WebElement other = element("a", "Checkboxes", "https://example.com/checkboxes");
        Assert.assertTrue(LoginFormNavigator.loginEntryScore(login) > LoginFormNavigator.loginEntryScore(other));
        Assert.assertTrue(LoginFormNavigator.loginEntryScore(login) > 0);
        Assert.assertEquals(LoginFormNavigator.loginEntryScore(other), 1); // tag bonus only
    }

    private static WebElement element(String tag, String text, String href) {
        WebElement el = mock(WebElement.class);
        when(el.getTagName()).thenReturn(tag);
        when(el.getText()).thenReturn(text);
        when(el.getAttribute("href")).thenReturn(href);
        when(el.getAttribute("aria-label")).thenReturn("");
        when(el.getAttribute("id")).thenReturn("");
        when(el.getAttribute("name")).thenReturn("");
        when(el.getAttribute("data-test")).thenReturn("");
        when(el.getAttribute("data-testid")).thenReturn("");
        return el;
    }
}
