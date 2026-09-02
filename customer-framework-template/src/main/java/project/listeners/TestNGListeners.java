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

public class TestNGListeners implements ITestListener, IExecutionListener, IInvokedMethodListener {

    public void onExecutionStart() {
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
        AllureReportGenerator.generateReports(false);
        AllureReportGenerator.copyHistory();
        AllureReportGenerator.generateReports(true);
        AllureReportGenerator.openReport(AllureReportGenerator.renameReportFile());
        LogsManager.info("Allure report generated and opened");
    }

    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if(method.isTestMethod()){
            LogsManager.info("Starting TestCase: " + testResult.getName());
        }

    }

    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        WebDriver driver=null;
        if(method.isTestMethod()){
            Validation.assertAll();
            if(testResult.getInstance() instanceof WebDriverProvider provider){
                driver=provider.getWebDriver();
                switch (testResult.getStatus()){
                case ITestResult.FAILURE->
                    ScreenShotManager.takeFullPageScreenshot(driver,"TC Failed: " + testResult.getName());
                case ITestResult.SUCCESS->
                    ScreenShotManager.takeFullPageScreenshot(driver,"TC passed: " + testResult.getName());
                case ITestResult.SKIP->
                    ScreenShotManager.takeFullPageScreenshot(driver,"TC skipped: " + testResult.getName());
                }
                AllureAttachmentManager.attachLogs();
            }
        }
    }

    public void onTestSuccess(ITestResult result) {
        LogsManager.info("TestCase passed: " + result.getName());
    }

    public void onTestFailure(ITestResult result) {
        LogsManager.error("TestCase failed: " + result.getName());
    }

    public void onTestSkipped(ITestResult result) {
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
