package project.utils.retry;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

public class MobileReadOnlyRetryAnalyzerTest {

    @KeelReadOnly
    public void readOnlyCarrier() {}

    public void mutatingCarrier() {}

    @Test
    public void retriesReadOnlyAssertion() throws Exception {
        Method m = getClass().getMethod("readOnlyCarrier");
        Assert.assertTrue(MobileReadOnlyRetryAnalyzer.shouldRetry(m, new AssertionError("x")));
    }

    @Test
    public void neverRetriesMutating() throws Exception {
        Method m = getClass().getMethod("mutatingCarrier");
        Assert.assertFalse(MobileReadOnlyRetryAnalyzer.shouldRetry(m, new AssertionError("x")));
    }

    @Test
    public void neverRetriesAppCrashed() throws Exception {
        Method m = getClass().getMethod("readOnlyCarrier");
        Assert.assertFalse(MobileReadOnlyRetryAnalyzer.shouldRetry(m, new IllegalStateException("APP_CRASHED")));
    }

    @Test
    public void neverRetriesActionNoEffect() throws Exception {
        Method m = getClass().getMethod("readOnlyCarrier");
        Assert.assertFalse(MobileReadOnlyRetryAnalyzer.shouldRetry(
                m, new IllegalStateException("ACTION_NO_EFFECT")));
    }

    @Test
    public void retriesTimeoutExceptionName() throws Exception {
        Method m = getClass().getMethod("readOnlyCarrier");
        Assert.assertTrue(MobileReadOnlyRetryAnalyzer.shouldRetry(
                m, new org.openqa.selenium.TimeoutException("wait")));
    }
}
