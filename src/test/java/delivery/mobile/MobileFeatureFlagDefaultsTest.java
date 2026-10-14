package delivery.mobile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * M0 / integration 0056: mobile stays off by default so deterministic CI never needs a device.
 * Enabling mobile in CI requires an explicit dual opt-in (property + keel.mobile.ci-opt-in).
 */
public class MobileFeatureFlagDefaultsTest {

    @Test
    public void applicationPropertiesDefaultMobileDisabled() throws Exception {
        Properties p = loadApplicationProperties();
        Assert.assertEquals(p.getProperty("delivery.mobile.enabled", "missing"), "false",
                "delivery.mobile.enabled must default to false (no device in default CI)");
    }

    @Test
    public void systemPropertyCannotEnableMobileWithoutCiOptIn() {
        String previousMobile = System.getProperty("delivery.mobile.enabled");
        String previousOptIn = System.getProperty("keel.mobile.ci-opt-in");
        try {
            System.setProperty("delivery.mobile.enabled", "true");
            System.clearProperty("keel.mobile.ci-opt-in");
            Assert.assertFalse(mobileEffectivelyEnabled(),
                    "delivery.mobile.enabled=true alone must NOT enable mobile without keel.mobile.ci-opt-in=true");

            System.setProperty("keel.mobile.ci-opt-in", "true");
            Assert.assertTrue(mobileEffectivelyEnabled(),
                    "dual opt-in (enabled + ci-opt-in) is required for mobile CI/device paths");
        } finally {
            restore("delivery.mobile.enabled", previousMobile);
            restore("keel.mobile.ci-opt-in", previousOptIn);
        }
    }

    @Test
    public void defaultJvmWithoutOverridesKeepsMobileOff() throws Exception {
        // Simulate reading defaults from the packaged properties when sysprops are unset.
        String previousMobile = System.getProperty("delivery.mobile.enabled");
        String previousOptIn = System.getProperty("keel.mobile.ci-opt-in");
        try {
            System.clearProperty("delivery.mobile.enabled");
            System.clearProperty("keel.mobile.ci-opt-in");
            Properties p = loadApplicationProperties();
            boolean fileSaysOn = "true".equalsIgnoreCase(p.getProperty("delivery.mobile.enabled", "false"));
            Assert.assertFalse(fileSaysOn);
            Assert.assertFalse(mobileEffectivelyEnabled());
        } finally {
            restore("delivery.mobile.enabled", previousMobile);
            restore("keel.mobile.ci-opt-in", previousOptIn);
        }
    }

    /** Same gate workflows/scripts should use until a Spring @ConfigurationProperty binder exists. */
    static boolean mobileEffectivelyEnabled() {
        boolean enabled = Boolean.parseBoolean(System.getProperty("delivery.mobile.enabled", "false"));
        boolean optIn = Boolean.parseBoolean(System.getProperty("keel.mobile.ci-opt-in", "false"));
        return enabled && optIn;
    }

    private static Properties loadApplicationProperties() throws Exception {
        Properties p = new Properties();
        // Prefer classpath (test runtime), then repo file for apply-check trees.
        try (InputStream in = MobileFeatureFlagDefaultsTest.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                p.load(in);
                return p;
            }
        }
        Path file = Path.of("src/main/resources/application.properties");
        Assert.assertTrue(Files.isRegularFile(file), "application.properties missing");
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        return p;
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
}
