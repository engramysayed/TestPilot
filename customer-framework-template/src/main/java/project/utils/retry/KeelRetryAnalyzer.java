package project.utils.retry;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import project.utils.Logs.LogsManager;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Retries a failed test immediately (no sleep) only when ALL hold:
 * <ol>
 *   <li>the case is read-only: {@link KeelReadOnly} on the method or class, or TestNG group
 *       {@value KeelReadOnly#GROUP};</li>
 *   <li>the failure is test-category: an {@link AssertionError} or a timeout
 *       (Selenium {@code TimeoutException}, {@code java.util.concurrent.TimeoutException}) anywhere in
 *       the cause chain. Infrastructure errors (session lost, driver crash, setup failures) never
 *       consume retries;</li>
 *   <li>the budget is not spent: -DKEEL_RETRY_MAX (default 1, capped at {@value #CAP}).</li>
 * </ol>
 * A later pass is reported as FLAKY by {@link KeelRetryListener}, never as a clean pass.
 */
public class KeelRetryAnalyzer implements IRetryAnalyzer {
    public static final int CAP = 3;
    public static final String ATTEMPTS_ATTR = "keel.retry.attempts";
    private int retries;

    @Override
    public boolean retry(ITestResult result) {
        if (!isReadOnly(result)) {
            return false;
        }
        if (!isTestCategory(result.getThrowable())) {
            LogsManager.info("keel retry: not retrying " + result.getName() + " (infrastructure error: "
                    + result.getThrowable() + ")");
            return false;
        }
        if (retries >= maxRetries()) {
            return false;
        }
        retries++;
        result.setAttribute(ATTEMPTS_ATTR, retries);
        LogsManager.warn("keel retry: " + result.getName() + " failed (" + result.getThrowable()
                + "), retry " + retries + "/" + maxRetries());
        return true;
    }

    /** Retries consumed so far (0 = first attempt). */
    public int retries() {
        return retries;
    }

    public static int maxRetries() {
        String raw = System.getProperty("KEEL_RETRY_MAX", "1");
        try {
            return Math.max(0, Math.min(CAP, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static boolean isReadOnly(ITestResult result) {
        Method m = result.getMethod().getConstructorOrMethod().getMethod();
        if (m != null && (m.isAnnotationPresent(KeelReadOnly.class)
                || m.getDeclaringClass().isAnnotationPresent(KeelReadOnly.class))) {
            return true;
        }
        return Arrays.asList(result.getMethod().getGroups()).contains(KeelReadOnly.GROUP);
    }

    public static boolean isTestCategory(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof AssertionError
                    || c instanceof org.openqa.selenium.TimeoutException
                    || c instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
