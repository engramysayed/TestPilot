package delivery.agent.harness;

import delivery.store.Atomic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes {@code validation-report.json} with Phase 3 required fields. */
public final class ValidationReportWriter {
    private static final Logger log = LoggerFactory.getLogger(ValidationReportWriter.class);

    private ValidationReportWriter() {
    }

    public static Map<String, Object> baseReport(String inputFingerprint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("validationScope", "PACKAGED_ZIP");
        m.put("workspaceSource", "ZIP_EXTRACT");
        m.put("inputFingerprint", inputFingerprint == null ? "" : inputFingerprint);
        m.put("validated", false);
        return m;
    }

    public static void persistPrimary(Path taskDir, Map<String, Object> report) throws IOException {
        Path file = taskDir.resolve("validation-report.json");
        Files.createDirectories(taskDir);
        Atomic.writeString(file, toJson(report), StandardCharsets.UTF_8);
    }

    public static void persistFallback(Path projectRoot, String parentJobId, String taskId, Map<String, Object> report)
            throws IOException {
        Path parent = projectRoot.resolve(AgentRunStore.ROOT_DIR).resolve(parentJobId);
        Files.createDirectories(parent);
        Path file = parent.resolve("validation-fallback-" + taskId + ".json");
        Atomic.writeString(file, toJson(report), StandardCharsets.UTF_8);
    }

    /**
     * Advisory-only: never throws to caller for persistence failure.
     *
     * @return true if primary or fallback succeeded
     */
    public static boolean persistFailOpen(
            Path projectRoot,
            String parentJobId,
            Path taskDir,
            String taskId,
            Map<String, Object> report,
            AgentRunStore store) {
        Exception primary = null;
        Exception fallback = null;
        try {
            persistPrimary(taskDir, report);
            return true;
        } catch (Exception e) {
            primary = e;
        }
        try {
            persistFallback(projectRoot, parentJobId, taskId, report);
            return true;
        } catch (Exception e) {
            fallback = e;
        }
        if (primary != null) {
            log.warn("validation-report primary persist failed for {}: {}", taskId, primary.getMessage(), primary);
        }
        if (fallback != null) {
            log.warn("validation-report fallback persist failed for {}: {}", taskId, fallback.getMessage(), fallback);
        }
        if (store != null && taskDir != null) {
            try {
                store.appendEvent(projectRoot, parentJobId, taskId,
                        new AgentEvent(java.time.Instant.now(), "REPORT_PERSIST_FAILED",
                                "validation report not persisted",
                                Map.of(
                                        "primary", primary == null ? "" : primary.getMessage(),
                                        "fallback", fallback == null ? "" : fallback.getMessage())));
            } catch (Exception ignored) {
                log.warn("could not append REPORT_PERSIST_FAILED event for {}", taskId);
            }
        }
        return false;
    }

    private static String toJson(Map<String, Object> report) {
        org.json.JSONObject json = new org.json.JSONObject(report);
        return json.toString(2);
    }
}
