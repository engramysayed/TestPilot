package delivery.portal.service;

import delivery.authoring.LocalLlmClient;
import delivery.excel.GenerateAuthoringRepair;
import delivery.excel.GenerateQualityGate;
import delivery.excel.GeneratedTcCsvParser;
import delivery.excel.GeneratedTcJsonParser;
import delivery.excel.KeelPathCounts;
import delivery.excel.ManualTestCase;
import delivery.job.JobCancelSupport;
import delivery.job.JobCancelledException;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.ProjectRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

@Service
public class TcGenerateService {
    private static final Logger log = LogManager.getLogger(TcGenerateService.class);
    private static final String COVERAGE_MARKER = "---KEEL_COVERAGE---";

    private final DeliveryPortalProperties props;
    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;
    private final GenerateModelService models;
    private final TcImportService tcImport;

    public TcGenerateService(
            DeliveryPortalProperties props,
            PortalStore store,
            GeneratedWorkbookService workbooks,
            GenerateModelService models,
            TcImportService tcImport
    ) {
        this.props = props;
        this.store = store;
        this.workbooks = workbooks;
        this.models = models;
        this.tcImport = tcImport;
    }

    public Map<String, Object> generate(
            String projectId,
            Long ownerUserId,
            String stories,
            boolean reviewPass,
            String model
    ) throws Exception {
        if (!props.isGenerateEnabled()) {
            throw new IllegalStateException("GENERATE_DISABLED");
        }
        if (stories == null || stories.isBlank()) {
            throw new IllegalArgumentException("Stories text is required");
        }
        ProjectRecord project = store.getOwnedProject(projectId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project"));

        StoryGenerateResult generated = generateStory(project, stories.trim(), reviewPass, null, model);
        workbooks.saveFromCases(projectId, generated.cases(), "GENERATE_SYNC", projectId, generated.model());
        if (generated.coverageNotes() != null && !generated.coverageNotes().isBlank()) {
            workbooks.updateCoverageNotes(projectId, generated.coverageNotes());
        }
        return toGeneratePayload(projectId, generated);
    }

    public Map<String, Object> compare(
            String projectId,
            Long ownerUserId,
            String stories,
            String modelA,
            String modelB
    ) throws Exception {
        if (!props.isGenerateEnabled()) {
            throw new IllegalStateException("GENERATE_DISABLED");
        }
        if (stories == null || stories.isBlank()) {
            throw new IllegalArgumentException("Stories text is required");
        }
        ProjectRecord project = store.getOwnedProject(projectId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project"));
        return compare(project, stories.trim(), modelA, modelB);
    }

    public Map<String, Object> compare(
            ProjectRecord project,
            String stories,
            String modelA,
            String modelB
    ) throws Exception {
        StoryGenerateResult resultA = generateStory(project, stories, false, null, modelA);
        StoryGenerateResult resultB = generateStory(project, stories, false, null, modelB);
        return buildCompareResult(project.getProjectId(), resultA, resultB);
    }

    public Map<String, Object> buildCompareResult(
            String projectId,
            StoryGenerateResult resultA,
            StoryGenerateResult resultB
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("projectId", projectId);
        response.put("modelA", toSidePayload(resultA));
        response.put("modelB", toSidePayload(resultB));
        return response;
    }

    public Map<String, Object> saveCompared(
            String projectId,
            Long ownerUserId,
            String csv,
            String model,
            String coverageNotes
    ) throws Exception {
        if (!props.isGenerateEnabled()) {
            throw new IllegalStateException("GENERATE_DISABLED");
        }
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("csv field is required");
        }
        List<ManualTestCase> cases = GeneratedTcCsvParser.parse(csv);
        Map<String, Object> result = tcImport.importCases(
                projectId, ownerUserId, cases, "GENERATE_COMPARE", model);
        result.put("coverageNotes", coverageNotes == null ? "" : coverageNotes);
        return result;
    }

    private Map<String, Object> toGeneratePayload(String projectId, StoryGenerateResult generated) {
        String rawCombined = GeneratedTcCsvParser.toCsv(generated.cases());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("model", generated.model());
        result.put("rows", toRowMaps(generated.cases()));
        result.put("coverageNotes", generated.coverageNotes());
        result.put("csv", rawCombined);
        result.put("counts", GeneratedTcCsvParser.countByKeelPath(generated.cases()));
        result.put("keelPathCounts", KeelPathCounts.from(generated.cases()).toMap());
        return result;
    }

    private static Map<String, Object> toSidePayload(StoryGenerateResult generated) {
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("model", generated.model());
        side.put("rows", toRowMaps(generated.cases()));
        side.put("coverageNotes", generated.coverageNotes());
        side.put("csv", GeneratedTcCsvParser.toCsv(generated.cases()));
        side.put("counts", GeneratedTcCsvParser.countByKeelPath(generated.cases()));
        side.put("keelPathCounts", KeelPathCounts.from(generated.cases()).toMap());
        return side;
    }

    public record StoryGenerateResult(List<ManualTestCase> cases, String coverageNotes, String model) {
    }

    public List<ManualTestCase> generateCasesForStory(
            ProjectRecord project,
            String stories,
            boolean reviewPass,
            String storyLabel,
            String model
    ) throws Exception {
        return generateCasesForStory(project, stories, reviewPass, storyLabel, model, null);
    }

    public List<ManualTestCase> generateCasesForStory(
            ProjectRecord project,
            String stories,
            boolean reviewPass,
            String storyLabel,
            String model,
            BooleanSupplier cancelCheck
    ) throws Exception {
        return generateStory(project, stories, reviewPass, storyLabel, model, cancelCheck).cases();
    }

    public StoryGenerateResult generateStory(
            ProjectRecord project,
            String stories,
            boolean reviewPass,
            String storyLabel,
            String model
    ) throws Exception {
        return generateStory(project, stories, reviewPass, storyLabel, model, null);
    }

    public StoryGenerateResult generateStory(
            ProjectRecord project,
            String stories,
            boolean reviewPass,
            String storyLabel,
            String model,
            BooleanSupplier cancelCheck
    ) throws Exception {
        String resolvedModel = models.resolve(model);
        String system = buildSystemPrompt();
        String user = buildUserMessage(project, stories, storyLabel);
        LocalLlmClient.ChatOutcome first = invokeOllamaDetailed(system, user, resolvedModel, cancelCheck);
        String raw = first.contentOrEmpty();
        if (first.truncated() || looksLikeEmptyOrBrokenBatch(raw)) {
            log.warn("Generate output truncated or empty testCases — retrying compact batch");
            String compactUser = user + "\n\nIMPORTANT: Previous model output was truncated or had an empty "
                    + "testCases array (Ollama done_reason=length). Emit 1–5 COMPLETE testCases only. "
                    + "Keep steps concise. Every JSON string must be closed. Prefer one happy-path case "
                    + "covering the full acceptance flow.";
            first = invokeOllamaDetailed(system, compactUser, resolvedModel, cancelCheck);
            raw = first.contentOrEmpty();
            if (first.truncated()) {
                throw new IllegalStateException(
                        "OLLAMA_TRUNCATED: model hit the output token limit twice. "
                                + "Raise delivery.generate-num-predict or use a larger generate model "
                                + "(e.g. qwen2.5:latest).");
            }
        }
        if (reviewPass) {
            String reviewUser = buildReviewUserMessage(stories, raw);
            raw = invokeOllamaDetailed(system, reviewUser, resolvedModel, cancelCheck).contentOrEmpty();
        }
        ParsedLlmOutput parsed;
        try {
            parsed = parseLlmOutput(raw);
        } catch (IllegalArgumentException parseError) {
            log.warn("Generate parse failed — retrying once: {}", parseError.getMessage());
            String retryUser = user + "\n\nPrevious JSON was unusable (" + parseError.getMessage()
                    + "). Reply with ONE complete JSON object only. "
                    + "testCases must be a non-empty array. Keep to 1–5 cases.";
            raw = invokeOllamaDetailed(system, retryUser, resolvedModel, cancelCheck).contentOrEmpty();
            parsed = parseLlmOutput(raw);
        }
        List<ManualTestCase> cases = GeneratedTcScopeFilter.apply(stories, parsed.cases());
        List<String> gateErrors = GenerateQualityGate.validate(cases, project.getBaseUrl());
        if (!gateErrors.isEmpty()) {
            cases = applyAuthoringRepair(cases, project.getBaseUrl(), storyLabel);
            gateErrors = GenerateQualityGate.validate(cases, project.getBaseUrl());
        }
        if (!gateErrors.isEmpty()) {
            String retryUser = buildQualityRetryUserMessage(project, stories, storyLabel, raw, gateErrors);
            raw = invokeOllamaDetailed(system, retryUser, resolvedModel, cancelCheck).contentOrEmpty();
            parsed = parseLlmOutput(raw);
            cases = GeneratedTcScopeFilter.apply(stories, parsed.cases());
            cases = applyAuthoringRepair(cases, project.getBaseUrl(), storyLabel);
            gateErrors = GenerateQualityGate.validate(cases, project.getBaseUrl());
            if (!gateErrors.isEmpty()) {
                throw GenerateQualityGate.failureException(gateErrors);
            }
        }
        return new StoryGenerateResult(cases, parsed.coverageNotes(), resolvedModel);
    }

    static boolean looksLikeEmptyOrBrokenBatch(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        try {
            GeneratedTcJsonParser.parse(raw);
            return false;
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            return msg.contains("non-empty testCases")
                    || msg.contains("JSON response is empty")
                    || msg.contains("Not valid JSON");
        }
    }

    private List<ManualTestCase> applyAuthoringRepair(
            List<ManualTestCase> cases, String baseUrl, String storyLabel) {
        List<ManualTestCase> repaired = GenerateAuthoringRepair.repair(cases);
        int before = GenerateQualityGate.validate(cases, baseUrl).size();
        int after = GenerateQualityGate.validate(repaired, baseUrl).size();
        if (after < before) {
            String label = (storyLabel == null || storyLabel.isBlank()) ? "story" : storyLabel;
            log.info("Repaired {} generate authoring issue(s) for {}", before - after, label);
            return repaired;
        }
        return cases;
    }

    static String ollamaTimeoutMessage(int timeoutSec) {
        return "OLLAMA_TIMEOUT after " + timeoutSec + "s";
    }

    private LocalLlmClient.ChatOutcome invokeOllamaDetailed(
            String system, String user, String model, BooleanSupplier cancelCheck
    ) throws Exception {
        JobCancelSupport.checkCancelled(cancelCheck);
        if (cancelCheck == null) {
            return callOllamaDetailed(system, user, model);
        }
        return callOllamaDetailed(system, user, model, cancelCheck);
    }

    private String invokeOllama(String system, String user, String model, BooleanSupplier cancelCheck)
            throws Exception {
        return invokeOllamaDetailed(system, user, model, cancelCheck).contentOrEmpty();
    }

    String callOllama(String system, String user, String model) throws Exception {
        return callOllamaDetailed(system, user, model).contentOrEmpty();
    }

    String callOllama(String system, String user, String model, BooleanSupplier cancelCheck) throws Exception {
        return callOllamaDetailed(system, user, model, cancelCheck).contentOrEmpty();
    }

    LocalLlmClient.ChatOutcome callOllamaDetailed(String system, String user, String model) throws Exception {
        return callOllamaDetailed(system, user, model, null);
    }

    LocalLlmClient.ChatOutcome callOllamaDetailed(
            String system, String user, String model, BooleanSupplier cancelCheck
    ) throws Exception {
        int timeoutSec = Math.max(30, props.getGenerateTimeoutSeconds());
        int numPredict = Math.max(
                LocalLlmClient.DEFAULT_GENERATE_NUM_PREDICT,
                props.getGenerateNumPredict());
        LocalLlmClient client = new LocalLlmClient(
                props.getLlmBaseUrl(),
                model,
                Duration.ofSeconds(timeoutSec + 30L),
                numPredict);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<LocalLlmClient.ChatOutcome> task =
                    () -> client.completeChatDetailed(system, user, true, numPredict);
            Future<LocalLlmClient.ChatOutcome> future = executor.submit(task);
            try {
                return JobCancelSupport.awaitOrCancel(
                        future, TimeUnit.SECONDS.toMillis(timeoutSec), cancelCheck);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new IllegalStateException(ollamaTimeoutMessage(timeoutSec));
            } catch (JobCancelledException e) {
                future.cancel(true);
                throw e;
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                if (cancelCheck != null && cancelCheck.getAsBoolean()) {
                    throw new JobCancelledException();
                }
                throw e;
            } catch (java.util.concurrent.ExecutionException e) {
                if (cancelCheck != null && cancelCheck.getAsBoolean()) {
                    throw new JobCancelledException();
                }
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) {
                    throw new IllegalStateException("OLLAMA_UNAVAILABLE", io);
                }
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw new IllegalStateException("OLLAMA_ERROR", cause);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<Map<String, Object>> toRowMaps(List<ManualTestCase> cases) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ManualTestCase tc : cases) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tcId", tc.tcId());
            row.put("title", tc.title());
            row.put("preconditions", tc.preconditions());
            row.put("steps", tc.steps());
            row.put("expectedResult", tc.expectedResult());
            row.put("priority", tc.priority());
            row.put("tags", tc.tags());
            row.put("visualAssertion", tc.visualAssertion());
            row.put("testData", tc.testData());
            row.put("keelPath", tc.keelPath());
            row.put("callBefore", tc.callBefore());
            rows.add(row);
        }
        return rows;
    }

    private String buildSystemPrompt() throws IOException {
        return loadPrompt("static/prompts/keel-tc-generate-from-stories-to-json.txt")
                + "\n\n---\n\n"
                + loadPrompt("static/prompts/keel-capability-manifest.txt")
                + "\n\n---\n\n"
                + loadPrompt("static/prompts/keel-testing-techniques.txt");
    }

    static ParsedLlmOutput parseLlmOutput(String raw) {
        IllegalArgumentException jsonError;
        try {
            GeneratedTcJsonParser.ParseResult json = GeneratedTcJsonParser.parse(raw);
            return new ParsedLlmOutput(json.cases(), json.coverageNotes());
        } catch (IllegalArgumentException e) {
            jsonError = e;
        }
        try {
            return new ParsedLlmOutput(
                    GeneratedTcCsvParser.parse(raw),
                    GeneratedTcCsvParser.extractCoverageNotes(raw));
        } catch (IllegalArgumentException csvError) {
            if (!looksLikeCsv(raw)) {
                throw jsonError;
            }
            throw csvError;
        }
    }

    static boolean looksLikeCsv(String raw) {
        String csv = GeneratedTcCsvParser.extractCsvBlock(raw);
        if (csv == null || csv.isBlank()) {
            return false;
        }
        int nl = csv.indexOf('\n');
        String header = (nl < 0 ? csv : csv.substring(0, nl))
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9,]", "");
        return header.contains("TCID") && header.contains("TITLE");
    }

    record ParsedLlmOutput(List<ManualTestCase> cases, String coverageNotes) {
    }

    static String buildQualityRetryUserMessage(
            ProjectRecord project,
            String stories,
            String storyLabel,
            String previousRaw,
            List<String> errors
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT CONTEXT\n");
        sb.append("Name: ").append(project.getName()).append('\n');
        sb.append("Base URL: ").append(project.getBaseUrl() == null ? "(not set)" : project.getBaseUrl()).append('\n');
        if (storyLabel != null && !storyLabel.isBlank()) {
            sb.append("Story ID: ").append(storyLabel.trim()).append('\n');
        }
        sb.append("\nUSER STORIES / ACCEPTANCE CRITERIA:\n");
        sb.append(stories);
        sb.append("\n\nPrevious output failed quality checks:\n");
        for (String err : errors) {
            sb.append("- ").append(err).append('\n');
        }
        sb.append("\nPrevious output:\n").append(previousRaw);
        sb.append("\n\nRegenerate the full JSON batch now. Fix every issue above. "
                + "Use tcId values like TC_01 or TC_DQ_01 only. "
                + "Inside JSON strings, encode line breaks as a backslash-n escape (valid JSON). "
                + "KeelPath must be blank or one of AUTOMATE, EXECUTE, VISION_ONLY, MANUAL (aliases OK). "
                + "For login: use 'Email or phone field' on combined forms (never standalone Phone field). "
                + "For empty-field cases: use 'Leave the … field empty' and blank matching TestData lines. "
                + "For confirm/assert steps: quote the exact UI error message from requirements.");
        return sb.toString();
    }

    static String buildReviewUserMessage(String stories, String raw) {
        String existing;
        String formatHint;
        try {
            GeneratedTcJsonParser.parse(raw);
            existing = GeneratedTcJsonParser.extractJsonBlock(raw);
            formatHint = "Fix authoring mistakes in existing cases first (empty-field TestData must be blank on that step; "
                    + "use 'Leave the … field empty'; combined login uses 'Email or phone field'; quote exact UI error messages). "
                    + "Then add ONLY missing test cases vs the original stories. "
                    + "Reply with the full merged JSON object (all cases) and updated coverageNotes.";
        } catch (IllegalArgumentException ignored) {
            existing = GeneratedTcCsvParser.extractCsvBlock(raw);
            formatHint = "Fix authoring mistakes in existing rows first (empty-field TestData blank on that step; "
                    + "Leave the … field empty; Email or phone field on combined login; quote exact UI errors). "
                    + "Then add ONLY missing test cases as additional CSV rows (same header). "
                    + "Reply with full merged CSV (all rows) then " + COVERAGE_MARKER + " and updated coverage notes.";
        }
        return "Original stories:\n" + stories
                + "\n\nExisting output:\n" + existing
                + "\n\n" + formatHint;
    }

    private static String buildUserMessage(ProjectRecord project, String stories, String storyLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT CONTEXT\n");
        sb.append("Name: ").append(project.getName()).append('\n');
        sb.append("Base URL: ").append(project.getBaseUrl() == null ? "(not set)" : project.getBaseUrl()).append('\n');
        if (storyLabel != null && !storyLabel.isBlank()) {
            sb.append("Story ID: ").append(storyLabel.trim()).append('\n');
        }
        sb.append("\nUSER STORIES / ACCEPTANCE CRITERIA:\n");
        sb.append(stories);
        sb.append("\n\nGenerate the JSON batch now with testCases and coverageNotes.");
        return sb.toString();
    }

    private static String loadPrompt(String classpath) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpath);
        if (!resource.exists()) {
            throw new IOException("Missing prompt file: " + classpath);
        }
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }
}
