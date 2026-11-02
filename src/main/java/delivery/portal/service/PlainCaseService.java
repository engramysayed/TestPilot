package delivery.portal.service;

import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persist + convert plain-English cases for Execute.
 */
@Service
public class PlainCaseService {
    private final PortalStore store;
    private final PlainCaseDiskStore disk = new PlainCaseDiskStore();

    public PlainCaseService(PortalStore store) {
        this.store = store;
    }

    public List<Map<String, Object>> list(String projectId) throws Exception {
        return disk.list(store.projectDiskRoot(projectId));
    }

    public Optional<Map<String, Object>> get(String projectId, String caseId) throws Exception {
        return disk.load(store.projectDiskRoot(projectId), caseId);
    }

    public Map<String, Object> create(String projectId, String title, List<String> lines) throws Exception {
        String caseId = "pc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return disk.saveNew(store.projectDiskRoot(projectId), caseId, title, lines);
    }

    public Map<String, Object> update(String projectId, String caseId, String title, List<String> lines)
            throws Exception {
        return disk.update(store.projectDiskRoot(projectId), caseId, title, lines);
    }

    /**
     * Convert stored plain-case lines into a one-row Keel workbook for the existing execute runner.
     */
    public Path materializeWorkbook(String projectId, String caseId) throws Exception {
        Map<String, Object> plain = disk.load(store.projectDiskRoot(projectId), caseId)
                .orElseThrow(() -> new PlainCaseDiskStore.NotFoundException("Plain case not found: " + caseId));
        ManualTestCase tc = toManualTestCase(plain);
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads", projectId, "plain-cases");
        Files.createDirectories(dir);
        Path excelPath = dir.resolve(caseId.trim() + "-" + UUID.randomUUID() + ".xlsx");
        ManualTcExcelWriter.write(excelPath, List.of(tc));
        return excelPath;
    }

    @SuppressWarnings("unchecked")
    public static ManualTestCase toManualTestCase(Map<String, Object> plain) {
        String plainId = str(plain.get("id"));
        if (plainId.isBlank()) {
            plainId = "pc_unknown";
        }
        // Excel/execute gate requires TC_<digits> or TC_<ALNUM_UNDERSCORE>
        String id = toExecuteTcId(plainId);
        String title = str(plain.get("title"));
        if (title.isBlank()) {
            title = plainId;
        }
        List<String> lines = new ArrayList<>();
        Object linesObj = plain.get("lines");
        if (linesObj instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) {
                    continue;
                }
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) {
                    lines.add(s);
                }
            }
        }
        if (lines.isEmpty()) {
            throw new PlainCaseDiskStore.BadRequestException("lines must contain at least one non-blank entry");
        }
        List<String> stepLines = new ArrayList<>();
        List<String> expectLines = new ArrayList<>();
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("expect ") || lower.equals("expect") || lower.startsWith("expect\t")) {
                String rest = line.length() >= 6 ? line.substring(6).trim() : "";
                expectLines.add(rest.isEmpty() ? line : rest);
            } else {
                stepLines.add(line);
            }
        }
        if (stepLines.isEmpty()) {
            // All expect lines — keep them as steps so the runner still has something to do.
            stepLines.addAll(lines);
            expectLines.clear();
        }
        String steps = numberLines(stepLines);
        String expected = expectLines.isEmpty() ? "Observe results" : numberLines(expectLines);
        return new ManualTestCase(
                id,
                title,
                "",
                steps,
                expected,
                "P2",
                "plain-case",
                "",
                "",
                "EXECUTE",
                ""
        );
    }

    private static String numberLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String line = lines.get(i);
            if (line.matches("^\\d+\\.\\s.*")) {
                sb.append(line);
            } else {
                sb.append(i + 1).append(". ").append(line);
            }
        }
        return sb.toString();
    }


    static String toExecuteTcId(String plainId) {
        String raw = plainId == null ? "" : plainId.trim();
        String alnum = raw.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT);
        if (alnum.isBlank()) {
            alnum = "PLAIN";
        }
        if (alnum.length() > 40) {
            alnum = alnum.substring(0, 40);
        }
        if (alnum.matches("^TC_[A-Z0-9_]+$")) {
            return alnum;
        }
        return "TC_" + alnum;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
