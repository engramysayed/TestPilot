package delivery.mobile.transport;

import keel.contract.runner.ArtifactSignature;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Non-secret frozen mobile input manifest. Secrets never appear here — only {@code secretEnvelopeRef}.
 */
public final class MobileInputManifest {
    private final String schemaVersion;
    private final String platform;
    private final String jobId;
    private final String attemptId;
    private final String projectId;
    private final String libraryRevisionId;
    private final String appRevisionId;
    private final String profileRevisionId;
    private final String apkSha256;
    private final List<String> selectedTcIds;
    private final String secretEnvelopeRef;
    private final String deviceId;
    private final long leaseFence;

    public MobileInputManifest(String schemaVersion, String platform, String jobId, String attemptId,
                               String projectId, String libraryRevisionId, String appRevisionId,
                               String profileRevisionId, String apkSha256, List<String> selectedTcIds,
                               String secretEnvelopeRef, String deviceId, long leaseFence) {
        this.schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1" : schemaVersion;
        this.platform = platform == null || platform.isBlank() ? "ANDROID" : platform;
        this.jobId = nullToEmpty(jobId);
        this.attemptId = nullToEmpty(attemptId);
        this.projectId = nullToEmpty(projectId);
        this.libraryRevisionId = nullToEmpty(libraryRevisionId);
        this.appRevisionId = nullToEmpty(appRevisionId);
        this.profileRevisionId = nullToEmpty(profileRevisionId);
        this.apkSha256 = nullToEmpty(apkSha256);
        this.selectedTcIds = selectedTcIds == null ? List.of() : List.copyOf(selectedTcIds);
        this.secretEnvelopeRef = nullToEmpty(secretEnvelopeRef);
        this.deviceId = nullToEmpty(deviceId);
        this.leaseFence = leaseFence;
    }

    public String canonicalJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", schemaVersion);
        m.put("platform", platform);
        m.put("jobId", jobId);
        m.put("attemptId", attemptId);
        m.put("projectId", projectId);
        m.put("libraryRevisionId", libraryRevisionId);
        m.put("appRevisionId", appRevisionId);
        m.put("profileRevisionId", profileRevisionId);
        m.put("apkSha256", apkSha256);
        m.put("selectedTcIds", selectedTcIds);
        m.put("secretEnvelopeRef", secretEnvelopeRef);
        m.put("deviceId", deviceId);
        m.put("leaseFence", leaseFence);
        return toJson(m);
    }

    public String hash() {
        return ArtifactSignature.Hmac.sha256Hex(canonicalJson().getBytes(StandardCharsets.UTF_8));
    }

    public String secretEnvelopeRef() {
        return secretEnvelopeRef;
    }

    public String deviceId() {
        return deviceId;
    }

    public long leaseFence() {
        return leaseFence;
    }

    public String jobId() {
        return jobId;
    }

    public String attemptId() {
        return attemptId;
    }

    /** Reject manifests that smuggle secret material into non-ref fields. */
    public void assertNoEmbeddedSecrets() {
        for (String v : List.of(libraryRevisionId, appRevisionId, profileRevisionId, apkSha256,
                secretEnvelopeRef, deviceId, jobId, attemptId, projectId)) {
            if (looksLikeSecretPayload(v)) {
                throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID,
                        "secret material must not appear in input manifesto");
            }
        }
        for (String tc : selectedTcIds) {
            if (looksLikeSecretPayload(tc)) {
                throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID,
                        "secret material must not appear in selectedTcIds");
            }
        }
    }

    static boolean looksLikeSecretPayload(String v) {
        if (v == null || v.isBlank()) {
            return false;
        }
        String s = v.toLowerCase();
        if (s.contains("password=") || s.contains("passwd=") || s.contains("secret=")
                || s.contains("api_key=") || s.contains("apikey=")) {
            return true;
        }
        // Long opaque base64-ish blobs in identity fields are suspicious.
        return v.length() > 256 && v.chars().allMatch(c ->
                (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                        || c == '+' || c == '/' || c == '=' || c == '-');
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String toJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else if (v instanceof List<?> list) {
                sb.append('[');
                boolean f2 = true;
                // Keep declaration order for selectedTcIds (already copyOf); do not resort.
                for (Object o : list) {
                    if (!f2) {
                        sb.append(',');
                    }
                    f2 = false;
                    sb.append('"').append(escape(o == null ? "" : Objects.toString(o))).append('"');
                }
                sb.append(']');
            } else {
                sb.append('"').append(escape(v == null ? "" : Objects.toString(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
