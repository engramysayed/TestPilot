package delivery.portal.service;

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
    public static final int MAX_CASES = 50;

    private static final String SYSTEM_PROMPT = """
            You are Keel authoring reviewer. Given suite JSON plus optional stories and requirements notes,
            return ONLY JSON with findings[], cases[] (the full suite), and coverageNotes.
            Finding severity must be one of: info, warn, error.
            Fix leave-empty/TestData alignment, vague assertions, and ambiguities. Flag missing coverage
            against stories/notes when present; if both are empty, note that in a finding and still repair
            authoring issues. cases[] must include every input TC_id exactly once (extra TC_ids only for
            missing coverage). Do not invent large unrelated suites. Preserve TC_id values and obey
            KeelPath rules.
            """;

    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;
    private final ReviewLlmPort llm;

    @FunctionalInterface
    public interface ReviewLlmPort {
        String complete(String provider, String model, String system, String user) throws Exception;
    }

    @Autowired
    public AuthoringReviewService(
            DeliveryPortalProperties props,
            PortalStore store,
            GeneratedWorkbookService workbooks
    ) {
        this(props, store, workbooks, defaultPort(props));
    }

    AuthoringReviewService(
            DeliveryPortalProperties props,
            PortalStore store,
            GeneratedWorkbookService workbooks,
            ReviewLlmPort llm
    ) {
        this.store = store;
        this.workbooks = workbooks;
        this.llm = llm;
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
        return reviewCases(
                projectId, ownerUserId, current, provider, model, requirementsNotes, stories);
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
        if (current.size() > MAX_CASES) {
            throw new IllegalArgumentException("Suite exceeds " + MAX_CASES + " cases");
        }

        String providerNorm = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!providerNorm.equals("cursor") && !providerNorm.equals("ollama")) {
            throw new IllegalArgumentException("provider must be cursor or ollama");
        }
        String modelNorm = model == null ? "" : model.trim();
        if (providerNorm.equals("ollama") && modelNorm.isEmpty()) {
            throw new IllegalArgumentException("Ollama model must be specified");
        }
        if (providerNorm.equals("cursor")) {
            modelNorm = "";
        }

        String userPayload = toSuiteJson(current, stories, requirementsNotes).toString(2);
        String raw = llm.complete(providerNorm, modelNorm, SYSTEM_PROMPT, userPayload);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(providerNorm + " returned empty review");
        }

        AuthoringReviewParser.ParseResult parsed = AuthoringReviewParser.parse(raw);
        validateReturnedSuite(current, parsed.cases());
        List<ManualTestCase> repaired = TcImportRepair.repairCases(parsed.cases());
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
                    .put("keelPath", tc.keelPath()));
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
