package delivery.job;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/** Signed webhook deliveries with bounded retries and duplicate detection. */
public final class WebhookSigner {
    public static final int MAX_ATTEMPTS = 5;

    private WebhookSigner() {
    }

    public static String signature(String secret, String deliveryId, String body) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("webhook secret is required");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = (deliveryId == null ? "" : deliveryId) + "." + (body == null ? "" : body);
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hmac unavailable", e);
        }
    }

    public static Duration retryDelay(int attempt) {
        if (attempt < 1 || attempt >= MAX_ATTEMPTS) {
            return Duration.ZERO;
        }
        long seconds = Math.min(300, (long) Math.pow(4, attempt - 1));
        return Duration.ofSeconds(seconds);
    }

    public static boolean duplicate(String recordedDeliveryId, String incomingDeliveryId) {
        if (recordedDeliveryId == null || incomingDeliveryId == null) {
            return false;
        }
        return recordedDeliveryId.equals(incomingDeliveryId);
    }

    public static String digestBody(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((body == null ? "" : body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString((body == null ? "" : body).hashCode());
        }
    }

    public static String headerValue(String signature) {
        return "sha256=" + (signature == null ? "" : signature.toLowerCase(Locale.ROOT));
    }
}
