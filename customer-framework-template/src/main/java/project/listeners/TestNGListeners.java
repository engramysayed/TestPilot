package project.listeners;

import org.openqa.selenium.WebDriver;
import org.testng.*;
import project.drivers.WebDriverProvider;
import project.media.ScreenShotManager;
import project.utils.FilesManager;
import project.utils.Logs.LogsManager;
import project.utils.dataReader.PropertyReader;
import project.utils.reports.AllureAttachmentManager;
import project.utils.reports.AllureEnviromentsManager;
import project.utils.reports.AllurePaths;
import project.utils.reports.AllureReportGenerator;
import project.validations.Validation;

import java.io.File;

/**
 * Framework listener. Registered via META-INF/services (ServiceLoader) and, for compatibility,
 * BaseTest {@code @Listeners}. Both paths may yield two instances in one run, so every callback is
 * idempotent: execution start/finish run once per JVM run, per-test callbacks once per ITestResult.
 * Set -Dkeel.testng.listeners=off to disable it (e.g. tooling that runs TestNG programmatically).
 */
public class TestNGListeners implements ITestListener, IExecutionListener, IInvokedMethodListener {
    private static final java.util.concurrent.atomic.AtomicBoolean STARTED = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean FINISHED = new java.util.concurrent.atomic.AtomicBoolean();

    static boolean disabled() {
        return "off".equalsIgnoreCase(System.getProperty("keel.testng.listeners", ""));
    }

    /** True the first time this callback runs for this result (dedupes a double-registered listener). */
    static boolean firstFor(ITestResult r, String callback) {
        String key = "keel.TestNGListeners." + callback;
        synchronized (r) {
            if (r.getAttribute(key) != null) {
                return false;
            }
            r.setAttribute(key, Boolean.TRUE);
            return true;
        }
    }

    public void onExecutionStart() {
        if (disabled() || !STARTED.compareAndSet(false, true)) {
            return;
        }
        FINISHED.set(false);
        LogsManager.info("Execution started");
        cleanTestOutputDir();
        LogsManager.info( "Test output directory cleaned");
        createTestOutputDir();
        LogsManager.info( "Test output directory created");
        PropertyReader.loadProperties();
        LogsManager.info("Properties loaded");
        AllureEnviromentsManager.setAllureEnvironment();
        LogsManager.info("Allure environment file created");


    }

    public void onExecutionFinish() {
        if (disabled() || !STARTED.get() || !FINISHED.compareAndSet(false, true)) {
            return;
        }
        STARTED.set(false);
        AllureReportGenerator.generateReports(false);
        AllureReportGenerator.copyHistory();
        AllureReportGenerator.generateReports(true);
        AllureReportGenerator.openReport(AllureReportGenerator.renameReportFile());
        LogsManager.info("Allure report generated and opened");
    }

    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (disabled() || !firstFor(testResult, "before")) {
            return;
        }
        if(method.isTestMethod()){
            LogsManager.info("Starting TestCase: " + testResult.getName());
        }

    }

    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (disabled() || !firstFor(testResult, "after")) {
            return;
        }
        WebDriver driver=null;
        if(method.isTestMethod()){
            try {
                Validation.assertAll();
            } catch (AssertionError e) {
                testResult.setStatus(ITestResult.FAILURE);
                testResult.setThrowable(e);
            }
            if(testResult.getInstance() instanceof WebDriverProvider provider){
                try {
                    driver=provider.getWebDriver();
                } catch (RuntimeException ignored) {
                    driver = null;
                }
                if (driver != null) {
                    switch (testResult.getStatus()){
                    case ITestResult.FAILURE->
                        ScreenShotManager.takeFullPageScreenshot(driver,"TC Failed: " + testResult.getName());
                    case ITestResult.SUCCESS->
                        ScreenShotManager.takeFullPageScreenshot(driver,"TC passed: " + testResult.getName());
                    case ITestResult.SKIP->
                        ScreenShotManager.takeFullPageScreenshot(driver,"TC skipped: " + testResult.getName());
                    }
                }
                AllureAttachmentManager.attachLogs();
            }
        }
    }

    public void onTestSuccess(ITestResult result) {
        if (disabled() || !firstFor(result, "success")) {
            return;
        }
        LogsManager.info("TestCase passed: " + result.getName());
    }

    public void onTestFailure(ITestResult result) {
        if (disabled() || !firstFor(result, "failure")) {
            return;
        }
        LogsManager.error("TestCase failed: " + result.getName());
    }

    public void onTestSkipped(ITestResult result) {
        if (disabled() || !firstFor(result, "skipped")) {
            return;
        }
        LogsManager.info("TestCase skipped: " + result.getName());
    }

    private void cleanTestOutputDir() {
        FilesManager.cleanDirectory(AllurePaths.RESULTS_FOLDER.toFile());
        FilesManager.cleanDirectory(new File(ScreenShotManager.screenShotPath));
        FilesManager.clearFiles(LogsManager.LOGS_PATH + File.separator + "logs.log");
    }

    private void createTestOutputDir() {
        FilesManager.createDirectory(ScreenShotManager.screenShotPath);
        FilesManager.createDirectory(LogsManager.LOGS_PATH);
    }




}
