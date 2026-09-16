package project.validations;

import org.openqa.selenium.WebDriver;
import org.testng.asserts.SoftAssert;
import project.utils.Logs.LogsManager;

public class Validation extends Assertion {
    private static final ThreadLocal<SoftAssert> SOFT_ASSERT = ThreadLocal.withInitial(SoftAssert::new);
    private static final ThreadLocal<Boolean> USED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public Validation(WebDriver driver) {
        super(driver);
    }

    @Override
    protected void assertTrue(boolean condition, String message) {
        SOFT_ASSERT.get().assertTrue(condition, message);
        USED.set(Boolean.TRUE);
    }

    @Override
    protected void assertFalse(boolean condition, String message) {
        SOFT_ASSERT.get().assertFalse(condition, message);
        USED.set(Boolean.TRUE);
    }

    @Override
    protected void assertEquals(String actual, String expected, String message) {
        SOFT_ASSERT.get().assertEquals(actual, expected, message);
        USED.set(Boolean.TRUE);
    }

    public static void assertAll() {
        boolean used = Boolean.TRUE.equals(USED.get());
        SoftAssert current = SOFT_ASSERT.get();
        reset();
        if (!used) {
            return;
        }
        try {
            current.assertAll();
        } catch (AssertionError e) {
            LogsManager.error("Assertion Failed " + e.getMessage());
            throw e;
        }
    }

    /** Drop this thread's recorded assertions so the next test starts clean. */
    public static void reset() {
        USED.set(Boolean.FALSE);
        SOFT_ASSERT.set(new SoftAssert());
    }
}
