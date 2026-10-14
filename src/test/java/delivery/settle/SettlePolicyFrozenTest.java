package delivery.settle;

import org.testng.annotations.Test;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.testng.Assert.assertEquals;

/**
 * FROZEN: keel.settle-policy v1 is pinned byte for byte (Keel Codegen r1/06 copies it verbatim and pins the
 * same sha256). Any change to settle-policy.json, settle-probe.js or settle-policy.golden.txt requires
 * "version": 2 in the table, new pins here, and an announcement to Codegen. Do not just update the hashes.
 */
public class SettlePolicyFrozenTest {
    static final String V1_TABLE = "96f0dd0596ba003df868efca65fbd25c215a10b2c826124dddd3471c5aa473a0";

    static String sha(String res) throws Exception {
        try (var in = SettlePolicyFrozenTest.class.getResourceAsStream(res)) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }

    static byte[] bytes(String res) throws Exception {
        try (var in = SettlePolicyFrozenTest.class.getResourceAsStream(res)) {
            return in.readAllBytes();
        }
    }

    @Test
    public void crlfCopyPassesAndRealChangeFailsClearly() {
        byte[] lf = "a\nb\n".getBytes();
        String pin = LfPins.sha(lf);
        // Save/restore so a user-set -Dkeel.pins.strict-eol=true is not cleared for later tests in the same JVM.
        String previous = System.getProperty("keel.pins.strict-eol");
        try {
            System.clearProperty("keel.pins.strict-eol"); // force off for the "CRLF passes" assertion only
            org.testng.Assert.assertNull(LfPins.check("x", "a\r\nb\r\n".getBytes(), pin));
            System.setProperty("keel.pins.strict-eol", "true");
            org.testng.Assert.assertTrue(LfPins.check("x", "a\r\nb\r\n".getBytes(), pin).contains("CRLF line endings"));
            org.testng.Assert.assertTrue(LfPins.check("x", "a\nc\n".getBytes(), pin).contains("FROZEN"));
        } finally {
            if (previous == null) System.clearProperty("keel.pins.strict-eol");
            else System.setProperty("keel.pins.strict-eol", previous);
        }
    }

    @Test
    public void v1BytesArePinned() throws Exception {
        if (SettlePolicy.VERSION == 1) {
            LfPins.assertPinned("keel/settle-policy.json", bytes("/keel/settle-policy.json"), V1_TABLE);
            LfPins.assertPinned("keel/settle-probe.js", bytes("/keel/settle-probe.js"), PROBE_V1);
            LfPins.assertPinned("keel/settle-policy.golden.txt", bytes("/keel/settle-policy.golden.txt"), GOLDEN_V1);
        }
    }

    static final String PROBE_V1 = "c73cdd8b2dbd319424b09e4fc417a3b66464571a169448d69cc6cc7b29459932";
    static final String GOLDEN_V1 = "78b16a7b2c5fbd63e869ad51d8061437f57124f6479cb505ed8c70f8903c72b4";
}
