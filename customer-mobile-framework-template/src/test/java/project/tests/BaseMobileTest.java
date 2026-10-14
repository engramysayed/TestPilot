package project.tests;

import io.appium.java_client.android.AndroidDriver;
import org.testng.ITestContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;
import project.config.MobileConfig;
import project.config.MobileProperties;
import project.config.PrerequisiteChecker;
import project.drivers.AndroidDriverFactory;
import project.drivers.AndroidDriverProvider;
import project.runtime.auth.AuthCoordinator;
import project.runtime.capture.CapturedValues;
import project.utils.dataReader.PropertyReader;

/**
 * Owns session lifecycle + requiredProperty (web P0 mirror).
 * Generated / Todo tests stay thin: screen vars + chronological calls only.
 * One device; suite must not enable parallel methods/classes.
 */
@Listeners({project.listeners.MobileTestNgListeners.class})
public abstract class BaseMobileTest implements AndroidDriverProvider {

    protected AndroidDriverFactory driverFactory;
    protected MobileConfig config;
    private boolean sessionStarted;

    @Override
    public AndroidDriver getAndroidDriver() {
        return driverFactory == null ? null : driverFactory.get();
    }

    @BeforeSuite(alwaysRun = true)
    public void initializeSuite(ITestContext context) {
        PropertyReader.loadProperties();
        rejectParallel(context);
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        sessionStarted = false;
        CapturedValues.clear();
        MobileProperties props = PropertyReader.mobileProperties();
        config = new MobileConfig(props);
        PrerequisiteChecker.checkOrThrow(config);
        driverFactory = new AndroidDriverFactory();
        // Fresh session per method — operator must configure Appium
        driverFactory.create(config);
        sessionStarted = true;
        authSetupIfConfigured();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            // soft-assert flush hooks land with validations package (M4 follow-on)
        } finally {
            try {
                if (driverFactory != null) {
                    driverFactory.quitQuietly();
                }
            } finally {
                sessionStarted = false;
                CapturedValues.clear();
            }
        }
    }

    /** Prefer this over duplicating property lookups in every generated test. */
    protected static String requiredProperty(String key) {
        return PropertyReader.required(key);
    }

    /**
     * Auth/setup hook (M4-03): delegates to {@link AuthCoordinator#applyConfigured()}.
     * Modes/profile revision ids are AE contract refs — never runner tokens or credentials.
     */
    protected void authSetupIfConfigured() {
        AuthCoordinator.applyConfigured();
    }

    protected boolean isSessionStarted() {
        return sessionStarted;
    }

    private static void rejectParallel(ITestContext context) {
        if (context == null || context.getSuite() == null) {
            return;
        }
        var suite = context.getSuite().getXmlSuite();
        if (suite == null) {
            return;
        }
        if (suite.getParallel() != null
                && suite.getParallel() != org.testng.xml.XmlSuite.ParallelMode.NONE
                ) {
            throw new IllegalStateException(
                    "Mobile suite forbids TestNG parallel mode (" + suite.getParallel()
                            + "). One device only — set parallel=\"none\".");
        }
    }
}
