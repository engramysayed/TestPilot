package delivery.mobile;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * M0-05 + M1-02..M1-06: feature flags default off; upload/ADB/lease/transport/Appium limits have safe defaults.
 */
public class MobilePropertiesDefaultsTest {
    @Test
    public void defaultsAreOff() {
        MobileProperties props = new MobileProperties();
        Assert.assertFalse(props.isEnabled(), "delivery.mobile.enabled must default false");
        Assert.assertFalse(props.isLiveTests(), "delivery.mobile.live-tests must default false");
    }

    @Test
    public void uploadDefaultsAreConservative() {
        MobileProperties props = new MobileProperties();
        Assert.assertEquals(props.getUploadMaxBytes(), 500L * 1024L * 1024L);
        Assert.assertFalse(props.isAllowDowngrade());
        Assert.assertFalse(props.isAllowSignatureReplacement());
        Assert.assertEquals(props.getApkanalyzerPath(), "apkanalyzer");
        Assert.assertEquals(props.getAapt2Path(), "aapt2");
    }

    @Test
    public void adbDefaultsAreConservative() {
        MobileProperties props = new MobileProperties();
        Assert.assertEquals(props.getAdbPath(), "adb");
        Assert.assertEquals(props.getAdbTimeoutSeconds(), 15);
    }

    @Test
    public void leaseDefaultsAreConservative() {
        MobileProperties props = new MobileProperties();
        Assert.assertEquals(props.getLeaseHeartbeatSeconds(), 5);
        Assert.assertEquals(props.getLeaseExpirySeconds(), 30);
        Assert.assertEquals(props.getHostLockRoot(), "");
    }

    @Test
    public void runnerTransportDefaultsAreConservative() {
        MobileProperties props = new MobileProperties();
        Assert.assertEquals(props.getSecretEnvelopeTtlSeconds(), 300);
        Assert.assertEquals(props.getRunnerInputZipMaxBytes(), 64L * 1024L * 1024L);
        Assert.assertEquals(props.getRunnerInputZipMaxEntries(), 10_000);
        Assert.assertEquals(props.getRunnerInputZipMaxUncompressedBytes(), 64L * 1024L * 1024L);
        Assert.assertEquals(props.getRunnerHmacMaxBodyBytes(), 64L * 1024L * 1024L);
        Assert.assertEquals(props.getRunnerProtocolVersion(), 1);
    }

    @Test
    public void appiumLifecycleDefaultsAreConservative() {
        MobileProperties props = new MobileProperties();
        Assert.assertEquals(props.getAppiumPath(), "appium");
        Assert.assertEquals(props.getAppiumPortRangeStart(), 4723);
        Assert.assertEquals(props.getAppiumPortRangeEnd(), 4923);
        Assert.assertEquals(props.getAppiumStartTimeoutSeconds(), 60);
        Assert.assertEquals(props.getSessionStartTimeoutSeconds(), 60);
        Assert.assertEquals(props.getTeardownDeadlineSeconds(), 30);
        Assert.assertFalse(props.isEnabled());
        Assert.assertFalse(props.isLiveTests());
    }
}
