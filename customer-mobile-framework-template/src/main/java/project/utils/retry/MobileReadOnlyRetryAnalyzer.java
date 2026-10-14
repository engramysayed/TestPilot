package project.utils.retry;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.lang.reflect.Method;

/**
 * Retry at most once — only assertion failures / timeouts of read-only cases.
 * Retry pass → report FLAKY (Allure label + attribute), never plain PASS.
 * Mutating cases, APP_CRASHED, ACTION_NO_EFFECT, setup failures: never retry.
 */
public class MobileReadOnlyRetryAnalyzer implements IRetryAnalyzer {

    public static final String FLAKY_ATTRIBUTE = "keel.mobile.flaky";
    private int attempts;

    @Override
    public boolean retry(ITestResult result) {
        if (attempts >= 1) {
            return false;
        }
        if (result == null || result.getStatus() != ITestResult.FAILURE) {
            return false;
        }
        Method method = null;
        try {
            if (result.getMethod() != null && result.getMethod().getConstructorOrMethod() != null) {
                method = result.getMethod().getConstructorOrMethod().getMethod();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        if (!shouldRetry(method, result.getThrowable())) {
            return false;
        }
        attempts++;
        result.setAttribute(FLAKY_ATTRIBUTE, Boolean.TRUE);
        try {
            io.qameta.allure.Allure.label("flaky", "true");
        } catch (Throwable ignored) {
            // Allure optional
        }
        return true;
    }

    /** Package-visible for unit tests — no live TestNG result required. */
    static boolean shouldRetry(Method method, Throwable failure) {
        if (!isReadOnly(method)) {
            return false;
        }
        return isAssertionOrTimeout(failure);
    }

    static boolean isReadOnly(Method method) {
        if (method == null) {
            return false;
        }
        if (method.isAnnotationPresent(KeelReadOnly.class)) {
            return true;
        }
        return method.getDeclaringClass().isAnnotationPresent(KeelReadOnly.class);
    }

    static boolean isAssertionOrTimeout(Throwable t) {
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null) {
                String u = msg.toUpperCase();
                if (u.contains("APP_CRASHED") || u.contains("ACTION_NO_EFFECT")) {
                    return false;
                }
            }
            if (t instanceof AssertionError) {
                return true;
            }
            String name = t.getClass().getName();
            if (name.contains("Timeout") || name.contains("Assertion")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
