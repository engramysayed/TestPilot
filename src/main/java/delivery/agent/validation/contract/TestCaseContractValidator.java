package delivery.agent.validation.contract;

import delivery.excel.GenerateQualityGate;
import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic TC contract validation: structural (existing quality gate), contextual heuristics,
 * and quoted-entity extractability. Does not call Cursor.
 */
public final class TestCaseContractValidator {
    private static final Set<String> STORY_STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "should", "when", "then", "user");

    private TestCaseContractValidator() {
    }

    public static ValidationReport validate(List<ManualTestCase> cases, TestCaseValidationContext context) {
        List<ValidationFinding> findings = new ArrayList<>();
        findings.addAll(fromQualityGate(cases, context == null ? null : context.baseUrl()));
        if (cases != null) {
            for (ManualTestCase tc : cases) {
                if (tc != null) {
                    findings.addAll(contextualChecks(tc, context));
                    findings.addAll(entityChecks(tc));
                }
            }
        }
        return ValidationReport.of(ValidationContractIds.TC_CONTRACT_V1, findings);
    }

    private static List<ValidationFinding> fromQualityGate(List<ManualTestCase> cases, String baseUrl) {
        GenerateQualityGate.GateResult gate = GenerateQualityGate.validateDetailed(cases, baseUrl);
        if (gate.passed()) {
            return List.of();
        }
        List<ValidationFinding> out = new ArrayList<>();
        for (GenerateQualityGate.GateFailure f : gate.failures()) {
            String artifact = f.tcId().isBlank() ? "batch" : "tc:" + f.tcId();
            out.add(new ValidationFinding(
                    "TC_STRUCT_" + f.rule(),
                    f.message(),
                    ValidationSeverity.MANDATORY,
                    artifact,
                    "",
                    null,
                    f.field().isBlank() ? "" : "field=" + f.field(),
                    f.howToFix()));
        }
        return out;
    }

    private static List<ValidationFinding> contextualChecks(ManualTestCase tc, TestCaseValidationContext context) {
        List<ValidationFinding> out = new ArrayList<>();
        String tcId = tc.tcId() == null ? "" : tc.tcId();

        if (tc.title() == null || tc.title().isBlank()) {
            out.add(new ValidationFinding(
                    "TC_STRUCT_TITLE",
                    "tcId '" + tcId + "': title is blank",
                    ValidationSeverity.MANDATORY,
                    "tc:" + tcId,
                    "",
                    null,
                    "",
                    "Add a short title grounded in the user story."));
        }
        if (tc.expectedResult() == null || tc.expectedResult().isBlank()) {
            out.add(new ValidationFinding(
                    "TC_STRUCT_EXPECTED",
                    "tcId '" + tcId + "': expected result is blank",
                    ValidationSeverity.MANDATORY,
                    "tc:" + tcId,
                    "",
                    null,
                    "",
                    "State the observable outcome after the steps."));
        }

        if (context != null && !context.userStoryText().isBlank()) {
            String title = tc.title() == null ? "" : tc.title().toLowerCase(Locale.ROOT);
            if (!title.isBlank() && !sharesStoryToken(title, context.userStoryText())) {
                out.add(new ValidationFinding(
                        "TC_CTX_STORY_ALIGNMENT",
                        "tcId '" + tcId + "': title may not reflect the supplied user story (no shared keywords)",
                        ValidationSeverity.RECOMMENDED,
                        "tc:" + tcId,
                        "",
                        null,
                        truncate(context.userStoryText(), 120),
                        "Regenerate or edit the title so it reflects the story scope."));
            }
        }
        return out;
    }

    private static List<ValidationFinding> entityChecks(ManualTestCase tc) {
        List<ValidationFinding> out = new ArrayList<>();
        String tcId = tc.tcId() == null ? "" : tc.tcId();
        String steps = tc.steps() == null ? "" : tc.steps();
        List<String> quoted = QuotedEntityExtractor.extractFromTestCase(steps, tc.expectedResult());
        if (QuotedEntityExtractor.stepsLikelyNeedQuotedEntities(steps) && quoted.isEmpty()) {
            out.add(new ValidationFinding(
                    "TC_ENTITY_QUOTED_LABELS",
                    "tcId '" + tcId + "': steps reference UI actions but no quoted labels "
                            + "(e.g. 'Login Page', 'Password Field')",
                    ValidationSeverity.MANDATORY,
                    "tc:" + tcId,
                    "",
                    null,
                    truncate(steps, 160),
                    "Quote page, field, and button names exactly as shown in the UI."));
        }
        if (!quoted.isEmpty()) {
            for (String label : quoted) {
                if (!labelContainsUiNoun(label)) {
                    out.add(new ValidationFinding(
                            "TC_ENTITY_EXTRACTABLE",
                            "tcId '" + tcId + "': quoted label '" + label + "' may not identify a page/field/button",
                            ValidationSeverity.RECOMMENDED,
                            "tc:" + tcId,
                            "",
                            null,
                            label,
                            "Use labels like 'Login Page', 'Username Field', 'Submit Button'."));
                }
            }
        }
        return out;
    }

    private static boolean labelContainsUiNoun(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.contains("page")
                || lower.contains("field")
                || lower.contains("button")
                || lower.contains("link")
                || lower.contains("message")
                || lower.contains("tab")
                || lower.contains("menu");
    }

    private static boolean sharesStoryToken(String title, String story) {
        for (String token : tokenize(title)) {
            if (token.length() >= 4 && story.toLowerCase(Locale.ROOT).contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> tokenize(String text) {
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p.length() >= 3 && !STORY_STOP_WORDS.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}