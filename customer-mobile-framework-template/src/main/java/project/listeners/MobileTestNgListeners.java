package project.listeners;

import org.testng.ITestListener;
import org.testng.ITestResult;
import project.utils.retry.MobileReadOnlyRetryAnalyzer;

/** Marks FLAKY on retry-pass of read-only cases; evidence hooks later. */
public class MobileTestNgListeners implements ITestListener {
    @Override
    public void onTestSuccess(ITestResult result) {
        if (result != null && Boolean.TRUE.equals(result.getAttribute(MobileReadOnlyRetryAnalyzer.FLAKY_ATTRIBUTE))) {
            result.setAttribute("keel.status", "FLAKY");
        }
    }
}
