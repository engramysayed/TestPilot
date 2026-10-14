package delivery.mobile.transport;

import delivery.mobile.MobileProperties;
import keel.contract.runner.RunnerCapability;
import keel.contract.runner.RunnerDtos;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** M1-05 end-to-end transport facade (no Spring / no Appium). */
public class MobileRunnerTransportTest {
    private MobileRunnerTransport transport;

    @BeforeMethod
    public void setUp() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("./target/test-delivery-store-mobile-m105-transport");
        Files.createDirectories(root);
        System.setProperty("delivery.store-root", root.toString());
        System.setProperty("DELIVERY_SECRET_KEY", "unit-test-mobile-m105-transport-key");
        MobileProperties props = new MobileProperties();
        props.setEnabled(true);
        transport = new MobileRunnerTransport(props, new MobileClaimRegistry());
    }

    @Test
    public void registerClaimProducesExtrasAndSecretChannel() {
        Instant now = Instant.parse("2026-10-14T12:00:00Z");
        MobileClaimSnapshot snap = transport.registerMobileClaim(
                "tenant_a", "job_m", "att_m", "proj_m", "dev_m", 3L,
                "apprev_m", "devrev_m", "lib_m", "sha_m", List.of("TC_01"),
                Map.of("auth.phone", "+1000"), "tp_run_tok", now);
        Assert.assertNotNull(snap.inputManifestHash());
        Assert.assertFalse(snap.secretEnvelopeRef().isBlank());
        RunnerDtos.MobileClaimExtras extras = transport.extrasFor("job_m");
        Assert.assertNotNull(extras);
        Assert.assertEquals(extras.platform(), "ANDROID");
        Assert.assertTrue(extras.requiredCapabilities().contains(RunnerCapability.ANDROID_UIAUTOMATOR2));

        Map<String, String> secrets = transport.openSecrets("tenant_a", "job_m", "att_m", "tp_run_tok", now);
        Assert.assertEquals(secrets.get("auth.phone"), "+1000");

        try {
            transport.openSecrets("tenant_b", "job_m", "att_m", "tp_run_tok", now);
            Assert.fail("cross-tenant");
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.CROSS_TENANT);
        }

        transport.assertBinding("tenant_a", "job_m", "att_m", "dev_m", 3L, snap.inputManifestHash());
        try {
            transport.assertBinding("tenant_a", "job_m", "att_stale", "dev_m", 3L, snap.inputManifestHash());
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.STALE_ATTEMPT);
        }
    }
}
