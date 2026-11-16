package delivery.agent.validation.automate;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.validation.contract.FrameworkExpectedInventory;
import delivery.agent.validation.contract.ValidationReport;
import delivery.agent.validation.contract.ValidationSeverity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persists compile-on live qualification evidence without being blocked by TestNG assertions. */
final class FrameworkCompileOnQualificationEvidenceWriter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private FrameworkCompileOnQualificationEvidenceWriter() {
    }

    static void write(
            Path work,
            String scenarioId,
            String parentJob,
            AutomateFrameworkAcceptanceOutcome outcome,
            ValidationReport preRepair,
            FrameworkExpectedInventory inventory,
            boolean compilePassed,
            Path compileFail,
            Path project,
            Path repairCandidate) throws Exception {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("phase", "6.8-compile-on");
        evidence.put("scenario", scenarioId);
        evidence.put("workDir", work.toAbsolutePath().normalize().toString());
        evidence.put("accepted", outcome.accepted());
        evidence.put("promoted", outcome.accepted());
        evidence.put("gateStatus", outcome.status().name());
        evidence.put("initialMandatoryRuleIds", preRepair.findings().stream()
                .filter(f -> f.severity() == ValidationSeverity.MANDATORY)
                .map(f -> f.ruleId())
                .distinct()
                .sorted()
                .toList());
        evidence.put("compilePassed", compilePassed);
        evidence.put("compileCheckEnabled", true);
        if (Files.isRegularFile(compileFail)) {
            evidence.put("compileFailureLog", compileFail.toAbsolutePath().toString());
        }
        Path recon = work.resolve("framework-acceptance/reconciliation-manifest.json");
        if (Files.isRegularFile(recon)) {
            evidence.put("reconciliation", JSON.readTree(Files.readString(recon)));
        }
        Path operational = FrameworkRepairOperationalReportWriter.reportPath(work);
        if (Files.isRegularFile(operational)) {
            evidence.put("operationalReport", operational.toAbsolutePath().toString());
            evidence.put("operational", JSON.readTree(Files.readString(operational)));
        }
        Path revalidateReport = work.resolve("agent-runs")
                .resolve(parentJob + "_acceptance-revalidate")
                .resolve("contract-validation-report.json");
        if (Files.isRegularFile(revalidateReport)) {
            evidence.put("revalidationReport", revalidateReport.toAbsolutePath().toString());
            var rev = JSON.readTree(Files.readString(revalidateReport));
            evidence.put("revalidationPassed", rev.path("passed").asBoolean(false));
            var mandatory = new java.util.ArrayList<String>();
            rev.path("findings").forEach(f -> {
                if ("MANDATORY".equals(f.path("severity").asText())) {
                    mandatory.add(f.path("ruleId").asText());
                }
            });
            evidence.put("revalidationMandatoryRuleIds", mandatory.stream().distinct().sorted().toList());
        }
        String taskId = resolveTaskId(work, parentJob, outcome.taskId());
        evidence.put("taskId", taskId);
        AgentRunStore store = new AgentRunStore();
        if (!taskId.isBlank()) {
            store.loadTask(work, parentJob, taskId).ifPresent(task -> {
                evidence.put("agentId", task.agentId());
                evidence.put("agentRunId", task.agentRunId());
                evidence.put("cursorTaskStatus", task.taskStatus() != null ? task.taskStatus().name() : "");
            });
        }
        var archRepair = FrameworkRepairArchitectureAssessor.assess(repairCandidate, inventory);
        var archPublished = outcome.accepted()
                ? FrameworkRepairArchitectureAssessor.assess(project, inventory)
                : archRepair;
        evidence.put("architectureRepairCandidate", mapArch(archRepair));
        evidence.put("architecturePublished", mapArch(archPublished));
        evidence.put("failureClass", classifyFailure(outcome, compilePassed, archPublished));
        String attempt = System.getProperty("keel.agent.qualification.attempt");
        if (attempt != null && !attempt.isBlank()) {
            evidence.put("qualificationAttempt", attempt);
            evidence.put("phase", "6.12-compile-on");
            enrichAttemptMetadata(evidence, work, parentJob, taskId);
            Path destAttempt = Path.of(
                    "docs/handoff/phase6-12-compile-on-" + scenarioId + "-" + attempt + ".json");
            Files.createDirectories(destAttempt.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(destAttempt.toFile(), evidence);
        }
        boolean versionedOnly = Boolean.parseBoolean(
                System.getProperty("keel.agent.qualification.evidence.versionedOnly", "false"));
        if (!versionedOnly) {
            Path dest = Path.of("docs/handoff/phase6-8-compile-on-" + scenarioId + ".json");
            Files.createDirectories(dest.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(dest.toFile(), evidence);
        }
    }

    private static void enrichAttemptMetadata(
            Map<String, Object> evidence, Path work, String parentJob, String taskId) throws Exception {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        Path taskDir = work.resolve("agent-runs").resolve(parentJob).resolve(taskId);
        Path taskJson = taskDir.resolve("task.json");
        if (Files.isRegularFile(taskJson)) {
            var task = JSON.readTree(Files.readString(taskJson));
            if (task.has("budget")) {
                evidence.put("taskMaxRuntimeSeconds", task.path("budget").path("maxRuntime").asDouble());
            }
            evidence.put("taskCreatedAt", task.path("createdAt").asText(""));
            evidence.put("taskStartedAt", task.path("startedAt").asText(""));
            evidence.put("taskCompletedAt", task.path("completedAt").asText(""));
        }
        Path launch = taskDir.resolve("launch-state.json");
        if (Files.isRegularFile(launch)) {
            evidence.put("launchState", JSON.readTree(Files.readString(launch)));
        }
        Path events = taskDir.resolve("events.jsonl");
        if (Files.isRegularFile(events)) {
            var lines = Files.readAllLines(events);
            evidence.put("taskEventCount", lines.size());
            evidence.put("taskEventsTail", lines.size() > 8 ? lines.subList(lines.size() - 8, lines.size()) : lines);
        }
        evidence.put("liveLogHint", "docs/handoff/phase6-12-deferred-cursor-live.log");
    }

    private static Map<String, Object> mapArch(FrameworkRepairArchitectureAssessor.ArchitectureQualityReport arch) {
        return Map.of(
                "functionallyComplete", arch.functionallyComplete(),
                "findings", arch.findings().stream()
                        .map(f -> Map.of("code", f.code(), "message", f.message(), "functionalGap", f.functionalGap()))
                        .toList());
    }

    static String classifyFailure(
            AutomateFrameworkAcceptanceOutcome outcome,
            boolean compilePassed,
            FrameworkRepairArchitectureAssessor.ArchitectureQualityReport arch) {
        if (outcome.accepted() && compilePassed && arch.functionallyComplete()) {
            return "SUCCESS";
        }
        if (outcome.status() == AutomateFrameworkAcceptanceStatus.RECONCILIATION_FAILED) {
            return "RECONCILIATION_FAILURE";
        }
        if (outcome.status() == AutomateFrameworkAcceptanceStatus.COMPILE_FAILED || !compilePassed) {
            return "COMPILE_FAILURE";
        }
        if (outcome.status() == AutomateFrameworkAcceptanceStatus.REVALIDATION_FAILED) {
            return "REPAIR_QUALITY_OR_VALIDATION";
        }
        if (outcome.accepted() && !arch.functionallyComplete()) {
            return "ARCHITECTURE_QUALITY_FAILURE";
        }
        return "OTHER_" + outcome.status().name();
    }

    private static String resolveTaskId(Path work, String parentJob, String fromOutcome) {
        if (fromOutcome != null && !fromOutcome.isBlank()) {
            return fromOutcome;
        }
        try {
            Path tasksRoot = work.resolve("agent-runs").resolve(parentJob);
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
}