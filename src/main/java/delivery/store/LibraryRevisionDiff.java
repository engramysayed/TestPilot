package delivery.store;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Field-level library diff keyed by TC_ID. */
public final class LibraryRevisionDiff {
    public record Result(List<String> added, List<String> removed, Map<String, Map<String, String>> changed) {
        public Result filter(String kinds, String field) {
            boolean wantAdded = includesKind(kinds, "added");
            boolean wantRemoved = includesKind(kinds, "removed");
            boolean wantChanged = includesKind(kinds, "changed");
            List<String> nextAdded = wantAdded ? added : List.of();
            List<String> nextRemoved = wantRemoved ? removed : List.of();
            Map<String, Map<String, String>> nextChanged = new LinkedHashMap<>();
            if (wantChanged) {
                String fieldKey = field == null || field.isBlank() ? null : field.trim();
                for (Map.Entry<String, Map<String, String>> e : changed.entrySet()) {
                    if (fieldKey == null) {
                        nextChanged.put(e.getKey(), e.getValue());
                        continue;
                    }
                    Map<String, String> subset = new LinkedHashMap<>();
                    String before = e.getValue().get(fieldKey + ".before");
                    String after = e.getValue().get(fieldKey + ".after");
                    if (before != null) {
                        subset.put(fieldKey + ".before", before);
                    }
                    if (after != null) {
                        subset.put(fieldKey + ".after", after);
                    }
                    if (!subset.isEmpty()) {
                        nextChanged.put(e.getKey(), Map.copyOf(subset));
                    }
                }
            }
            return new Result(List.copyOf(nextAdded), List.copyOf(nextRemoved), Map.copyOf(nextChanged));
        }

        private static boolean includesKind(String kinds, String kind) {
            if (kinds == null || kinds.isBlank() || "all".equalsIgnoreCase(kinds.trim())) {
                return true;
            }
            for (String part : kinds.split(",")) {
                if (kind.equalsIgnoreCase(part.trim())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final List<String> FIELDS = List.of("Title", "Steps", "ExpectedResult",
            "Preconditions", "Priority", "Tags", "VisualAssertion", "TestData", "KeelPath", "CallBefore");

    private LibraryRevisionDiff() {
    }

    public static Result compareCsv(byte[] before, byte[] after) {
        return compare(parseCsv(before), parseCsv(after));
    }

    public static Result compare(Map<String, Map<String, String>> before, Map<String, Map<String, String>> after) {
        Map<String, Map<String, String>> left = before == null ? Map.of() : before;
        Map<String, Map<String, String>> right = after == null ? Map.of() : after;
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        Map<String, Map<String, String>> changed = new LinkedHashMap<>();
        for (String id : right.keySet()) {
            if (!left.containsKey(id)) {
                added.add(id);
            }
        }
        for (String id : left.keySet()) {
            if (!right.containsKey(id)) {
                removed.add(id);
            }
        }
        for (String id : right.keySet()) {
            if (!left.containsKey(id)) {
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            Map<String, String> a = left.get(id);
            Map<String, String> b = right.get(id);
            for (String field : FIELDS) {
                String av = a.getOrDefault(field, "");
                String bv = b.getOrDefault(field, "");
                if (!av.equals(bv)) {
                    fields.put(field + ".before", av);
                    fields.put(field + ".after", bv);
                }
            }
            if (!fields.isEmpty()) {
                changed.put(id, Map.copyOf(fields));
            }
        }
        return new Result(List.copyOf(added), List.copyOf(removed), Map.copyOf(changed));
    }

    static Map<String, Map<String, String>> parseCsv(byte[] raw) {
        String text = raw == null ? "" : new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n");
        List<String[]> rows = delivery.excel.GeneratedTcCsvParser.parseRows(text);
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            return out;
        }
        String[] header = rows.get(0);
        Map<String, Integer> cols = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            cols.put(header[i].trim().toUpperCase(Locale.ROOT).replace("_", ""), i);
        }
        Integer idCol = cols.get("TCID");
        if (idCol == null) {
            return out;
        }
        for (int r = 1; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            String id = cell(cells, idCol);
            if (id.isBlank()) {
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            for (String field : FIELDS) {
                fields.put(field, cell(cells, cols.get(field.toUpperCase(Locale.ROOT))));
            }
            out.put(id, fields);
        }
        return out;
    }

    private static String cell(String[] cells, Integer index) {
        if (index == null || index < 0 || index >= cells.length) {
            return "";
        }
        return cells[index] == null ? "" : cells[index].trim();
    }
}
