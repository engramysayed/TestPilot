package delivery.excel;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AuthoringReviewParser {
    private AuthoringReviewParser() {}

    public record Finding(String severity, String tcId, String message) {
        public Finding {
            String s = severity == null || severity.isBlank()
                    ? "info"
                    : severity.trim().toLowerCase(Locale.ROOT);
            if ("warning".equals(s)) {
                s = "warn";
            }
            severity = s;
            tcId = tcId == null ? "" : tcId.trim();
            message = message == null ? "" : message.trim();
        }
    }

    public record ParseResult(List<Finding> findings, List<ManualTestCase> cases, String coverageNotes) {
        public ParseResult {
            findings = findings == null ? List.of() : List.copyOf(findings);
            cases = cases == null ? List.of() : List.copyOf(cases);
            coverageNotes = coverageNotes == null ? "" : coverageNotes;
        }
    }

    public static ParseResult parse(String raw) {
        String stripped = TcImportRepair.stripMarkdownFences(raw == null ? "" : raw);
        JsonNode root;
        try {
            root = GeneratedTcJsonParser.parseRoot(stripped);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Authoring review response is not valid JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Authoring review response is not a JSON object");
        }
        List<Finding> findings = new ArrayList<>();
        JsonNode findingsNode = root.get("findings");
        if (findingsNode != null && findingsNode.isArray()) {
            for (JsonNode finding : findingsNode) {
                if (!finding.isObject()) continue;
                findings.add(new Finding(
                        finding.path("severity").asText("info"),
                        finding.path("tcId").asText(""),
                        finding.path("message").asText("")));
            }
        }
        JsonNode casesNode = root.get("cases");
        if (casesNode == null || !casesNode.isArray() || casesNode.isEmpty()) {
            throw new IllegalArgumentException("Authoring review response has no cases");
        }
        GeneratedTcJsonParser.ParseResult parsed = GeneratedTcJsonParser.parse(root.toString());
        if (parsed.cases().isEmpty()) {
            throw new IllegalArgumentException("Authoring review response has no cases");
        }
        String notes = root.path("coverageNotes").asText("");
        if (notes.isBlank()) {
            notes = parsed.coverageNotes();
        }
        return new ParseResult(findings, parsed.cases(), notes);
    }
}
