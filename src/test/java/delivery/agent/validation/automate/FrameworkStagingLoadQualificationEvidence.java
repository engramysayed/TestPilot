package delivery.agent.validation.automate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import delivery.agent.harness.AgentRunStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Staging-only load qualification evidence (Phase 6.15). */
final class FrameworkStagingLoadQualificationEvidence {

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).findAndRegisterModules();

    private FrameworkStagingLoadQualificationEvidence() {
    }

    static void writeBaseline(Path repoRoot, String stagingHost) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("phase", "6.15-staging-baseline");
        doc.put("capturedAt", Instant.now().toString());
        doc.put("stagingHost", stagingHost);
        doc.put("javaVersion", System.getProperty("java.version"));
        doc.put("osName", System.getProperty("os.name"));
        doc.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        doc.put("maxMemoryMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        doc.put("repoRoot", repoRoot.toAbsolutePath().normalize().toString());
        doc.put("instrumentationGaps", List.of("sidecarStartupMs", "storeLockWaitMs", "resultRetrievalMs"));
        Path dest = repoRoot.resolve("docs/handoff/phase6-15-staging-baseline.json");
        write(dest, doc);
    }

    static Path writeCycle(
            Path repoRoot,
            String kind,
            int index,
            String scenarioId,
            String parentJob,
            Path work,
            AutomateFrameworkAcceptanceOutcome outcome,
            boolean compilePassed) throws Exception {
        Map<String, Object> doc = baseCycle(kind, index, scenarioId, parentJob, work, outcome, compilePassed);
        String fileName = "phase6-15-staging-" + kind + "-" + index + "-" + scenarioId + ".json";
        Path dest = repoRoot.resolve("docs/handoff").resolve(fileName);
        write(dest, doc);
        return dest;
    }

    static Path writeConcurrentPair(Path repoRoot, List<Map<String, Object>> jobs) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("phase", "6.15-staging-concurrent");
        doc.put("capturedAt", Instant.now().toString());
        doc.put("jobs", jobs);
        Path dest = repoRoot.resolve("docs/handoff/phase6-15-staging-concurrent-pair.json");
        write(dest, doc);
        return dest;
    }

    static Map<String, Object> cycleMetrics(Path work, String parentJob, AutomateFrameworkAcceptanceOutcome outcome)
            throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gateStatus", outcome.status().name());
        m.put("accepted", outcome.accepted());
        m.put("taskId", outcome.taskId());
        Path operational = FrameworkRepairOperationalReportWriter.reportPath(work);
        if (Files.isRegularFile(operational)) {
            JsonNode op = JSON.readTree(Files.readString(operational));
            m.put("operationalReport", operational.toAbsolutePath().toString());
            JsonNode trace = op.path("trace");
            m.put("bridgeTerminalReason", text(trace, "bridgeTerminalReason"));
            m.put("acceptanceTerminalReason", text(trace, "acceptanceTerminalReason"));
            m.put("bridgeElapsedMs", numberOrNull(trace, "bridgeElapsedMs"));
            m.put("bridgeBudgetMs", numberOrNull(trace, "bridgeBudgetMs"));
            m.put("bridgeBudgetUtilizationPct", numberOrNull(trace, "bridgeBudgetUtilizationPct"));
            m.put("timeToRunStartedMs", numberOrNull(trace, "timeToRunStartedMs"));
            m.put("cursorRunMs", numberOrNull(trace, "cursorRunMs"));
            m.put("reconciliationMs", numberOrNull(trace, "reconciliationMs"));
            m.put("agentRunStarted", trace.path("agentRunStarted").isMissingNode()
                    ? null
                    : trace.path("agentRunStarted").asBoolean());
            m.put("agentId", textOrNull(trace, "agentId"));
            m.put("agentRunId", textOrNull(trace, "agentRunId"));
        }
        String taskId = resolveTaskId(work, parentJob, outcome.taskId());
        if (!taskId.isBlank()) {
            Path taskDir = work.resolve(AgentRunStore.ROOT_DIR).resolve(parentJob).resolve(taskId);
            m.put("taskDir", taskDir.toAbsolutePath().toString());
            Path events = taskDir.resolve("events.jsonl");
            if (Files.isRegularFile(events)) {
                var lines = Files.readAllLines(events);
                m.put("taskEventsTail", lines.size() > 8 ? lines.subList(lines.size() - 8, lines.size()) : lines);
            }
        }
        m.put("sidecarStartupMs", null);
        return m;
    }

    private static Map<String, Object> baseCycle(
            String kind,
            int index,
            String scenarioId,
            String parentJob,
            Path work,
            AutomateFrameworkAcceptanceOutcome outcome,
            boolean compilePassed) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("phase", "6.15-staging-" + kind);
        doc.put("cycleIndex", index);
        doc.put("scenario", scenarioId);
        doc.put("parentJob", parentJob);
        doc.put("workDir", work.toAbsolutePath().normalize().toString());
        doc.put("capturedAt", Instant.now().toString());
        doc.put("compilePassed", compilePassed);
        doc.putAll(cycleMetrics(work, parentJob, outcome));
        return doc;
    }

    private static void write(Path dest, Map<String, Object> doc) throws Exception {
        Files.createDirectories(dest.getParent());
        JSON.writeValue(dest.toFile(), doc);
    }

    private static String resolveTaskId(Path work, String parentJob, String fromOutcome) {
        if (fromOutcome != null && !fromOutcome.isBlank()) {
            return fromOutcome;
        }
        try {
            Path tasksRoot = work.resolve(AgentRunStore.ROOT_DIR).resolve(parentJob);
            if (!Files.isDirectory(tasksRoot)) {
                return "";
            }
            try (var dirs = Files.list(tasksRoot)) {
                return dirs.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .max(String::compareTo)
                        .orElse("");
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static String text(JsonNode n, String field) {
        return n.path(field).isMissingNode() || n.path(field).isNull() ? "" : n.path(field).asText();
    }

    private static String textOrNull(JsonNode n, String field) {
        String t = text(n, field);
        return t.isBlank() ? null : t;
    }

    private static Long numberOrNull(JsonNode n, String field) {
        if (n.path(field).isMissingNode() || n.path(field).isNull()) {
            return null;
        }
        return n.path(field).asLong();
    }
}