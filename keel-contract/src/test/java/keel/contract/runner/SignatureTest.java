package keel.contract.runner;

import org.testng.annotations.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.testng.Assert.*;

public class SignatureTest {
    private static String hmac(String k, String s) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(k.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(m.doFinal(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void artifactSignatureKeepsV1WireFormat() throws Exception {
        byte[] body = "zip".getBytes(StandardCharsets.UTF_8);
        String expected = hmac("tp_run_x", "job.att." + ArtifactSignature.digest(body));
        assertEquals(ArtifactSignature.signature("tp_run_x", "job", "att", body), expected);
        assertTrue(ArtifactSignature.matches("tp_run_x", "job", "att", body, "sha256=" + expected.toUpperCase()));
        assertFalse(ArtifactSignature.matches("tp_run_x", "job", "att2", body, expected));
        assertFalse(ArtifactSignature.matches("", "job", "att", body, expected));
    }

    @Test
    public void requestSignatureCoversEveryCanonicalPart() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String sig = RequestSigner.sign("tp_run_x", "post", "/api/v2/runners/claim", "", 100, "n1", body);
        assertTrue(sig.startsWith("v1="));
        assertTrue(RequestSigner.verify("tp_run_x", "POST", "/api/v2/runners/claim", "", 100, "n1", body, sig));
        assertFalse(RequestSigner.verify("tp_run_y", "POST", "/api/v2/runners/claim", "", 100, "n1", body, sig));
        assertFalse(RequestSigner.verify("tp_run_x", "GET", "/api/v2/runners/claim", "", 100, "n1", body, sig));
        assertFalse(RequestSigner.verify("tp_run_x", "POST", "/api/v2/runners/other", "", 100, "n1", body, sig));
        assertFalse(RequestSigner.verify("tp_run_x", "POST", "/api/v2/runners/claim", "a=1", 100, "n1", body, sig));
        assertFalse(RequestSigner.verify("tp_run_x", "POST", "/api/v2/runners/claim", "", 101, "n1", body, sig));
        assertFalse(RequestSigner.verify("tp_run_x", "POST", "/api/v2/runners/claim", "", 100, "n2", body, sig));
        assertFalse(RequestSigner.verify("tp_run_x", "POST", "/api/v2/runners/claim", "", 100, "n1",
                "{ }".getBytes(StandardCharsets.UTF_8), sig));
        assertFalse(RequestSigner.verify("tp_run_x", "POST", "/api/v2/runners/claim", "", 100, "n1", body, null));
    }
}
