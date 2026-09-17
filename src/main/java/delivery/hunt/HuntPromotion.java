package delivery.hunt;

import delivery.excel.ManualTestCase;
import delivery.ir.TcIdentity;
import delivery.store.StaleLibraryRevisionException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reviewed Hunt promotion. Candidates never merge silently; stale acceptances must rebase.
 */
public final class HuntPromotion {
    public enum DuplicateKind { EXACT, SEMANTIC_SUGGESTED }

    public record Duplicate(String candidateTitle, String libraryTcId, DuplicateKind kind) {
    }

    public record Preview(
            List<ManualTestCase> cases,
            List<Duplicate> duplicates,
            String pinnedLibraryRevisionId
    ) {
    }

    private HuntPromotion() {
    }

    public static Preview preview(
            String candidatesJson,
            Map<String, ManualTestCase> pinnedLibrary,
            String pinnedRevisionId,
            String headRevisionId
    ) {
        if (pinnedRevisionId != null && headRevisionId != null
                && !pinnedRevisionId.isBlank() && !headRevisionId.isBlank()
                && !pinnedRevisionId.equals(headRevisionId)) {
            throw new StaleLibraryRevisionException(pinnedRevisionId, headRevisionId);
        }
        List<ManualTestCase> cases = parseCandidates(candidatesJson);
        List<Duplicate> dups = new ArrayList<>();
        Map<String, ManualTestCase> library = pinnedLibrary == null ? Map.of() : pinnedLibrary;
        for (ManualTestCase candidate : cases) {
            for (ManualTestCase existing : library.values()) {
                if (exactMatch(candidate, existing)) {
                    dups.add(new Duplicate(candidate.title(), existing.tcId(), DuplicateKind.EXACT));
                } else if (semanticMatch(candidate, existing)) {
                    dups.add(new Duplicate(candidate.title(), existing.tcId(), DuplicateKind.SEMANTIC_SUGGESTED));
                }
            }
        }
        return new Preview(List.copyOf(cases), List.copyOf(dups), pinnedRevisionId == null ? "" : pinnedRevisionId);
    }

    public static List<ManualTestCase> parseCandidates(String candidatesJson) {
        JSONArray arr;
        if (candidatesJson == null || candidatesJson.isBlank()) {
            arr = new JSONArray();
        } else {
            String trimmed = candidatesJson.trim();
            if (trimmed.startsWith("{")) {
                arr = new JSONObject(trimmed).optJSONArray("scenarios");
                if (arr == null) {
                    arr = new JSONArray();
                }
            } else {
                arr = new JSONArray(trimmed);
            }
        }
        List<ManualTestCase> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String title = row.optString("title", "Hunt candidate " + (i + 1));
            String steps = row.optString("steps", "");
            String expected = row.optString("expected", row.optString("expectedResult", ""));
            String tags = row.optString("tags", "hunt");
            String tcId = "TC_HUNT_" + String.format(Locale.ROOT, "%03d", i + 1);
            TcIdentity.requireValid(tcId);
            out.add(new ManualTestCase(tcId, title, "", steps, expected, "P2", tags));
        }
        return List.copyOf(out);
    }

    public static String provenance(String huntJobId) {
        return "HUNT_PROMOTE:" + (huntJobId == null ? "" : huntJobId);
    }

    private static boolean exactMatch(ManualTestCase a, ManualTestCase b) {
        return norm(a.title()).equals(norm(b.title()))
                && norm(a.steps()).equals(norm(b.steps()))
                && norm(a.expectedResult()).equals(norm(b.expectedResult()));
    }

    private static boolean semanticMatch(ManualTestCase a, ManualTestCase b) {
        return norm(a.title()).equals(norm(b.title())) && !exactMatch(a, b);
    }

    private static String norm(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static Map<String, ManualTestCase> index(List<ManualTestCase> cases) {
        Map<String, ManualTestCase> out = new LinkedHashMap<>();
        if (cases == null) {
            return out;
        }
        for (ManualTestCase tc : cases) {
            if (tc != null && tc.tcId() != null) {
                out.put(tc.tcId(), tc);
            }
        }
        return out;
    }

    public static String utf8(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }
}
