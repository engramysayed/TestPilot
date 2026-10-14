package delivery.job;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class PostActionSettleTest {
    @Test public void typingAndAssertionsDoNotPayFixedWait() {
        System.setProperty(PostActionSettle.PROPERTY, "5000");
        var driver = org.mockito.Mockito.mock(org.openqa.selenium.WebDriver.class);
        long start = System.nanoTime();
        for (String action : java.util.List.of("type", "assert")) {
            PostActionSettle.afterSteps(driver, java.util.List.of(new delivery.codegen.ProvenStep(
                    "TC", "Page", "elementAction", action, "id", "otp", "", "", "", true, "")));
        }
        Assert.assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);
        org.mockito.Mockito.verifyNoInteractions(driver);
    }

    private String previousWait;
    private String previousAdaptive;
    @org.testng.annotations.BeforeMethod public void saveSettings() {
        previousWait = System.getProperty(PostActionSettle.PROPERTY);
        previousAdaptive = System.getProperty(PostActionSettle.ADAPTIVE_PROPERTY);
    }
    @AfterMethod(alwaysRun = true) public void clearOverride() {
        restore(PostActionSettle.PROPERTY, previousWait);
        restore(PostActionSettle.ADAPTIVE_PROPERTY, previousAdaptive);
    }
    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }
    @Test public void adaptiveReadyPageReturnsBeforeFixedBudgetEvenWithoutNavigation() {
        var driver = org.mockito.Mockito.mock(org.openqa.selenium.WebDriver.class,
                org.mockito.Mockito.withSettings().extraInterfaces(org.openqa.selenium.JavascriptExecutor.class));
        org.mockito.Mockito.when(((org.openqa.selenium.JavascriptExecutor) driver).executeScript(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        System.setProperty(PostActionSettle.PROPERTY, "5000");
        System.setProperty(PostActionSettle.ADAPTIVE_PROPERTY, "true");
        long start = System.nanoTime();
        PostActionSettle.afterNavigation(driver, "https://example.test/same", "Click Save");
        long elapsed = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        Assert.assertTrue(elapsed >= 750 && elapsed < 4000, "elapsed=" + elapsed);
    }
    @Test public void adaptiveDeadSessionDoesNotBecomeReady() {
        var driver = org.mockito.Mockito.mock(org.openqa.selenium.WebDriver.class,
                org.mockito.Mockito.withSettings().extraInterfaces(org.openqa.selenium.JavascriptExecutor.class));
        org.mockito.Mockito.when(((org.openqa.selenium.JavascriptExecutor) driver).executeScript(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new org.openqa.selenium.NoSuchSessionException("dead"));
        System.setProperty(PostActionSettle.PROPERTY, "5000");
        System.setProperty(PostActionSettle.ADAPTIVE_PROPERTY, "true");
        Assert.expectThrows(org.openqa.selenium.NoSuchSessionException.class,
                () -> PostActionSettle.afterNavigation(driver, null, "type"));
    }
    @Test public void adaptiveBusyPageHasABoundedDeadline() {
        var driver = org.mockito.Mockito.mock(org.openqa.selenium.WebDriver.class,
                org.mockito.Mockito.withSettings().extraInterfaces(org.openqa.selenium.JavascriptExecutor.class));
        org.mockito.Mockito.when(((org.openqa.selenium.JavascriptExecutor) driver).executeScript(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(false);
        System.setProperty(PostActionSettle.PROPERTY, "250");
        System.setProperty(PostActionSettle.ADAPTIVE_PROPERTY, "true");
        long start = System.nanoTime();
        PostActionSettle.afterNavigation(driver, null, "type");
        long elapsed = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        Assert.assertTrue(elapsed >= 250 && elapsed < 3000, "elapsed=" + elapsed);
    }

    @Test
    public void defaultWaitIsFiveSeconds() {
        System.clearProperty(PostActionSettle.PROPERTY);
        Assert.assertEquals(PostActionSettle.waitMs(), 5000L);
    }

    @Test
    public void propertyOverrideIsHonored() {
        System.setProperty(PostActionSettle.PROPERTY, "1000");
        Assert.assertEquals(PostActionSettle.waitMs(), 1000L);
    }

    @Test
    public void zeroDisablesSleep() {
        System.setProperty(PostActionSettle.PROPERTY, "0");
        Assert.assertEquals(PostActionSettle.waitMs(), 0L);
    }

    @Test
    public void negativeClampedToZero() {
        System.setProperty(PostActionSettle.PROPERTY, "-5");
        Assert.assertEquals(PostActionSettle.waitMs(), 0L);
    }
}
