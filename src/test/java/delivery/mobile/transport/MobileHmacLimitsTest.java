package delivery.mobile.transport;

import keel.contract.runner.ArtifactSignature;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

/** M1-05: HMAC body size limits. */
public class MobileHmacLimitsTest {

    @Test
    public void rejectsOversizedBodyBeforeHmac() {
        byte[] body = "x".repeat(100).getBytes(StandardCharsets.UTF_8);
        try {
            MobileHmacLimits.requireBodyWithinLimit(body, 50);
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.HMAC_LIMIT);
        }
    }

    @Test
    public void verifiesWithinLimit() {
        byte[] body = "artifact".getBytes(StandardCharsets.UTF_8);
        String sig = ArtifactSignature.signature("tp_run_testtoken", "job1", "att1", body);
        Assert.assertTrue(MobileHmacLimits.verifyArtifact("tp_run_testtoken", "job1", "att1", body, sig, 1024));
        Assert.assertFalse(MobileHmacLimits.verifyArtifact("tp_run_testtoken", "job1", "att1", body, "bad", 1024));
    }
}
