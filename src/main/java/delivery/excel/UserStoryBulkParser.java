package delivery.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses bulk user-story uploads for generate-batch jobs. */
public final class UserStoryBulkParser {
    private static final String EXPECTED_HEADER = "US_ID,Title,Story";

    private UserStoryBulkParser() {
    }

    public record UserStoryEntry(String usId, String title, String story) {
        public String toPromptBlock() {
            StringBuilder sb = new StringBuilder();
            if (title != null && !title.isBlank()) {
                sb.append("Title: ").append(title.trim()).append('\n');
            }
            sb.append(story == null ? "" : story.trim());
            return sb.toString().trim();
        }
    }

    /** Builds a one-row bulk CSV from free-form story text (async single generate). */
    public static String toSingleStoryCsv(String story) {
        if (story == null || story.isBlank()) {
            throw new IllegalArgumentException("Story text is empty");
        }
        return EXPECTED_HEADER + "\nUS_ASYNC_001,," + escapeCsvField(story.trim()) + "\n";
    }

    private static String escapeCsvField(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public static List<UserStoryEntry> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Bulk stories file is empty");
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNl = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                text = text.substring(firstNl + 1, lastFence).trim();
            }
        }
        List<String[]> rows = GeneratedTcCsvParser.parseRows(text);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No rows found in bulk stories file");
        }
        Map<String, Integer> cols = mapHeader(rows.get(0));
        if (!cols.containsKey("USID") || !cols.containsKey("STORY")) {
            throw new IllegalArgumentException(
                    "Bulk stories CSV must have header: " + EXPECTED_HEADER);
        }
        List<UserStoryEntry> out = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            String usId = cell(row, cols.get("USID"));
            String story = cell(row, cols.get("STORY"));
            if (usId.isBlank() || story.isBlank()) {
                continue;
            }
            String title = cell(row, cols.getOrDefault("TITLE", -1));
            out.add(new UserStoryEntry(usId.trim(), title.trim(), story.trim()));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("Bulk stories CSV has no data rows");
        }
        return out;
    }

    private static Map<String, Integer> mapHeader(String[] header) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = normalizeHeader(header[i]);
            if (!key.isEmpty()) {
                map.put(key, i);
            }
        }
        return map;
    }

    private static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String cell(String[] row, int index) {
        if (index < 0 || index >= row.length) {
            return "";
        }
        return row[index] == null ? "" : row[index];
    }

    private static boolean isBlankRow(String[] row) {
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
