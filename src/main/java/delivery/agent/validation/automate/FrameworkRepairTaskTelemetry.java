package delivery.agent.validation.automate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentRunStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Reads authoritative timestamps from persisted harness task artifacts. */
final class FrameworkRepairTaskTelemetry {

    private static final ObjectMapper JSON = new ObjectMapper();

    private FrameworkRepairTaskTelemetry() {
    }

    static void enrich(
            FrameworkRepairOperationalTrace.Builder trace,
            Path projectRoot,
            String parentJobId,
            String taskId) {
        if (projectRoot == null || parentJobId == null || taskId == null || taskId.isBlank()) {
            return;
        }
        Path taskDir = projectRoot.resolve(AgentRunStore.ROOT_DIR).resolve(parentJobId).resolve(taskId);
        trace.taskId(taskId);
        Path taskJson = taskDir.resolve("task.json");
        Instant created = null;
        Instant completed = null;
        String agentId = null;
        String agentRunId = null;
        String taskStatus = null;
        if (Files.isRegularFile(taskJson)) {
            try {
                JsonNode t = JSON.readTree(Files.readString(taskJson));
                taskStatus = text(t, "taskStatus");
                agentId = text(t, "agentId");
                agentRunId = text(t, "agentRunId");
                created = parseInstant(text(t, "createdAt"));
                completed = parseInstant(text(t, "completedAt"));
                trace.agentId(blankToNull(agentId));
                trace.agentRunId(blankToNull(agentRunId));
                trace.taskStatus(taskStatus);
            } catch (Exception ignored) {
                // leave nulls
            }
        }
        Instant startEvent = null;
        Instant terminalEvent = null;
        String terminalSummary = "";
        Path events = taskDir.resolve("events.jsonl");
        if (Files.isRegularFile(events)) {
            try {
                for (String line : Files.readAllLines(events)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode e = JSON.readTree(line);
                    String type = text(e, "type");
                    Instant at = parseInstant(text(e, "at"));
                    if ("START".equals(type) && at != null) {
                        startEvent = at;
                    }
                    if ("TERMINAL".equals(type) && at != null) {
                        terminalEvent = at;
                        if (e.has("details")) {
                            terminalSummary = text(e.path("details"), "summary");
                        }
                        if (terminalSummary.isBlank()) {
                            terminalSummary = text(e, "message");
                        }
                    }
                }
            } catch (Exception ignored) {
                // leave nulls
            }
        }
        boolean runStarted = (agentId != null && !agentId.isBlank() && agentRunId != null && !agentRunId.isBlank())
                || startEvent != null;
        trace.agentRunStarted(runStarted);
        if (created != null && startEvent != null) {
            long startupMs = Math.max(0, startEvent.toEpochMilli() - created.toEpochMilli());
            trace.timeToRunStartedMs(startupMs);
            // B2 closed: sidecarStartupMs is the time from task creation to the runtime reporting
            // the sidecar has started. Previously left null; now derived from the same timestamps
            // the telemetry already reads. Same value as timeToRunStartedMs, surfaced under the
            // field the Phase 6.16 audit called out.
            trace.sidecarStartupMs(startupMs);
        }
        if (startEvent != null && terminalEvent != null) {
            trace.cursorRunMs(Math.max(0, terminalEvent.toEpochMilli() - startEvent.toEpochMilli()));
        } else if (created != null && completed != null && runStarted) {
            trace.cursorRunMs(Math.max(0, completed.toEpochMilli() - created.toEpochMilli()));
        }
        String resultSummary = readResultSummary(taskDir);
        if (resultSummary.isBlank()) {
            resultSummary = terminalSummary;
        }
        boolean interrupted = "INTERRUPTED".equals(taskStatus)
                || resultSummary.toLowerCase().contains("reconcile poll budget");
        if (interrupted) {
            trace.bridgeTerminalReason(
                    FrameworkRepairTerminalReasonClassifier.fromRepairTerminal(
                            delivery.agent.harness.AgentTaskStatus.INTERRUPTED,
                            resultSummary,
                            false));
        }
    }

    private static String readResultSummary(Path taskDir) {
        Path resultJson = taskDir.resolve("result.json");
        if (!Files.isRegularFile(resultJson)) {
            return "";
        }
        try {
            JsonNode r = JSON.readTree(Files.readString(resultJson));
            return text(r, "summary");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return "";
        }
        return n.get(field).asText("");
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}