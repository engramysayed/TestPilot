package delivery.portal.service;

import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the workbook for one Automate/Execute job from library rows and/or an upload.
 * Upload wins on duplicate {@code TC_ID}.
 */
public final class WorkbookJobMaterializer {
    private WorkbookJobMaterializer() {
    }

    public static List<ManualTestCase> merge(
            List<ManualTestCase> libraryCases,
            List<String> selectedTcIds,
            List<ManualTestCase> uploadCases
    ) {
        List<ManualTestCase> library = libraryCases == null ? List.of() : libraryCases;
        List<ManualTestCase> upload = uploadCases == null ? List.of() : uploadCases;

        Set<String> selected = normalizeIds(selectedTcIds);
        Map<String, ManualTestCase> byId = new LinkedHashMap<>();

        boolean filterLibrary = !selected.isEmpty();
        for (ManualTestCase tc : library) {
            if (tc == null || tc.tcId() == null || tc.tcId().isBlank()) {
                continue;
            }
            if (filterLibrary && !selected.contains(tc.tcId().trim())) {
                continue;
            }
            byId.put(tc.tcId().trim(), tc);
        }

        // If selection was requested but library empty / no hits, still allow upload-only.
        for (ManualTestCase tc : upload) {
            if (tc == null || tc.tcId() == null || tc.tcId().isBlank()) {
                continue;
            }
            byId.put(tc.tcId().trim(), tc);
        }

        // Selection-only without library match and without upload → empty (caller errors).
        if (filterLibrary && upload.isEmpty() && byId.isEmpty()) {
            return List.of();
        }

        // No library filter and no library and only upload — byId already has upload.
        // No selection, empty library, empty upload — empty.
        if (!filterLibrary && library.isEmpty() && upload.isEmpty()) {
            return List.of();
        }

        // useGenerated all + no upload: selected empty means all library (already in byId)
        return new ArrayList<>(byId.values());
    }

    private static Set<String> normalizeIds(List<String> selectedTcIds) {
        Set<String> out = new LinkedHashSet<>();
        if (selectedTcIds == null) {
            return out;
        }
        for (String id : selectedTcIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            out.add(id.trim());
        }
        return out;
    }

    /** Surface-agnostic helper for parsing repeated form fields. */
    public static List<String> parseTcIdParams(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String r : raw) {
            if (r == null || r.isBlank()) {
                continue;
            }
            // Allow comma-separated in a single field
            for (String part : r.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }
}
