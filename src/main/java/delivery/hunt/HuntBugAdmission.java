package delivery.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * P1 hard bug admission: keep a planner- or oracle-emitted bug only when there is hard evidence —
 * oracle tag, network failure, or a map-bound assert. Toast-only / soft {@code assert_text} noise
 * is dropped unless severity ≥ warn <em>and</em> the asserted copy was observed on the page.
 */
public final class HuntBugAdmission {
    private HuntBugAdmission() {}

    public enum Evidence { ORACLE, NETWORK, MAP_BOUND_ASSERT, COPY_MATCHED_WARN, REJECTED }

    public record Decision(boolean admit, Evidence evidence, String reason) {
        static Decision keep(Evidence e, String r) { return new Decision(true, e, r); }
        static Decision drop(String r) { return new Decision(false, Evidence.REJECTED, r); }
    }

    /** Filter a batch; admitted bugs keep their fields, rejected are omitted. */
    public static List<Map<String, Object>> filter(
            List<Map<String, Object>> bugs,
            List<Map<String, Object>> networkFailures,
            List<Map<String, Object>> actionLog,
            String pageTextOrSlim
    ) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (bugs == null) return out;
        for (Map<String, Object> bug : bugs) {
            Decision d = decide(bug, networkFailures, actionLog, pageTextOrSlim);
            if (d.admit()) {
                bug.put("admissionEvidence", d.evidence().name());
                // Frontend triage UI (COORDINATE_WITH_AE): admission enum
                bug.put("admission", frontendAdmission(d.evidence()));
                out.add(bug);
            }
        }
        return out;
    }

    public static Decision decide(
            Map<String, Object> bug,
            List<Map<String, Object>> networkFailures,
            List<Map<String, Object>> actionLog,
            String pageTextOrSlim
    ) {
        if (bug == null) return Decision.drop("null bug");
        // AE-D2: do NOT trust planner-supplied oracleSource — only HuntOracle.collect may stamp ORACLE
        // (those bugs skip this filter). Network / map-bound / soft toast paths remain.
        if (looksNetwork(bug) || hasNetworkMatch(bug, networkFailures)) {
            return Decision.keep(Evidence.NETWORK, "network");
        }
        if (hasMapBoundAssertFail(actionLog, bug)) {
            return Decision.keep(Evidence.MAP_BOUND_ASSERT, "map-bound assert");
        }
        // Soft assert_text / toast-only: require severity≥warn AND copy matched on page
        if (isSoftAssertOrToast(bug)) {
            if (severityAtLeastWarn(bug) && copyMatched(bug, pageTextOrSlim)) {
                return Decision.keep(Evidence.COPY_MATCHED_WARN, "severity≥warn + copy on page");
            }
            return Decision.drop("toast/soft assert_text without matched copy or severity");
        }
        // Speculative planner bugs with no hard evidence
        return Decision.drop("no oracle/network/map-bound evidence");
    }

    /** Map internal evidence → Frontend triage admission labels. */
    public static String frontendAdmission(Evidence e) {
        if (e == null) return "REJECTED";
        return switch (e) {
            case ORACLE -> "ORACLE";
            case NETWORK -> "NETWORK";
            case MAP_BOUND_ASSERT -> "MAP_BOUND_ASSERT";
            case COPY_MATCHED_WARN -> "TOAST_PROMOTED";
            case REJECTED -> "REJECTED";
        };
    }

    static boolean isSoftAssertOrToast(Map<String, Object> bug) {
        String title = str(bug.get("title")).toLowerCase(Locale.ROOT);
        String actual = str(bug.get("actual")).toLowerCase(Locale.ROOT);
        String expected = str(bug.get("expected")).toLowerCase(Locale.ROOT);
        String blob = title + " " + actual + " " + expected;
        if (blob.contains("toast") || blob.contains("snackbar") || blob.contains("assert_text")
                || blob.contains("assert text") || title.startsWith("assert failed: assert_text")) {
            return true;
        }
        // planner filed a missing-string guess
        return actual.contains("not found") && (title.contains("text") || expected.length() > 0)
                && !title.contains("assert_visible");
    }

    static boolean severityAtLeastWarn(Map<String, Object> bug) {
        String s = str(bug.get("severity")).toLowerCase(Locale.ROOT);
        // AE-D3: severity ≥ warn (minor/info/blank excluded)
        return s.equals("warn") || s.equals("warning") || s.equals("major")
                || s.equals("critical") || s.equals("error") || s.equals("high");
    }

    static boolean copyMatched(Map<String, Object> bug, String page) {
        if (page == null || page.isBlank()) return false;
        String pageLc = page.toLowerCase(Locale.ROOT);
        for (String key : List.of("expected", "actual", "text", "title")) {
            String v = str(bug.get(key)).trim();
            if (v.length() < 4) continue;
            // strip common prefixes
            String core = v.replaceFirst("(?i)^assert failed:\\s*assert_text\\s*\\(?", "")
                    .replaceFirst("\\)$", "").trim();
            if (core.length() >= 4 && pageLc.contains(core.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    static boolean looksNetwork(Map<String, Object> bug) {
        String blob = (str(bug.get("title")) + " " + str(bug.get("actual"))).toLowerCase(Locale.ROOT);
        return blob.contains("net::err") || blob.contains("http ") || blob.contains("status 5")
                || blob.contains("network") || blob.contains("loading_failed");
    }

    static boolean hasNetworkMatch(Map<String, Object> bug, List<Map<String, Object>> netFails) {
        if (netFails == null || netFails.isEmpty()) return false;
        String blob = (str(bug.get("title")) + " " + str(bug.get("actual"))).toLowerCase(Locale.ROOT);
        for (Map<String, Object> f : netFails) {
            String url = str(f.get("url")).toLowerCase(Locale.ROOT);
            String err = str(f.get("errorText")).toLowerCase(Locale.ROOT);
            if (!url.isBlank() && blob.contains(url.substring(0, Math.min(40, url.length())))) return true;
            if (!err.isBlank() && blob.contains(err.substring(0, Math.min(24, err.length())))) return true;
            if ("http_error".equals(str(f.get("type"))) || "loading_failed".equals(str(f.get("type")))) {
                // any network failure in the cycle corroborates a network-titled bug
                if (looksNetwork(bug)) return true;
            }
        }
        return false;
    }

    static boolean hasMapBoundAssertFail(List<Map<String, Object>> actionLog, Map<String, Object> bug) {
        if (actionLog == null) return false;
        String title = str(bug.get("title")).toLowerCase(Locale.ROOT);
        String actual = str(bug.get("actual")).toLowerCase(Locale.ROOT);
        String expected = str(bug.get("expected")).toLowerCase(Locale.ROOT);
        String blob = title + " " + actual + " " + expected;
        for (Map<String, Object> row : actionLog) {
            if (!"fail".equalsIgnoreCase(str(row.get("status")))) continue;
            String type = str(row.get("type")).toLowerCase(Locale.ROOT);
            String loc = str(row.get("locator"));
            if (loc.isBlank()) loc = str(row.get("locatorValue"));
            if (loc.isBlank()) continue;
            String locLc = loc.toLowerCase(Locale.ROOT);
            // AE-D4: require locator (or asserted text) overlap with the bug — not "any assert fail in cycle"
            boolean locHit = blob.contains(locLc) || title.contains(locLc);
            String rowText = str(row.get("text"));
            if (rowText.isBlank()) rowText = str(row.get("expected"));
            boolean textHit = rowText.length() >= 4 && blob.contains(rowText.toLowerCase(Locale.ROOT));
            if (!locHit && !textHit) continue;
            if ("assert_visible".equals(type) || "assert_text".equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(str(o));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
