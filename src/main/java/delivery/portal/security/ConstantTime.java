package delivery.portal.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Constant-time string equality for secrets (tokens, agent secrets). */
public final class ConstantTime {
    private ConstantTime() {
    }

    public static boolean equals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
