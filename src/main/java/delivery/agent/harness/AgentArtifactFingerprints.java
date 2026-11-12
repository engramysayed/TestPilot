package delivery.agent.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** SHA-256 fingerprints for harness artifact identity. */
public final class AgentArtifactFingerprints {
    public static final String STAGE_CONVERT_PRE_PUBLISH = "CONVERT_PRE_PUBLISH";

    private AgentArtifactFingerprints() {
    }

    public static String sha256Hex(Path file) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String deterministicTaskId(String parentJobId, String stageKey, String inputFingerprint) {
        String harnessKey = parentJobId + "\0" + stageKey + "\0" + inputFingerprint;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(harnessKey.getBytes(StandardCharsets.UTF_8));
        String hex = HexFormat.of().formatHex(digest);
            return "fp_" + hex.substring(0, Math.min(24, hex.length()));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
