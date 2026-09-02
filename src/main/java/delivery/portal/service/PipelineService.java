package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCounts;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    public enum Stage { GENERATE, AUTOMATE, EXECUTE }

    public enum StageStatus { PENDING, RUNNING, COMPLETED, SKIPPED, FAILED }

    private final DeliveryPortalProperties props;
    private final GeneratedWorkbookService workbooks;
    private final JobStarter jobStarter;
    private final JobStatusLookup jobStatus;
    private final ObjectMapper objectMapper;
    private final PipelineGenerator generator;
    private final Executor generateExecutor;
    /** Pipelines currently running Generate on a background thread. */
    private final Set<String> generateInFlight = ConcurrentHashMap.newKeySet();

    public PipelineService(
            DeliveryPortalProperties props,
            GeneratedWorkbookService workbooks,
            JobStarter jobStarter,
            JobStatusLookup jobStatus,
            ObjectMapper objectMapper
    ) {
        this(props, workbooks, jobStarter, jobStatus, objectMapper, null, Runnable::run);
    }

    public PipelineService(
            DeliveryPortalProperties props,
            GeneratedWorkbookService workbooks,
            JobStarter jobStarter,
            JobStatusLookup jobStatus,
            ObjectMapper objectMapper,
            PipelineGenerator generator
    ) {
        this(props, workbooks, jobStarter, jobStatus, objectMapper, generator, Runnable::run);
    }

    public PipelineService(
            DeliveryPortalProperties props,
            GeneratedWorkbookService workbooks,
            JobStarter jobStarter,
            JobStatusLookup jobStatus,
            ObjectMapper objectMapper,
            PipelineGenerator generator,
            Executor generateExecutor
    ) {
        this.props = props;
        this.workbooks = workbooks;
        this.jobStarter = jobStarter;
        this.jobStatus = jobStatus;
        this.objectMapper = objectMapper;
        this.generator = generator;
        this.generateExecutor = generateExecutor != null ? generateExecutor : Runnable::run;
    }

    public Map<String, Object> start(String projectId, Long ownerUserId, String stories) throws Exception {
        String pipelineId = "pipe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> pipeline = newPipelineRecord(pipelineId, projectId, ownerUserId);

        if (stories != null && !stories.isBlank()) {
            if (generator == null) {
                throw new IllegalStateException("PipelineGenerator not configured");
            }
            // Do NOT generate inside the HTTP request — Ollama can take many minutes (esp. with
            // second coverage pass) and the browser shows "Failed to fetch".
            pipeline.put("stories", stories.trim());
            setStage(pipeline, Stage.GENERATE, StageStatus.RUNNING, null,
                    "Generating with second coverage pass…");
            pipeline.put("runAutomate", false);
            pipeline.put("runExecute", false);
            pipeline.put("status", "RUNNING");
            pipeline.put("currentStage", Stage.GENERATE.name());
            pipeline.put("message", "Generate in progress (second coverage pass enabled)");
            save(pipeline);
            scheduleGenerate(pipelineId);
            // Sync executor (tests) finishes before return; async executor leaves GENERATE RUNNING.
            return snapshot(load(pipelineId));
        }

        if (workbooks.describe(projectId).isEmpty()) {
            throw new IllegalStateException("NO_GENERATED_WORKBOOK");
        }
        setStage(pipeline, Stage.GENERATE, StageStatus.SKIPPED, null, null);
        applyWorkbookRouting(pipeline, projectId);
        save(pipeline);
        return snapshot(pipeline);
    }

    public Map<String, Object> get(String pipelineId, Long ownerUserId) throws Exception {
        Map<String, Object> pipeline = load(pipelineId);
        assertOwner(pipeline, ownerUserId);
        return snapshot(pipeline);
    }

    /** Advance one pipeline step — polls linked job status when a stage is RUNNING. */
    public Map<String, Object> tick(String pipelineId) throws Exception {
        Map<String, Object> pipeline = load(pipelineId);
        ensureGenerateScheduled(pipeline);
        tickInternal(pipeline);
        save(pipeline);
        return snapshot(pipeline);
    }

    private void scheduleGenerate(String pipelineId) {
        if (!generateInFlight.add(pipelineId)) {
            return;
        }
        generateExecutor.execute(() -> {
            try {
                runGenerateStage(pipelineId);
            } finally {
                generateInFlight.remove(pipelineId);
            }
        });
    }

    private void ensureGenerateScheduled(Map<String, Object> pipeline) {
        String pipelineId = String.valueOf(pipeline.get("pipelineId"));
        Map<String, Object> generate = stageMap(pipeline, Stage.GENERATE);
        if (!StageStatus.RUNNING.name().equals(String.valueOf(generate.get("status")))) {
            return;
        }
        Object stories = pipeline.get("stories");
        if (stories == null || stories.toString().isBlank()) {
            return;
        }
        scheduleGenerate(pipelineId);
    }

    private void runGenerateStage(String pipelineId) {
        try {
            Map<String, Object> pipeline = load(pipelineId);
            Map<String, Object> generate = stageMap(pipeline, Stage.GENERATE);
            if (!StageStatus.RUNNING.name().equals(String.valueOf(generate.get("status")))) {
                return;
            }
            String projectId = String.valueOf(pipeline.get("projectId"));
            Long ownerUserId = ((Number) pipeline.get("ownerUserId")).longValue();
            String stories = String.valueOf(pipeline.get("stories"));
            generator.generate(projectId, ownerUserId, stories);
            pipeline = load(pipelineId);
            setStage(pipeline, Stage.GENERATE, StageStatus.COMPLETED, null, null);
            pipeline.remove("stories");
            applyWorkbookRouting(pipeline, projectId);
            pipeline.put("message", "Generate completed — continuing pipeline");
            save(pipeline);
        } catch (Exception e) {
            log.warn("Pipeline {} generate failed: {}", pipelineId, e.toString());
            try {
                Map<String, Object> pipeline = load(pipelineId);
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                setStage(pipeline, Stage.GENERATE, StageStatus.FAILED, null, msg);
                finish(pipeline, "FAILED", "GENERATE: " + msg);
                save(pipeline);
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private void applyWorkbookRouting(Map<String, Object> pipeline, String projectId) throws Exception {
        KeelPathCounts counts = countsFromWorkbook(projectId);
        validateRunnable(counts);

        boolean runAutomate = counts.automateRunnable() > 0;
        boolean runExecute = counts.executeRunnable() > 0;

        if (!runAutomate) {
            setStage(pipeline, Stage.AUTOMATE, StageStatus.SKIPPED, null, null);
        } else {
            setStage(pipeline, Stage.AUTOMATE, StageStatus.PENDING, null, null);
        }
        if (!runExecute) {
            setStage(pipeline, Stage.EXECUTE, StageStatus.SKIPPED, null, null);
        } else {
            setStage(pipeline, Stage.EXECUTE, StageStatus.PENDING, null, null);
        }

        pipeline.put("runAutomate", runAutomate);
        pipeline.put("runExecute", runExecute);
        pipeline.put("status", "RUNNING");
        pipeline.put("currentStage", runAutomate ? Stage.AUTOMATE.name() : Stage.EXECUTE.name());
    }

    @SuppressWarnings("unchecked")
    private void tickInternal(Map<String, Object> pipeline) {
        for (int i = 0; i < 4; i++) {
            if (!tickOnce(pipeline)) {
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean tickOnce(Map<String, Object> pipeline) {
        String overall = String.valueOf(pipeline.get("status"));
        if ("COMPLETED".equals(overall) || "FAILED".equals(overall)) {
            return false;
        }

        Map<String, Object> generate = stageMap(pipeline, Stage.GENERATE);
        String generateStatus = String.valueOf(generate.get("status"));
        if (StageStatus.RUNNING.name().equals(generateStatus)
                || StageStatus.PENDING.name().equals(generateStatus)) {
            // Wait for background Generate (or skip was already applied at start).
            return false;
        }

        boolean runAutomate = Boolean.TRUE.equals(pipeline.get("runAutomate"));
        boolean runExecute = Boolean.TRUE.equals(pipeline.get("runExecute"));

        Map<String, Object> automate = stageMap(pipeline, Stage.AUTOMATE);
        Map<String, Object> execute = stageMap(pipeline, Stage.EXECUTE);

        String automateStatus = String.valueOf(automate.get("status"));
        if (runAutomate) {
            if (StageStatus.PENDING.name().equals(automateStatus)) {
                startAutomateStage(pipeline);
                return true;
            }
            if (StageStatus.RUNNING.name().equals(automateStatus)) {
                if (pollJob(pipeline, Stage.AUTOMATE, automate)) {
                    if (!runExecute) {
                        finish(pipeline, "COMPLETED", null);
                    } else {
                        pipeline.put("currentStage", Stage.EXECUTE.name());
                    }
                    return true;
                }
                return false;
            }
        }

        if (runExecute) {
            String executeStatus = String.valueOf(execute.get("status"));
            if (StageStatus.PENDING.name().equals(executeStatus)) {
                if (runAutomate && !StageStatus.COMPLETED.name().equals(automateStatus)) {
                    return false;
                }
                startExecuteStage(pipeline);
                return true;
            }
            if (StageStatus.RUNNING.name().equals(executeStatus)) {
                if (pollJob(pipeline, Stage.EXECUTE, execute)) {
                    finish(pipeline, "COMPLETED", null);
                    return true;
                }
            }
        }
        return false;
    }

    private void startAutomateStage(Map<String, Object> pipeline) {
        String projectId = String.valueOf(pipeline.get("projectId"));
        Long ownerUserId = ((Number) pipeline.get("ownerUserId")).longValue();
        String jobId = jobStarter.startConvert(projectId, ownerUserId, true);
        setStage(pipeline, Stage.AUTOMATE, StageStatus.RUNNING, jobId, null);
        pipeline.put("currentStage", Stage.AUTOMATE.name());
    }

    private void startExecuteStage(Map<String, Object> pipeline) {
        String projectId = String.valueOf(pipeline.get("projectId"));
        Long ownerUserId = ((Number) pipeline.get("ownerUserId")).longValue();
        String jobId = jobStarter.startExecute(projectId, ownerUserId, true);
        setStage(pipeline, Stage.EXECUTE, StageStatus.RUNNING, jobId, null);
        pipeline.put("currentStage", Stage.EXECUTE.name());
    }

    private boolean pollJob(Map<String, Object> pipeline, Stage stage, Map<String, Object> stageMap) {
        String jobId = stageMap.get("jobId") == null ? null : String.valueOf(stageMap.get("jobId"));
        if (jobId == null || jobId.isBlank()) {
            return false;
        }
        var statusOpt = jobStatus.status(jobId);
        if (statusOpt.isEmpty()) {
            return false;
        }
        return switch (statusOpt.get()) {
            case COMPLETED, COMPLETED_WITH_BLOCK -> {
                setStage(pipeline, stage, StageStatus.COMPLETED, jobId, null);
                yield true;
            }
            case FAILED, CANCELLED -> {
                String msg = jobStatus.errorMessage(jobId).orElse(statusOpt.get().name());
                setStage(pipeline, stage, StageStatus.FAILED, jobId, msg);
                finish(pipeline, "FAILED", stage.name() + ": " + msg);
                yield false;
            }
            default -> false;
        };
    }

    private void finish(Map<String, Object> pipeline, String status, String message) {
        pipeline.put("status", status);
        pipeline.put("updatedAt", Instant.now().toString());
        if (message != null) {
            pipeline.put("message", message);
        }
    }

    private KeelPathCounts countsFromWorkbook(String projectId) throws Exception {
        if (workbooks.describe(projectId).isEmpty()) {
            throw new IllegalStateException("NO_GENERATED_WORKBOOK");
        }
        java.util.List<ManualTestCase> cases = new ExcelTcReader().read(workbooks.requireExcel(projectId));
        return KeelPathCounts.from(cases);
    }

    private void validateRunnable(KeelPathCounts counts) {
        if (counts.automateRunnable() == 0 && counts.executeRunnable() == 0) {
            throw new IllegalStateException("SURFACE_MISMATCH: no runnable Automate or Execute cases in workbook");
        }
    }

    private Map<String, Object> newPipelineRecord(String pipelineId, String projectId, Long ownerUserId) {
        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("pipelineId", pipelineId);
        pipeline.put("projectId", projectId);
        pipeline.put("ownerUserId", ownerUserId);
        pipeline.put("status", "RUNNING");
        pipeline.put("message", "");
        pipeline.put("createdAt", Instant.now().toString());
        pipeline.put("updatedAt", Instant.now().toString());
        pipeline.put("currentStage", Stage.GENERATE.name());

        Map<String, Object> stages = new LinkedHashMap<>();
        for (Stage stage : Stage.values()) {
            stages.put(stage.name(), newStageMap(StageStatus.PENDING));
        }
        pipeline.put("stages", stages);
        return pipeline;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stageMap(Map<String, Object> pipeline, Stage stage) {
        Map<String, Object> stages = (Map<String, Object>) pipeline.get("stages");
        return (Map<String, Object>) stages.get(stage.name());
    }

    private void setStage(Map<String, Object> pipeline, Stage stage, StageStatus status, String jobId, String message) {
        Map<String, Object> stageMap = stageMap(pipeline, stage);
        stageMap.put("status", status.name());
        if (jobId != null) {
            stageMap.put("jobId", jobId);
        }
        if (message != null) {
            stageMap.put("message", message);
        }
        pipeline.put("updatedAt", Instant.now().toString());
    }

    private static Map<String, Object> newStageMap(StageStatus status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.name());
        m.put("jobId", "");
        m.put("message", "");
        return m;
    }

    private Map<String, Object> snapshot(Map<String, Object> pipeline) {
        return new LinkedHashMap<>(pipeline);
    }

    private void assertOwner(Map<String, Object> pipeline, Long ownerUserId) {
        Long owner = ((Number) pipeline.get("ownerUserId")).longValue();
        if (!owner.equals(ownerUserId)) {
            throw new IllegalArgumentException("Unknown pipeline");
        }
    }

    private Path pipelineFile(String pipelineId) {
        return Path.of(props.getStoreRoot(), "pipelines", pipelineId + ".json");
    }

    private synchronized void save(Map<String, Object> pipeline) throws Exception {
        Path file = pipelineFile(String.valueOf(pipeline.get("pipelineId")));
        Files.createDirectories(file.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), pipeline);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(String pipelineId) throws Exception {
        Path file = pipelineFile(pipelineId);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Unknown pipeline");
        }
        return objectMapper.readValue(file.toFile(), Map.class);
    }

    /** Production executor for long Ollama generates — kept as a bean-friendly factory. */
    public static Executor defaultGenerateExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "pipeline-generate");
            t.setDaemon(true);
            return t;
        });
    }
}
