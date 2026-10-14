package delivery.codegen;

import delivery.snapshot.KeelTarget;
import delivery.snapshot.TextLocatorSanitizer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One ranked locator from the Automation Engine candidate model (A2 contract v1).
 *
 * @param strategy {@code keel} (value = KeelTarget string form, authoritative) or a legacy light-DOM
 *                 strategy: css | cssSelector | id | name | data-testid | data-test | data-qa | xpath
 * @param value    target string / selector
 * @param score    0..1, higher is better (informational: list order from the engine is the rank)
 *                 (an optional {@code route} field in the IR JSON is tolerated and ignored)
 */
public record LocatorCandidate(String strategy, String value, double score) {
    public static final int MAX_FALLBACKS = 3;
    /** Highest locator contract codegen (and the template runtime it emits) supports. */
    public static final int CONTRACT_VERSION = 2;
    public static final String KEEL = "keel";

    public LocatorCandidate {
        strategy = strategy == null ? "" : strategy.trim();
        value = value == null ? "" : value;
    }

    public static LocatorCandidate of(String strategy, String value) {
        return new LocatorCandidate(strategy, value, 0);
    }

    /**
     * Canonical KeelTarget string, or null when this locator cannot be expressed as one (XPath,
     * blank, {@code unsupported-context-ordinal}). A malformed {@code keel} value (unknown hop, css=
     * not last, ...) is a hard error, as the contract requires.
     */
    public String toTarget() {
        if (value.isBlank()) {
            return null;
        }
        if (KEEL.equalsIgnoreCase(strategy)) {
            KeelTarget t = KeelTarget.parse(value);
            requireSupportedContract(t.requiredVersion());
            if (t.locator().kind() == KeelTarget.Kind.TEXT && !TextLocatorSanitizer.allowed(normalizeText(t.locator().css()))) {
                return null; // PII / volatile literal: never written into generated code
            }
            return t.format();
        }
        String norm = PageAccumulator.normalizeByStrategyForCandidates(strategy);
        String v = PageAccumulator.normalizeLocatorValueForCandidates(strategy, value);
        String css = switch (norm) {
            case "cssSelector" -> v;
            case "id" -> "[id=\"" + cssString(v) + "\"]";
            case "name" -> "[name=\"" + cssString(v) + "\"]";
            default -> null; // xpath has no KeelTarget form
        };
        if (css == null || css.isBlank()) {
            return null;
        }
        return new KeelTarget(List.of(new KeelTarget.Hop(KeelTarget.Kind.CSS, css))).format();
    }

    private static String cssString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Ordered targets per A3 contract v1: the proven main locator first, then {@code fallbacks[]} in
     * the order given (the engine already sorts them by score, descending; order is not re-sorted
     * here), de-duplicated by canonical target and capped at {@link #MAX_FALLBACKS} (main + 2).
     * Locators with no KeelTarget form (XPath) are skipped.
     */
    public static List<String> rankedTargets(String provenStrategy, String provenValue, List<LocatorCandidate> fallbacks) {
        Set<String> out = new LinkedHashSet<>();
        String proven = LocatorCandidate.of(provenStrategy, provenValue).toTarget();
        if (proven != null) {
            out.add(proven);
        }
        if (fallbacks != null) {
            for (LocatorCandidate c : fallbacks) {
                if (out.size() >= MAX_FALLBACKS) {
                    break;
                }
                String t = c == null ? null : c.toTarget();
                if (t != null) {
                    out.add(t);
                }
            }
        }
        return List.copyOf(out);
    }

    public static void requireSupportedContract(int contract) {
        if (contract < 1) {
            throw new IllegalArgumentException("IR Keel locator contract must be >= 1, got " + contract);
        }
        if (contract > CONTRACT_VERSION) {
            throw new IllegalArgumentException("IR uses Keel locator contract " + contract
                    + " but Keel Codegen supports up to " + CONTRACT_VERSION
                    + " (upgrade codegen, or set keel.contract.max-version <= " + CONTRACT_VERSION + ")");
        }
    }

    /** Lowest contract able to express every keel target of the step (1 when none needs v2). */
    public static int requiredContract(ProvenStep s) {
        int v = 1;
        List<LocatorCandidate> all = new java.util.ArrayList<>(s.locatorCandidates());
        all.addAll(s.dropCandidates());
        all.add(LocatorCandidate.of(s.locatorStrategy(), s.locatorValue()));
        for (LocatorCandidate c : all) {
            if (KEEL.equalsIgnoreCase(c.strategy()) && !c.value().isBlank()) {
                try {
                    v = Math.max(v, KeelTarget.parse(c.value()).requiredVersion());
                } catch (IllegalArgumentException ignored) {
                    // malformed targets fail later at codegen with the parser's message
                }
            }
        }
        return v;
    }

    /** Java mirror of keelNormalizeText (keel-text-match.js), used for the PII check only. */
    static String normalizeText(String s) {
        if (s == null) return "";
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
                .replaceAll("[\u00A0\u2007\u202F]", " ").replaceAll("[\u200B\uFEFF]", "");
        return t.replaceAll("(?U)\\s+", " ").trim();
    }

    static int requiredVersion(String target) {
        return KeelTarget.parse(target).requiredVersion();
    }

    static boolean hasPointer(String target) {
        KeelTarget t = KeelTarget.parse(target);
        return t.provider().isPresent() || t.point().isPresent();
    }

    static boolean isPlainCss(String target) {
        return KeelTarget.parse(target).isPlainCss();
    }

    static String terminalCss(String target) {
        return KeelTarget.parse(target).terminalCss();
    }

    static String javaString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** {@code KeelBy.of("label", "t1", "t2", "t3")}. */
    static String keelByExpression(String label, List<String> targets) {
        StringBuilder sb = new StringBuilder("KeelBy.of(").append(javaString(label == null ? "" : label));
        for (String t : targets) {
            sb.append(",\n            ").append(javaString(t));
        }
        return sb.append(')').toString();
    }
}
