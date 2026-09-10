package delivery.portal.service;

import delivery.excel.ManualTestCase;
import delivery.job.CallBeforeExpander;

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

        for (ManualTestCase tc : library) {
            if (tc == null || tc.tcId() == null || tc.tcId().isBlank()) {
                continue;
            }
            byId.put(tc.tcId().trim(), tc);
        }

        for (ManualTestCase tc : upload) {
            if (tc == null || tc.tcId() == null || tc.tcId().isBlank()) {
                continue;
            }
            byId.put(tc.tcId().trim(), tc);
        }

        if (byId.isEmpty()) {
            return List.of();
        }

        List<String> leafIds = resolveLeafIds(library, upload, selected);
        if (leafIds.isEmpty()) {
            return List.of();
        }

        return CallBeforeExpander.expand(new ArrayList<>(byId.values()), leafIds);
    }

    private static List<String> resolveLeafIds(
            List<ManualTestCase> library,
            List<ManualTestCase> upload,
            Set<String> selected
    ) {
        if (!selected.isEmpty()) {
            return new ArrayList<>(selected);
        }
        if (!library.isEmpty()) {
            return libraryIdsInOrder(library);
        }
        return libraryIdsInOrder(upload);
    }

    private static List<String> libraryIdsInOrder(List<ManualTestCase> cases) {
        List<String> leafIds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ManualTestCase tc : cases) {
            if (tc == null || tc.tcId() == null || tc.tcId().isBlank()) {
                continue;
            }
            String id = tc.tcId().trim();
            if (seen.add(id)) {
                leafIds.add(id);
            }
        }
        return leafIds;
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
