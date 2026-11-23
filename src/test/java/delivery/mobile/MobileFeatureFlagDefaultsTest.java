package delivery.mobile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Mobile is ON by default (flipped 2026-11-23). The dual-opt-in gate
 * (delivery.mobile.enabled + keel.mobile.ci-opt-in) still exists for scripts that need a
 * belt-and-braces check before touching real devices; the SpringBootTest FlagOff variants
 * override to false via @SpringBootTest(properties=…).
 */
public class MobileFeatureFlagDefaultsTest {

    @Test
    public void applicationPropertiesDefaultMobileEnabled() throws Exception {
        Properties p = loadApplicationProperties();
        Assert.assertEquals(p.getProperty("delivery.mobile.enabled", "missing"), "true",
                "delivery.mobile.enabled defaults ON (2026-11-23)");
    }

    @Test
    public void dualOptInStillRequiresBothSystemProperties() {
        // Guards shell scripts that use the dual-opt-in rule — independent of the file default.
        String previousMobile = System.getProperty("delivery.mobile.enabled");
        String previousOptIn = System.getProperty("keel.mobile.ci-opt-in");
        try {
            System.setProperty("delivery.mobile.enabled", "true");
            System.clearProperty("keel.mobile.ci-opt-in");
            Assert.assertFalse(mobileEffectivelyEnabled(),
                    "dual-opt-in script gate: enabled=true alone must not pass without ci-opt-in");

            System.setProperty("keel.mobile.ci-opt-in", "true");
            Assert.assertTrue(mobileEffectivelyEnabled(),
                    "dual-opt-in script gate: enabled + ci-opt-in both true → pass");
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
