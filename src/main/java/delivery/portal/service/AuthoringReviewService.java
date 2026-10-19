package delivery.portal.service;

import delivery.authoring.LeftoverExpectedQuoter;
import delivery.authoring.LocalLlmClient;
import delivery.excel.AuthoringReviewParser;
import delivery.excel.ExcelTcReader;
import delivery.excel.GenerateQualityGate;
import delivery.excel.GeneratedTcCsvParser;
import delivery.excel.ManualTestCase;
import delivery.excel.TcImportRepair;
import delivery.heal.CursorHealClient;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.ProjectRecord;
import delivery.portal.service.llm.LlmSettingsService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AuthoringReviewService {
    public static final int MAX_CASES = ReviewBatchPolicy.ABSOLUTE_MAX;

    private static final String SYSTEM_PROMPT = """
            You are Keel authoring reviewer. Given suite JSON plus optional stories and requirements notes,
            return ONLY JSON with findings[], cases[] (the full suite), and coverageNotes.
            Finding severity must be one of: info, warn, error.
            Fix leave-empty/TestData alignment, vague assertions, and ambiguities. Flag missing coverage
            against stories/notes when present; if both are empty, note that in a finding and still repair
            authoring issues. cases[] must include every input TC_id exactly once (extra TC_ids only for
            missing coverage). Do not invent large unrelated suites. Preserve TC_id values and obey
            KeelPath rules.
            Quoted phrases are visible-text waits: the string MUST match the screen (Upload Transaction List is not Upload Transactions List).
            Outcome "and" may split extra checks; "expands and sub-menu items are shown" stays one check.
            After a fill, prefer "{Field} is accepted" or "OTP is accepted". Never assert typed digits or password on a masked field (do not keep filled with '245345' as visible text).
            Keep "{Field} is shown", "{Button} is clicked", and page-shown wording.
            Do not invent large extra suites. Preserve tcId and KeelPath.
            Flag leftover Expected with no matching step.
            Leftover Expected that names a heading must quote the distinctive words only. Never quote area, button, label, title, page, section, field, menu, item, list, visible, shown, displayed. Example: "the Upload Transactions list area is visible" → 'Upload Transactions'.
            """;

    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;
    private final ReviewLlmPort llm;
    private final LlmSettingsService llmSettings;
    private final int configuredBatch;

    @FunctionalInterface
    public interface ReviewLlmPort {
        String complete(String provider, String model, String system, String user) throws Exception;
    }

    @Autowired
    public AuthoringReviewService(
            DeliveryPortalProperties props,
            PortalStore store,
            GeneratedWorkbookService workbooks,
            LlmSettingsService llmSettings
    ) {
        this(props, store, workbooks, defaultPort(props), llmSettings, ReviewBatchPolicy.DEFAULT_N);
    }

    AuthoringReviewService(
            DeliveryPortalProperties props,
            PortalStore store,
            GeneratedWorkbookService workbooks,
            ReviewLlmPort llm
    ) {
        this(props, store, workbooks, llm, null, ReviewBatchPolicy.DEFAULT_N);
    }

    AuthoringReviewService(
            DeliveryPortalProperties props,
            PortalStore store,
            GeneratedWorkbookService workbooks,
            ReviewLlmPort llm,
            int configuredBatch
    ) {
        this(props, store, workbooks, llm, null, configuredBatch);
    }

    private AuthoringReviewService(
            DeliveryPortalProperties props,
            PortalStore store,
            GeneratedWorkbookService workbooks,
            ReviewLlmPort llm,
            LlmSettingsService llmSettings,
            int configuredBatch
    ) {
        this.store = store;
        this.workbooks = workbooks;
        this.llm = llm;
        this.llmSettings = llmSettings;
        this.configuredBatch = ReviewBatchPolicy.clampN(configuredBatch);
    }

    public Map<String, Object> review(
            String projectId,
            Long ownerUserId,
            String provider,
            String model,
            String requirementsNotes,
            String stories
    ) throws Exception {
        List<ManualTestCase> current = new ExcelTcReader().read(workbooks.requireExcel(projectId));
        int n = configuredN();
        List<List<ManualTestCase>> batches = ReviewBatchPolicy.chunks(current, n);
        if (batches.size() <= 1) {
            return reviewCases(
                    projectId, ownerUserId, current, provider, model, requirementsNotes, stories);
        }
        List<Map<String, Object>> allCases = new ArrayList<>();
        List<Map<String, Object>> allFindings = new ArrayList<>();
        List<String> gateErrors = new ArrayList<>();
        StringBuilder coverage = new StringBuilder();
        String csv = "";
        boolean previewOk = true;
        String providerOut = provider;
        String modelOut = model;
        for (List<ManualTestCase> batch : batches) {
            Map<String, Object> part = reviewCases(
                    projectId, ownerUserId, batch, provider, model, requirementsNotes, stories);
            providerOut = String.valueOf(part.getOrDefault("provider", providerOut));
            modelOut = String.valueOf(part.getOrDefault("model", modelOut));
            Object cases = part.get("cases");
            if (cases instanceof List<?> list) {
                for (Object row : list) {
                    if (row instanceof Map<?, ?> map) {
                        allCases.add(new LinkedHashMap<>((Map<String, Object>) (Map<?, ?>) map));
                    }
                }
            }
            Object findings = part.get("findings");
            if (findings instanceof List<?> list) {
                for (Object row : list) {
                    if (row instanceof Map<?, ?> map) {
                        allFindings.add(new LinkedHashMap<>((Map<String, Object>) (Map<?, ?>) map));
                    }
                }
            }
            Object gates = part.get("gateErrors");
            if (gates instanceof List<?> list) {
                for (Object row : list) {
                    if (row != null) {
                        gateErrors.add(String.valueOf(row));
                    }
                }
            }
            Object notes = part.get("coverageNotes");
            if (notes != null && !String.valueOf(notes).isBlank()) {
                if (coverage.length() > 0) {
                    coverage.append('\n');
                }
                coverage.append(notes);
            }
            String partCsv = part.get("csv") == null ? "" : String.valueOf(part.get("csv"));
            csv = mergeCsv(csv, partCsv);
            previewOk &= Boolean.TRUE.equals(part.get("previewOk"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", providerOut);
        out.put("model", modelOut);
        out.put("findings", allFindings);
        out.put("cases", allCases);
        out.put("csv", csv);
        out.put("coverageNotes", coverage.toString());
        out.put("gateErrors", gateErrors);
        out.put("tcCount", allCases.size());
        out.put("previewOk", previewOk && gateErrors.isEmpty());
        return out;
    }

    /**
     * Review the exact suite about to run (library subset, upload, or merged input).
     * This is preview-only; the caller decides whether to accept and submit the returned CSV.
     */
    public Map<String, Object> reviewCases(
            String projectId,
            Long ownerUserId,
            List<ManualTestCase> current,
            String provider,
            String model,
            String requirementsNotes,
            String stories
    ) throws Exception {
        ProjectRecord project = store.getOwnedProject(projectId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project"));
        if (current == null || current.isEmpty()) {
            throw new IllegalArgumentException("No test cases to review");
        }
        int cap = ReviewBatchPolicy.capForPostedCount(current.size(), configuredN());
        if (ReviewBatchPolicy.exceedsCap(current.size(), configuredN())) {
            throw new IllegalArgumentException("Suite exceeds " + cap + " cases");
        }

        String providerNorm = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!providerNorm.equals("cursor")
                && !providerNorm.equals("ollama")
                && !providerNorm.equals("keel")) {
            throw new IllegalArgumentException("provider must be cursor, ollama, or keel");
        }
        String modelNorm = model == null ? "" : model.trim();
        if ((providerNorm.equals("ollama") || providerNorm.equals("keel")) && modelNorm.isEmpty()) {
            throw new IllegalArgumentException("Ollama model must be specified");
        }
        if (providerNorm.equals("cursor")) {
            modelNorm = "";
        }

        String llmProvider = providerNorm.equals("keel") ? "ollama" : providerNorm;
        String userPayload = toSuiteJson(current, stories, requirementsNotes).toString(2);
        String raw = llm.complete(llmProvider, modelNorm, SYSTEM_PROMPT, userPayload);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(llmProvider + " returned empty review");
        }

        AuthoringReviewParser.ParseResult parsed = AuthoringReviewParser.parse(raw);
        validateReturnedSuite(current, parsed.cases());
        List<ManualTestCase> repaired = TcImportRepair.repairCases(parsed.cases());
        List<ManualTestCase> quoted = new ArrayList<>();
        for (ManualTestCase tc : repaired) {
            quoted.add(LeftoverExpectedQuoter.quoteCase(tc));
        }
        repaired = quoted;
        List<String> gateErrors = GenerateQualityGate.validate(repaired, project.getBaseUrl());

        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> caseRows = new ArrayList<>();
        for (ManualTestCase tc : repaired) {
            caseRows.add(GeneratedWorkbookService.toRowMap(tc));
        }

        out.put("provider", providerNorm);
        out.put("model", modelNorm);
        out.put("findings", findingMaps(parsed.findings()));
        out.put("cases", caseRows);
        out.put("csv", GeneratedTcCsvParser.toCsv(repaired));
        out.put("coverageNotes", parsed.coverageNotes());
        out.put("gateErrors", gateErrors);
        out.put("tcCount", repaired.size());
        out.put("previewOk", gateErrors.isEmpty());
        return out;
    }

    private static void validateReturnedSuite(
            List<ManualTestCase> input,
            List<ManualTestCase> returned
    ) {
        if (returned.size() > MAX_CASES) {
            throw new IllegalArgumentException(
                    "Authoring review returned more than " + MAX_CASES + " cases");
        }
        Map<String, Integer> returnedCounts = new HashMap<>();
        for (ManualTestCase tc : returned) {
            String tcId = tc.tcId() == null ? "" : tc.tcId().trim();
            returnedCounts.merge(tcId, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : returnedCounts.entrySet()) {
            if (entry.getValue() > 1) {
                throw new IllegalArgumentException(
                        "Authoring review returned duplicate tcId '" + entry.getKey() + "'");
            }
        }
        Set<String> inputIds = new HashSet<>();
        for (ManualTestCase tc : input) {
            inputIds.add(tc.tcId() == null ? "" : tc.tcId().trim());
        }
        for (String inputId : inputIds) {
            if (returnedCounts.getOrDefault(inputId, 0) != 1) {
                throw new IllegalArgumentException(
                        "Authoring review response is missing input tcId '" + inputId + "'");
            }
        }
    }

    private static ReviewLlmPort defaultPort(DeliveryPortalProperties props) {
        CursorHealClient cursor = new CursorHealClient();
        // Cursor embeds the reviewer contract in heal.mjs; Ollama uses SYSTEM_PROMPT here.
        return (provider, model, system, user) -> {
            if ("cursor".equals(provider)) {
                return cursor.authoringReview(user);
            }
            LocalLlmClient ollama = new LocalLlmClient(
                    props.getLlmBaseUrl(),
                    model,
                    Duration.ofSeconds(Math.max(30, props.getGenerateTimeoutSeconds()) + 30L));
            return ollama.completeChat(system, user, true);
        };
    }

    private int configuredN() {
        if (llmSettings != null) {
            return llmSettings.reviewCasesPerBatch();
        }
        return configuredBatch;
    }

    static String mergeCsv(String existing, String next) {
        if (next == null || next.isBlank()) {
            return existing == null ? "" : existing;
        }
        if (existing == null || existing.isBlank()) {
            return next;
        }
        int nl = next.indexOf('\n');
        if (nl < 0 || nl + 1 >= next.length()) {
            return existing;
        }
        return existing + next.substring(nl);
    }

    private static JSONObject toSuiteJson(
            List<ManualTestCase> cases,
            String stories,
            String requirementsNotes
    ) {
        JSONArray rows = new JSONArray();
        for (ManualTestCase tc : cases) {
            rows.put(new JSONObject()
                    .put("tcId", tc.tcId())
                    .put("title", tc.title())
                    .put("preconditions", tc.preconditions())
                    .put("steps", tc.steps())
                    .put("expectedResult", tc.expectedResult())
                    .put("priority", tc.priority())
                    .put("tags", tc.tags())
                    .put("visualAssertion", tc.visualAssertion())
                    .put("testData", tc.testData())
                    .put("keelPath", tc.keelPath())
                    .put("callBefore", tc.callBefore()));
        }
        return new JSONObject()
                .put("cases", rows)
                .put("stories", stories == null ? "" : stories)
                .put("requirementsNotes", requirementsNotes == null ? "" : requirementsNotes);
    }

    private static List<Map<String, Object>> findingMaps(List<AuthoringReviewParser.Finding> findings) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (AuthoringReviewParser.Finding finding : findings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("severity", finding.severity());
            item.put("tcId", finding.tcId());
            item.put("message", finding.message());
            maps.add(item);
        }
        return List.copyOf(maps);
    }
}
