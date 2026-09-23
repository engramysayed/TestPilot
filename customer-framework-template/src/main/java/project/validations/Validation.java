package project.validations;

import org.openqa.selenium.WebDriver;
import org.testng.asserts.SoftAssert;
import project.utils.Logs.LogsManager;

public class Validation extends Assertion {
    private static final ThreadLocal<SoftAssert> SOFT_ASSERT = ThreadLocal.withInitial(SoftAssert::new);
    private static final ThreadLocal<Boolean> USED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Integer> FAILURES = ThreadLocal.withInitial(() -> 0);

    public Validation(WebDriver driver) {
        super(driver);
    }

    @Override
    protected void assertTrue(boolean condition, String message) {
        SOFT_ASSERT.get().assertTrue(condition, message);
        recordUse(!condition);
    }

    @Override
    protected void assertFalse(boolean condition, String message) {
        SOFT_ASSERT.get().assertFalse(condition, message);
        recordUse(condition);
    }

    @Override
    protected void assertEquals(String actual, String expected, String message) {
        SOFT_ASSERT.get().assertEquals(actual, expected, message);
        recordUse(actual == null ? expected != null : !actual.equals(expected));
    }

    /** Failures recorded on this thread that {@link #assertAll()} has not yet consumed. */
    public static int pendingFailureCount() {
        return FAILURES.get();
    }

    private static void recordUse(boolean failed) {
        USED.set(Boolean.TRUE);
        if (failed) {
            FAILURES.set(FAILURES.get() + 1);
        }
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
        FAILURES.set(0);
        SOFT_ASSERT.set(new SoftAssert());
    }

    /**
     * Drop captured phrases after the browser/test execution ends.
     * {@link #reset()} / {@link #assertAll()} must not clear them: setup capture is compared in {@code @Test}.
     */
    public static void clearCapturedPhrases() {
        project.utils.CapturedValues.clear();
    }
}
