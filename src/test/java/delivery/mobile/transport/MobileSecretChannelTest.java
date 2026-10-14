package delivery.mobile.transport;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** M1-05: protected secret channel — attempt-bound, TTL, not in manifest. */
public class MobileSecretChannelTest {

    @Test
    public void secretsRoundTripAndExpire() throws Exception {
        PathTemp.ensureSecretKey();
        MobileSecretChannel channel = new MobileSecretChannel();
        Instant now = Instant.parse("2026-10-14T12:00:00Z");
        var env = channel.seal("job_1", "att_1", Map.of("auth.otp", "123456"), now,
                Duration.ofSeconds(30), "tp_run_testtoken");
        Assert.assertNotNull(env.envelopeId());
        Map<String, String> opened = channel.open(env.envelopeId(), "job_1", "att_1", now.plusSeconds(5),
                "tp_run_testtoken");
        Assert.assertEquals(opened.get("auth.otp"), "123456");

        try {
            channel.open(env.envelopeId(), "job_1", "att_1", now.plusSeconds(60), "tp_run_testtoken");
            Assert.fail("expected expiry");
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.SECRET_EXPIRED);
        }
    }

    @Test
    public void staleAttemptRejected() throws Exception {
        PathTemp.ensureSecretKey();
        MobileSecretChannel channel = new MobileSecretChannel();
        Instant now = Instant.parse("2026-10-14T12:00:00Z");
        var env = channel.seal("job_1", "att_1", Map.of("pw", "secret"), now, Duration.ofMinutes(5),
                "tp_run_testtoken");
        try {
            channel.open(env.envelopeId(), "job_1", "att_other", now, "tp_run_testtoken");
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.STALE_ATTEMPT);
        }
    }

    @Test
    public void manifestNeverEmbedsSecretValues() {
        MobileInputManifest m = new MobileInputManifest("1", "ANDROID", "job_1", "att_1", "proj",
                "lib", "apprev", "devrev", "sha", java.util.List.of("TC"),
                "sec_ref_only", "dev_1", 1L);
        m.assertNoEmbeddedSecrets();
        Assert.assertFalse(m.canonicalJson().contains("password="));
        Assert.assertTrue(m.canonicalJson().contains("sec_ref_only"));
    }

    /** Isolates JobSecretCrypto key file under target/. */
    static final class PathTemp {
        static void ensureSecretKey() throws Exception {
            java.nio.file.Path root = java.nio.file.Path.of("./target/test-delivery-store-mobile-m105-secret");
            Files.createDirectories(root);
            System.setProperty("delivery.store-root", root.toString());
            System.setProperty("DELIVERY_SECRET_KEY", "unit-test-mobile-m105-secret-key");
        }
    }
}
