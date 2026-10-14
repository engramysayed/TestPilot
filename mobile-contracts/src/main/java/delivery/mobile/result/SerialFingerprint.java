package delivery.mobile.result;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Opaque hashed ADB serial for evidence (M2-07). Never stores or returns raw serial.
 */
public final class SerialFingerprint {
    private SerialFingerprint() {
    }

    /** SHA-256 hex of UTF-8 serial, truncated to 16 hex chars (64-bit prefix). */
    public static String hash(String rawSerial) {
        if (rawSerial == null || rawSerial.isBlank()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(rawSerial.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 required", e);
        }
    }
}
