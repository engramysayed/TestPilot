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
    }

    private static final List<String> FIELDS = List.of("Title", "Steps", "ExpectedResult");

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
        String[] lines = text.split("\n");
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        if (lines.length == 0) {
            return out;
        }
        String[] header = lines[0].split(",", -1);
        Map<String, Integer> cols = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            cols.put(header[i].trim().toUpperCase(Locale.ROOT).replace("_", ""), i);
        }
        Integer idCol = cols.get("TCID");
        if (idCol == null) {
            return out;
        }
        for (int r = 1; r < lines.length; r++) {
            if (lines[r].isBlank()) {
                continue;
            }
            String[] cells = lines[r].split(",", -1);
            String id = cell(cells, idCol);
            if (id.isBlank()) {
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("Title", cell(cells, cols.get("TITLE")));
            fields.put("Steps", cell(cells, cols.get("STEPS")));
            fields.put("ExpectedResult", cell(cells, cols.get("EXPECTEDRESULT")));
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
