package delivery.codegen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * W3: trimmed DOM-diff → assert suggestions for Automate emit.
 * Suggests only stable kinds already supported by page emit (visible / notVisible /
 * textContains / ariaRole / ariaLabel / region). Caps suggestions to avoid noisy pages.
 */
public final class DomDiffAssertSuggestor {
    public static final int MAX_SUGGESTIONS = 4;

    private DomDiffAssertSuggestor() {
    }

    /** One DOM-diff delta the AI / prove layer observed. */
    public record DiffHint(
            String change,
            String strategy,
            String value,
            String assertionType,
            String expected,
            String rationale
    ) {
        public DiffHint {
            change = change == null ? "" : change.trim().toLowerCase(Locale.ROOT);
            strategy = strategy == null ? "" : strategy.trim();
            value = value == null ? "" : value;
            assertionType = assertionType == null ? "" : assertionType.trim();
            expected = expected == null ? "" : expected;
            rationale = rationale == null ? "" : rationale;
        }
    }

    public record Suggestion(
            String assertionType,
            String strategy,
            String locatorValue,
            String expected,
            String rationale
    ) {
        public ProvenStep toStep(String tcId, String pageName) {
            return new ProvenStep(
                    tcId == null ? "TC_DOMDIFF" : tcId,
                    pageName == null || pageName.isBlank() ? "Page" : pageName,
                    "elementAction",
                    "assert",
                    strategy,
                    locatorValue,
                    "",
                    assertionType,
                    expected,
                    true,
                    rationale == null || rationale.isBlank() ? "dom-diff:" + assertionType : rationale,
                    "");
        }
    }

    public static List<Suggestion> suggest(List<DiffHint> hints) {
        if (hints == null || hints.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Suggestion> out = new ArrayList<>();
        for (DiffHint h : hints) {
            if (h == null || h.value().isBlank()) {
                continue;
            }
            Suggestion s = map(h);
            if (s == null) {
                continue;
            }
            String key = s.assertionType().toLowerCase(Locale.ROOT) + '\0'
                    + s.strategy().toLowerCase(Locale.ROOT) + '\0'
                    + s.locatorValue() + '\0' + s.expected();
            if (!seen.add(key)) {
                continue;
            }
            out.add(s);
            if (out.size() >= MAX_SUGGESTIONS) {
                break;
            }
        }
        return List.copyOf(out);
    }

    public static void apply(PageAccumulator pages, String tcId, String pageName, List<DiffHint> hints) {
        for (Suggestion s : suggest(hints)) {
            pages.add(s.toStep(tcId, pageName));
        }
    }

    static Suggestion map(DiffHint h) {
        String type = h.assertionType();
        String expected = h.expected();
        String change = h.change();
        if (type.isBlank()) {
            type = switch (change) {
                case "removed", "gone", "hidden" -> "notVisible";
                case "added", "appeared", "shown" -> "visible";
                case "text", "textchanged", "changed" -> "textContains";
                case "role", "ariarole" -> "ariaRole";
                case "label", "arialabel" -> "ariaLabel";
                case "region", "landmark" -> "region";
                default -> h.expected().isBlank() ? "visible" : "textContains";
            };
        }
        type = switch (type.toLowerCase(Locale.ROOT)) {
            case "text" -> "textContains";
            case "textexact", "exacttext" -> "textEquals";
            case "role", "hasrole" -> "ariaRole";
            case "accessiblename", "accname" -> "ariaLabel";
            case "landmark", "inregion" -> "region";
            case "hidden", "gone" -> "notVisible";
            case "shown", "appeared" -> "visible";
            default -> type;
        };
        Set<String> allowed = Set.of(
                "visible", "notVisible", "textContains", "textEquals",
                "ariaRole", "ariaLabel", "region", "value");
        if (!allowed.contains(type)) {
            return null;
        }
        if (("textContains".equals(type) || "textEquals".equals(type) || "value".equals(type)
                || "ariaRole".equals(type) || "ariaLabel".equals(type) || "region".equals(type))
                && expected.isBlank()) {
            return null;
        }
        String rationale = h.rationale().isBlank()
                ? "dom-diff:" + change + "->" + type
                : h.rationale();
        return new Suggestion(type, h.strategy(), h.value(), expected, rationale);
    }
}
