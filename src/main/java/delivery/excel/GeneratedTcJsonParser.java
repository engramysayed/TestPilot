package delivery.excel;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import delivery.portal.model.KeelPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses Keel TC JSON from LLM output into {@link ManualTestCase} rows. */
public final class GeneratedTcJsonParser {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    private GeneratedTcJsonParser() {
    }

    public record ParseResult(List<ManualTestCase> cases, String coverageNotes) {
    }

    public static ParseResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("JSON response is empty");
        }
        String jsonText = extractJsonBlock(raw);
        JsonNode root;
        try {
            root = MAPPER.readTree(jsonText);
        } catch (Exception e) {
            throw new IllegalArgumentException("Not valid JSON: " + e.getMessage(), e);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("JSON root must be an object");
        }
        JsonNode testCasesNode = firstArray(root, "testCases", "test_cases", "cases");
        if (testCasesNode == null || !testCasesNode.isArray() || testCasesNode.isEmpty()) {
            throw new IllegalArgumentException("JSON must contain a non-empty testCases array");
        }
        String coverageNotes = textOrEmpty(root.get("coverageNotes"));
        if (coverageNotes.isBlank()) {
            coverageNotes = GeneratedTcCsvParser.extractCoverageNotes(raw);
        }
        List<ManualTestCase> out = new ArrayList<>();
        for (JsonNode node : testCasesNode) {
            ManualTestCase tc = toManualTestCase(node);
            if (tc != null) {
                out.add(tc);
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("JSON has no data rows");
        }
        out = GeneratedTcCsvRepair.repair(out);
        for (ManualTestCase tc : out) {
            if (!GeneratedTcCsvRepair.isWellFormedTcId(tc.tcId())) {
                throw new IllegalArgumentException(
                        "Invalid tcId '" + tc.tcId() + "' — must be TC_01, TC_02, … with steps in the steps field");
            }
        }
        return new ParseResult(out, coverageNotes);
    }

    public static String extractJsonBlock(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        int coverage = text.indexOf("---KEEL_COVERAGE---");
        if (coverage >= 0) {
            text = text.substring(0, coverage).trim();
        }
        if (text.startsWith("```")) {
            int firstNl = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                text = text.substring(firstNl + 1, lastFence).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1).trim();
        }
        return text;
    }

    private static ManualTestCase toManualTestCase(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String tcId = textOrEmpty(firstField(node, "tcId", "TC_ID", "tc_id"));
        if (tcId.isBlank()) {
            return null;
        }
        String steps = ExcelStepText.normalizeMultiline(textOrEmpty(firstField(node, "steps", "Steps")));
        String expectedResult = ExcelStepText.normalizeMultiline(
                textOrEmpty(firstField(node, "expectedResult", "ExpectedResult")));
        String visualAssertion = ExcelStepText.normalizeMultiline(
                textOrEmpty(firstField(node, "visualAssertion", "VisualAssertion")));
        String testData = ExcelStepText.normalizeMultiline(textOrEmpty(firstField(node, "testData", "TestData")));
        String keelRaw = textOrEmpty(firstField(node, "keelPath", "KeelPath")).trim();
        testData = ExcelTcReader.alignTestDataToSteps(steps, testData);
        if (!keelRaw.isBlank()) {
            KeelPath.parse(keelRaw);
        }
        String keelStored = keelRaw.isBlank() ? "" : keelRaw.trim().toUpperCase(Locale.ROOT);
        return new ManualTestCase(
                tcId,
                textOrEmpty(firstField(node, "title", "Title")),
                textOrEmpty(firstField(node, "preconditions", "Preconditions")),
                steps,
                expectedResult,
                textOrEmpty(firstField(node, "priority", "Priority")),
                textOrEmpty(firstField(node, "tags", "Tags")),
                visualAssertion,
                testData,
                keelStored
        );
    }

    private static JsonNode firstArray(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private static JsonNode firstField(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.asText("");
    }
}
