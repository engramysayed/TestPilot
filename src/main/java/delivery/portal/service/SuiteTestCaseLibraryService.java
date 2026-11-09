package delivery.portal.service;

import delivery.excel.ManualTestCase;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only merged project library for the Suites TC picker (workbook + plain cases).
 */
@Service
public class SuiteTestCaseLibraryService {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;

    private final GeneratedWorkbookService workbooks;
    private final PlainCaseService plainCases;

    public SuiteTestCaseLibraryService(GeneratedWorkbookService workbooks, PlainCaseService plainCases) {
        this.workbooks = workbooks;
        this.plainCases = plainCases;
    }

    public Map<String, Object> list(String projectId, String query, String cursor, Integer limit) throws Exception {
        int pageSize = normalizeLimit(limit);
        int offset = parseOffset(cursor);
        List<Map<String, Object>> all = mergeSources(projectId);
        List<Map<String, Object>> filtered = filter(query, all);
        int end = Math.min(offset + pageSize, filtered.size());
        List<Map<String, Object>> page = offset >= filtered.size()
                ? List.of()
                : List.copyOf(filtered.subList(offset, end));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", page);
        out.put("nextCursor", end < filtered.size() ? String.valueOf(end) : null);
        out.put("total", filtered.size());
        return out;
    }

    static List<Map<String, Object>> filter(String query, List<Map<String, Object>> items) {
        if (query == null || query.isBlank()) {
            return List.copyOf(items);
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : items) {
            String id = str(row.get("id")).toLowerCase(Locale.ROOT);
            String title = str(row.get("title")).toLowerCase(Locale.ROOT);
            if (id.contains(q) || title.contains(q)) {
                out.add(row);
            }
        }
        return List.copyOf(out);
    }

    static int parseOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int v = Integer.parseInt(cursor.trim());
            return Math.max(0, v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<Map<String, Object>> mergeSources(String projectId) throws Exception {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();

        if (workbooks.hasWorkbook(projectId)) {
            List<ManualTestCase> cases = workbooks.readCases(projectId);
            for (ManualTestCase tc : cases) {
                if (tc == null || tc.tcId() == null || tc.tcId().isBlank()) {
                    continue;
                }
                String id = tc.tcId().trim();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("title", tc.title() == null ? "" : tc.title().trim());
                row.put("source", "workbook");
                row.put("executableInSuite", true);
                byId.put(id, row);
            }
        }

        for (Map<String, Object> plain : plainCases.list(projectId)) {
            String id = str(plain.get("id"));
            if (id.isBlank()) {
                continue;
            }
            if (byId.containsKey(id)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("title", str(plain.get("title")));
            row.put("source", "plain");
            row.put("executableInSuite", false);
            byId.put(id, row);
        }

        return List.copyOf(byId.values());
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
