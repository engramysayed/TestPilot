package delivery.mobile.codegen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Package-manifest fingerprints for mobile proof invalidation (M4-05).
 * Not web {@code ReuseEligibility} — mobile re-proves when any fingerprint input changes.
 * Credential material is an opaque revision id, never a secret value.
 */
public final class MobilePackageFingerprint {

    private MobilePackageFingerprint() {}

    public record Inputs(
            String appRevisionId,
            String apkSha256,
            String credentialRevisionId,
            String locale,
            String toolchainLockSha256,
            String libraryRevisionId,
            String settleTableSha256,
            String packageVersion) {}

    public static String digest(Inputs in) {
        if (in == null) {
            throw new IllegalArgumentException("inputs required");
        }
        Map<String, String> ordered = new TreeMap<>();
        ordered.put("appRevisionId", nz(in.appRevisionId()));
        ordered.put("apkSha256", nz(in.apkSha256()));
        ordered.put("credentialRevisionId", nz(in.credentialRevisionId()));
        ordered.put("locale", nz(in.locale()));
        ordered.put("toolchainLockSha256", nz(in.toolchainLockSha256()));
        ordered.put("libraryRevisionId", nz(in.libraryRevisionId()));
        ordered.put("settleTableSha256", nz(in.settleTableSha256()));
        ordered.put("packageVersion", nz(in.packageVersion()));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sha256(sb.toString());
    }

    /** True when stored proof fingerprint no longer matches current inputs — must re-prove. */
    public static boolean requiresReprove(String storedFingerprint, Inputs current) {
        if (storedFingerprint == null || storedFingerprint.isBlank()) {
            return true;
        }
        return !storedFingerprint.equals(digest(current));
    }

    public static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Bytes(byte[] raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw == null ? new byte[0] : raw));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }

    public static Map<String, String> manifestFields(Inputs in) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("schema", "mobile-package-manifest/1");
        m.put("fingerprint", digest(in));
        m.put("appRevisionId", nz(in.appRevisionId()));
        m.put("apkSha256", nz(in.apkSha256()));
        m.put("credentialRevisionId", nz(in.credentialRevisionId()));
        m.put("locale", nz(in.locale()));
        m.put("toolchainLockSha256", nz(in.toolchainLockSha256()));
        m.put("libraryRevisionId", nz(in.libraryRevisionId()));
        m.put("settleTableSha256", nz(in.settleTableSha256()));
        m.put("packageVersion", nz(in.packageVersion()));
        // Explicit: not web ReuseEligibility
        m.put("reuseShortcut", "NONE");
        return m;
    }
}
