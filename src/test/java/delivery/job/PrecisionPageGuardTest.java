package delivery.job;

import delivery.codegen.ProvenStep;
import org.openqa.selenium.*;
import org.testng.annotations.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class PrecisionPageGuardTest {
    @Test public void typedValuesAndEnabledChangesKeepIdentityButReplacementDoesNot() {
        WebDriver driver = mock(WebDriver.class);
        WebElement document = mock(WebElement.class), input = mock(WebElement.class);
        when(driver.getCurrentUrl()).thenReturn("https://example.com/login");
        when(driver.findElement(any(By.class))).thenReturn(document);
        when(driver.findElements(any(By.class))).thenReturn(List.of(input));
        when(input.getTagName()).thenReturn("input");
        when(input.isDisplayed()).thenReturn(true);
        var step = new ProvenStep("TC", "Login", "elementAction", "type", "id", "otp", "", "", "", true, "");
        var guard = new PrecisionPageGuard(driver, List.of(List.of(step)));
        when(input.getDomAttribute("value")).thenReturn("changed");
        when(input.getDomAttribute("class")).thenReturn("valid focused");
        when(input.isEnabled()).thenReturn(true);
        assertTrue(guard.valid(List.of(step)));
        when(driver.findElements(any(By.class))).thenReturn(List.of(mock(WebElement.class)));
        assertFalse(guard.valid(List.of(step)));
    }

    @Test public void sameUrlDocumentReplacementAndAmbiguityStopBatch() {
        WebDriver driver = mock(WebDriver.class);
        WebElement document = mock(WebElement.class), button = mock(WebElement.class);
        when(driver.getCurrentUrl()).thenReturn("https://example.com/login");
        when(driver.findElement(any(By.class))).thenReturn(document);
        when(driver.findElements(any(By.class))).thenReturn(List.of(button));
        when(button.getTagName()).thenReturn("button");
        when(button.getText()).thenReturn("Verify");
        when(button.isDisplayed()).thenReturn(true);
        var step = new ProvenStep("TC", "Login", "elementAction", "click", "id", "verify", "", "", "", true, "");
        var guard = new PrecisionPageGuard(driver, List.of(List.of(step)));
        assertTrue(guard.valid(List.of(step)));
        when(driver.findElements(any(By.class))).thenReturn(List.of(button, button));
        assertFalse(guard.valid(List.of(step)));
        when(driver.findElements(any(By.class))).thenReturn(List.of(button));
        when(driver.findElement(any(By.class))).thenReturn(mock(WebElement.class));
        assertFalse(guard.valid(List.of(step)));
    }
}
