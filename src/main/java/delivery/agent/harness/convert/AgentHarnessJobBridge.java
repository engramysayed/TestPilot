package delivery.agent.harness.convert;

import delivery.agent.harness.AgentArtifactFingerprints;
import delivery.agent.harness.AgentEvent;
import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentPolicy;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.harness.AgentToolId;
import delivery.agent.harness.AgentToolRegistry;
import delivery.agent.harness.AgentWorkspacePaths;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.agent.harness.ValidationReportWriter;
import delivery.job.ArtifactIntegrityException;
import delivery.agent.harness.AgentHarnessProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Convert pre-publish advisory validation (Phase 3).
 */
public final class AgentHarnessJobBridge {
    private static final Logger log = LoggerFactory.getLogger(AgentHarnessJobBridge.class);

    private final AgentHarnessProperties properties;
    private final AgentHarnessCoordinator coordinator;
    private final AgentRunStore store;
    private final AgentStageIndexStore stageIndex;

    public AgentHarnessJobBridge(
            AgentHarnessProperties properties,
            AgentHarnessCoordinator coordinator,
            AgentRunStore store,
            AgentStageIndexStore stageIndex) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
        this.coordinator = coordinator;
        this.store = store == null ? new AgentRunStore() : store;
        this.stageIndex = stageIndex == null ? new AgentStageIndexStore() : stageIndex;
    }

    /**
     * Runs advisory validation before project publish lock. Returns zip fingerprint for locked re-check.
     * Integrity failures throw {@link ArtifactIntegrityException}; advisory failures do not.
     */
    public String validateBeforePublish(
            Path projectRoot,
            String projectId,
            String parentJobId,
            Path zipFile,
            Duration harnessBudget,
            java.util.function.BooleanSupplier cancelCheck)
            throws Exception {
        if (!properties.isEnabled() || !properties.isFrameworkValidationEnabled()) {
            return "";
        }
        String fingerprint = AgentArtifactFingerprints.sha256Hex(zipFile);
        String stageKey = AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH;
        String taskId = AgentArtifactFingerprints.deterministicTaskId(parentJobId, stageKey, fingerprint);

        ValidationContext ctx = resolveValidation(projectRoot, parentJobId, projectId, fingerprint, stageKey, taskId);
        if (ctx.skipReason() != null) {
            writeSkippedReport(projectRoot, parentJobId, ctx.taskId(), fingerprint, ctx.skipReason());
            return fingerprint;
        }

        Path taskDir = store.taskDir(projectRoot, parentJobId, ctx.taskId());
        Path workspace = AgentWorkspacePaths.isolatedWorkspace(taskDir);
        try {
            PackageZipExtractor.extract(zipFile, workspace);
        } catch (ArtifactIntegrityException e) {
            throw e;
        } catch (IOException e) {
            throw new ArtifactIntegrityException("zip extract failed: " + e.getMessage());
        }
        copyFrameworkRulesDoc(projectRoot, workspace);

        if (cancelCheck != null && cancelCheck.getAsBoolean()) {
            writeSkippedReport(projectRoot, parentJobId, ctx.taskId(), fingerprint, "JOB_CANCELLED");
            return fingerprint;
        }

        try {
            AgentRunResult result = runHarness(
                    projectRoot, parentJobId, projectId, ctx.taskId(), workspace, fingerprint, stageKey, harnessBudget);
            Map<String, Object> report = ValidationReportWriter.baseReport(fingerprint);
            report.put("validationStatus", mapValidationStatus(result.terminalStatus()));
            report.put("validated", result.terminalStatus() == AgentTaskStatus.COMPLETED);
            report.put("integrityStatus", "OK");
            report.put("summary", result.summary());
            ValidationReportWriter.persistFailOpen(projectRoot, parentJobId, taskDir, ctx.taskId(), report, store);
            promoteDeferredIfActiveTerminal(projectRoot, parentJobId, ctx.taskId());
        } catch (AgentHarnessException e) {
            log.warn("advisory harness validation failed for {}: {}", parentJobId, e.getMessage());
            Map<String, Object> report = ValidationReportWriter.baseReport(fingerprint);
            report.put("validationStatus", advisoryStatusForException(e));
            report.put("integrityStatus", "OK");
            report.put("reason", e.getMessage());
            ValidationReportWriter.persistFailOpen(projectRoot, parentJobId, taskDir, ctx.taskId(), report, store);
        }
        return fingerprint;
    }

    private record ValidationContext(String taskId, boolean runHarness, String skipReason) {
    }

    private ValidationContext resolveValidation(
            Path projectRoot,
            String parentJobId,
            String projectId,
            String fingerprint,
            String stageKey,
            String taskId)
            throws IOException, Exception {
        final ValidationContext[] out = new ValidationContext[1];
        stageIndex.withLock(projectRoot, parentJobId, () -> {
            AgentStageIndexStore.IndexFile index = stageIndex.read(projectRoot, parentJobId);
            AgentStageIndexStore.StageEntry entry = index.stages.computeIfAbsent(
                    AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, k -> new AgentStageIndexStore.StageEntry());

            Optional<AgentTask> existing = store.loadTask(projectRoot, parentJobId, taskId);
            if (existing.isPresent()) {
                AgentTask t = existing.get();
                if (t.taskStatus() == AgentTaskStatus.COMPLETED && store.loadResult(projectRoot, parentJobId, taskId).isPresent()) {
                    out[0] = new ValidationContext(taskId, false, "CACHED_RESULT");
                    return null;
                }
                ensureActiveEntry(entry, fingerprint, taskId);
                stageIndex.write(projectRoot, parentJobId, index);
                out[0] = new ValidationContext(taskId, true, null);
                return null;
            }

            if (entry.active == null) {
                entry.active = new AgentStageIndexStore.TaskRef(fingerprint, taskId);
                ensureTaskStub(projectRoot, parentJobId, projectId, taskId, fingerprint, stageKey);
                stageIndex.write(projectRoot, parentJobId, index);
                out[0] = new ValidationContext(taskId, true, null);
                return null;
            }

            if (fingerprint.equals(entry.active.inputFingerprint())) {
                out[0] = new ValidationContext(entry.active.taskId(), true, null);
                return null;
            }

            AgentTask activeTask = store.loadTask(projectRoot, parentJobId, entry.active.taskId()).orElse(null);
            boolean activeNonTerminal = activeTask != null && !stageIndex.isTerminal(activeTask.taskStatus());

            if (activeNonTerminal) {
                if (entry.deferred != null && !fingerprint.equals(entry.deferred.inputFingerprint())) {
                    recordSkip(projectRoot, parentJobId, entry.deferred.taskId(), "DEFERRED_EVICTED");
                }
                String deferredId = AgentArtifactFingerprints.deterministicTaskId(
                        parentJobId, stageKey, fingerprint);
                entry.deferred = new AgentStageIndexStore.TaskRef(fingerprint, deferredId);
                ensureTaskStub(projectRoot, parentJobId, projectId, deferredId, fingerprint, stageKey);
                recordSkip(projectRoot, parentJobId, deferredId, "ACTIVE_CONFLICT");
                stageIndex.write(projectRoot, parentJobId, index);
                out[0] = new ValidationContext(deferredId, false, "ACTIVE_CONFLICT");
                return null;
            }

            if (entry.deferred != null && fingerprint.equals(entry.deferred.inputFingerprint())) {
                entry.active = entry.deferred;
                entry.deferred = null;
            } else {
                entry.active = new AgentStageIndexStore.TaskRef(fingerprint, taskId);
            }
            ensureTaskStub(projectRoot, parentJobId, projectId, entry.active.taskId(), fingerprint, stageKey);
            stageIndex.write(projectRoot, parentJobId, index);
            out[0] = new ValidationContext(entry.active.taskId(), true, null);
            return null;
        });
        return out[0];
    }

    private static void ensureActiveEntry(
            AgentStageIndexStore.StageEntry entry, String fingerprint, String taskId) {
        if (entry.active == null) {
            entry.active = new AgentStageIndexStore.TaskRef(fingerprint, taskId);
        }
    }

    private void ensureTaskStub(
            Path projectRoot,
            String parentJobId,
            String projectId,
            String taskId,
            String fingerprint,
            String stageKey)
            throws IOException {
        if (store.loadTask(projectRoot, parentJobId, taskId).isPresent()) {
            return;
        }
        Set<AgentToolId> readOnly = EnumSet.copyOf(
                AgentToolRegistry.defaultsFor(AgentTaskType.EXECUTION_FAILURE_INVESTIGATION_V1));
        AgentTask stub = new AgentTask(
                taskId, parentJobId, projectId, AgentTaskType.FRAMEWORK_VALIDATION_V1,
                AgentTaskStatus.PENDING, "Advisory framework validation", List.of(), "",
                Instant.now(), null, null, null, null, 1,
                new AgentPolicy(Duration.ofMinutes(15), 1, readOnly, List.of(), false, false),
                "", properties.isCursorEnabled() ? AgentProviderId.CURSOR : AgentProviderId.FAKE,
                fingerprint, stageKey, 3);
        store.saveTask(projectRoot, stub);
        store.appendEvent(projectRoot, parentJobId, taskId,
                new AgentEvent(Instant.now(), "TASK_CREATED", "stub for stage index", Map.of()));
    }

    private AgentRunResult runHarness(
            Path projectRoot,
            String parentJobId,
            String projectId,
            String taskId,
            Path workspace,
            String fingerprint,
            String stageKey,
            Duration harnessBudget)
            throws IOException {
        Optional<AgentTask> loaded = store.loadTask(projectRoot, parentJobId, taskId);
        AgentTask task = loaded.orElseThrow(() -> new AgentHarnessException("task missing: " + taskId));
        Set<AgentToolId> readOnly = EnumSet.copyOf(
                AgentToolRegistry.defaultsFor(AgentTaskType.EXECUTION_FAILURE_INVESTIGATION_V1));
        AgentPolicy budget = new AgentPolicy(
                harnessBudget == null ? task.budget().maxRuntime() : harnessBudget,
                task.budget().maxAttempts(),
                readOnly,
                List.of(workspace.toAbsolutePath().normalize().toString()),
                false,
                false);
        task = task.withPolicy(budget).withWorkspacePath(workspace.toString());
        store.saveTask(projectRoot, task);
        return coordinator.execute(projectRoot, parentJobId, taskId);
    }

    private void promoteDeferredIfActiveTerminal(Path projectRoot, String parentJobId, String taskId)
            throws Exception {
        stageIndex.withLock(projectRoot, parentJobId, () -> {
            AgentStageIndexStore.IndexFile index = stageIndex.read(projectRoot, parentJobId);
            AgentStageIndexStore.StageEntry entry = index.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH);
            if (entry == null || entry.active == null || !entry.active.taskId().equals(taskId)) {
                return null;
            }
            AgentTask active = store.loadTask(projectRoot, parentJobId, taskId).orElse(null);
            if (active == null || !stageIndex.isTerminal(active.taskStatus())) {
                return null;
            }
            if (entry.deferred != null) {
                entry.active = entry.deferred;
                entry.deferred = null;
            }
            stageIndex.write(projectRoot, parentJobId, index);
            return null;
        });
    }

    private void recordSkip(Path projectRoot, String parentJobId, String taskId, String reason) throws IOException {
        store.appendEvent(projectRoot, parentJobId, taskId,
                new AgentEvent(Instant.now(), "VALIDATION_SKIPPED", reason, Map.of("validated", false)));
        writeSkippedReport(projectRoot, parentJobId, taskId,
                store.loadTask(projectRoot, parentJobId, taskId).map(AgentTask::inputFingerprint).orElse(""),
                reason);
    }

    private void writeSkippedReport(
            Path projectRoot, String parentJobId, String taskId, String fingerprint, String reason) {
        Map<String, Object> report = ValidationReportWriter.baseReport(fingerprint);
        report.put("validationStatus", "SKIPPED");
        report.put("reason", reason);
        Path taskDir = store.taskDir(projectRoot, parentJobId, taskId);
        ValidationReportWriter.persistFailOpen(projectRoot, parentJobId, taskDir, taskId, report, store);
    }

    private static String mapValidationStatus(AgentTaskStatus status) {
        return switch (status) {
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case TIMED_OUT -> "INCOMPLETE";
            case INTERRUPTED -> "INCOMPLETE";
            case CANCELLED -> "SKIPPED";
            default -> "INCOMPLETE";
        };
    }

    private static String advisoryStatusForException(AgentHarnessException e) {
        if ("INTERRUPTED".equals(e.getMessage())) {
            return "INCOMPLETE";
        }
        if ("delivery.agent-harness.enabled=false".equals(e.getMessage())) {
            return "SKIPPED";
        }
        return "UNAVAILABLE";
    }

    /**
     * Copy docs/FRAMEWORK_RULES.md into the agent's workspace at .keel/FRAMEWORK_RULES.md so the
     * agent can read the canonical rules without running outside its isolated path. Fail-open:
     * a missing doc never blocks validation.
     */
    private static void copyFrameworkRulesDoc(Path projectRoot, Path workspace) {
        if (projectRoot == null || workspace == null) {
            return;
        }
        Path source = projectRoot.resolve("docs").resolve("FRAMEWORK_RULES.md");
        if (!java.nio.file.Files.isRegularFile(source)) {
            return;
        }
        Path target = workspace.resolve(".keel").resolve("FRAMEWORK_RULES.md");
        try {
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("failed to copy FRAMEWORK_RULES.md into agent workspace: {}", e.getMessage());
        }
    }
}
