package delivery.portal.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encrypt/decrypt for job target credentials at rest (D12).
 * Key from env {@code DELIVERY_SECRET_KEY} or file {@code delivery-store/delivery.secret}.
 */
public final class JobSecretCrypto {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;

    private JobSecretCrypto() {
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv);
            buf.put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("Job secret encrypt failed", e);
        }
    }

    public static String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }
        try {
            byte[] all = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Job secret decrypt failed", e);
        }
    }

    private static SecretKey key() throws Exception {
        String raw = System.getenv("DELIVERY_SECRET_KEY");
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty("DELIVERY_SECRET_KEY", "");
        }
        if (raw == null || raw.isBlank()) {
            Path file = Path.of(System.getProperty("delivery.store-root", "./delivery-store"))
                    .resolve("delivery.secret");
            if (Files.isRegularFile(file)) {
                raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            } else {
                Files.createDirectories(file.getParent());
                raw = Base64.getEncoder().encodeToString(SecureRandom.getInstanceStrong().generateSeed(32));
                Files.writeString(file, raw, StandardCharsets.UTF_8);
            }
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
