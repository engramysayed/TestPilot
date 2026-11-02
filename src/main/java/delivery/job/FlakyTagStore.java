package delivery.job;

import delivery.evidence.Phase2EvidenceConfig;
import delivery.evidence.Phase2Session;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.LogsManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * AE flaky signal append under {@code {projectRoot}/flaky-tags.json}.
 * Backend remains SoT for list/clear via portal APIs.
 */
public final class FlakyTagStore {
    public static final String FILE = "flaky-tags.json";

    private FlakyTagStore() {
    }

    public static String append(
            Path projectRoot,
            String tcId,
            Integer stepIndex,
            String reason,
            String sourceJobId,
            String signal
    ) {
        if (!Phase2EvidenceConfig.flakyEmitEnabled()) {
            return null;
        }
        if (projectRoot == null) {
            projectRoot = Phase2Session.projectRoot();
        }
        if (projectRoot == null || tcId == null || tcId.isBlank()) {
            return null;
        }
        String tagId = UUID.randomUUID().toString();
        String sig = (signal == null || signal.isBlank()) ? "other" : signal.trim();
        try {
            Path file = projectRoot.resolve(FILE);
            JSONArray arr = readArray(file);
            JSONObject row = new JSONObject();
            row.put("tagId", tagId);
            row.put("tcId", tcId);
            if (stepIndex == null) {
                row.put("stepIndex", JSONObject.NULL);
            } else {
                row.put("stepIndex", stepIndex);
            }
            row.put("tag", "flaky");
            row.put("reason", reason == null ? "" : reason);
            if (sourceJobId == null || sourceJobId.isBlank()) {
                String jid = Phase2Session.jobId();
                if (jid == null || jid.isBlank()) {
                    row.put("sourceJobId", JSONObject.NULL);
                } else {
                    row.put("sourceJobId", jid);
                }
            } else {
                row.put("sourceJobId", sourceJobId);
            }
            row.put("signal", sig);
            row.put("createdAt", Instant.now().toString());
            row.put("clearedAt", JSONObject.NULL);
            arr.put(row);
            Files.createDirectories(projectRoot);
            Files.writeString(file, arr.toString(2), StandardCharsets.UTF_8);
            LogsManager.info("PHASE2_FLAKY: tagged " + tcId + " signal=" + sig);
            return tagId;
        } catch (Exception e) {
            LogsManager.warn("PHASE2_FLAKY: " + e.getMessage());
            return null;
        }
    }

    /** Map common failure text to a contract signal hint. */
    public static String signalFromReason(String reason, String healTier) {
        String r = reason == null ? "" : reason.toLowerCase();
        if (r.contains("timeout") || r.contains("timed out")) {
            return "timeout";
        }
        if (healTier != null && !healTier.isBlank() && !"none".equalsIgnoreCase(healTier)) {
            return "heal_retry";
        }
        if (r.contains("intermittent") || r.contains("flaky")) {
            return "intermittent";
        }
        return "other";
    }

    static JSONArray readArray(Path file) throws Exception {
        if (file == null || !Files.isRegularFile(file)) {
            return new JSONArray();
        }
        String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) {
            return new JSONArray();
        }
        if (raw.startsWith("[")) {
            return new JSONArray(raw);
        }
        return new JSONArray();
    }
}
