package project.utils.retry;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.StatusDetails;
import org.testng.IExecutionListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.IRetryAnalyzer;
import org.testng.ITestListener;
import org.testng.ITestResult;
import project.utils.Logs.LogsManager;
import project.utils.reports.KeelResults;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns "passed after a retry" into an explicit FLAKY verdict:
 * Allure statusDetails.flaky=true plus label {@code keel_verdict=FLAKY} (set in afterInvocation,
 * before allure-testng writes the result), a WARN log entry, and a verdicts.jsonl line. Final
 * PASSED/FAILED verdicts are recorded too; retried attempts are logged as RETRIED.
 * Registered via META-INF/services and BaseTest {@code @Listeners}; callbacks are idempotent per
 * ITestResult so a double registration never duplicates labels or verdict lines.
 */
public class KeelRetryListener implements IInvokedMethodListener, ITestListener, IExecutionListener {
    public static final String VERDICT_ATTR = "keel.verdict";
    public static final String LABEL = "keel_verdict";

    private static final java.util.concurrent.atomic.AtomicBoolean RESET = new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    public void onExecutionStart() {
        if (RESET.compareAndSet(false, true)) {
            KeelResults.reset();
        }
    }

    @Override
    public void onExecutionFinish() {
        RESET.set(false);
    }

    static boolean firstFor(ITestResult r, String callback) {
        String key = "keel.KeelRetryListener." + callback;
        synchronized (r) {
            if (r.getAttribute(key) != null) {
                return false;
            }
            r.setAttribute(key, Boolean.TRUE);
            return true;
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result) {
        if (!method.isTestMethod() || result.getStatus() != ITestResult.SUCCESS || !firstFor(result, "after")) {
            return;
        }
        int retries = retriesOf(result);
        if (retries == 0) {
            return;
        }
        result.setAttribute(VERDICT_ATTR, "FLAKY");
        String msg = "FLAKY: " + name(result) + " passed on attempt " + (retries + 1)
                + " after " + retries + " failed attempt(s)";
        LogsManager.warn(msg);
        try {
            Allure.getLifecycle().updateTestCase(tr -> {
                StatusDetails d = tr.getStatusDetails() == null ? new StatusDetails() : tr.getStatusDetails();
                d.setFlaky(true);
                d.setMessage(msg);
                tr.setStatusDetails(d);
                tr.getLabels().add(new Label().setName(LABEL).setValue("FLAKY"));
            });
            Allure.step(msg);
        } catch (RuntimeException | LinkageError noAllure) {
            // reporting must never change the test outcome
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        boolean flaky = "FLAKY".equals(result.getAttribute(VERDICT_ATTR));
        record(result, flaky ? "FLAKY" : "PASSED");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        record(result, "FAILED");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        record(result, result.wasRetried() ? "RETRIED" : "SKIPPED");
    }

    static int retriesOf(ITestResult result) {
        IRetryAnalyzer a = result.getMethod().getRetryAnalyzer(result);
        return a instanceof KeelRetryAnalyzer k ? k.retries() : 0;
    }

    private static void record(ITestResult result, String verdict) {
        if (!firstFor(result, "record")) {
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("test", name(result));
        m.put("verdict", verdict);
        m.put("attempt", retriesOf(result) + 1);
        m.put("readOnly", KeelRetryAnalyzer.isReadOnly(result));
        if (result.getThrowable() != null && !"PASSED".equals(verdict) && !"FLAKY".equals(verdict)) {
            m.put("error", String.valueOf(result.getThrowable()).lines().findFirst().orElse(""));
        }
        KeelResults.append(KeelResults.VERDICTS, m);
    }

    static String name(ITestResult r) {
        return r.getTestClass().getRealClass().getName() + "." + r.getMethod().getMethodName();
    }
}
