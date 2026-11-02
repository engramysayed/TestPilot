package delivery.portal.service;

import delivery.excel.ExcelTcReader;
import delivery.excel.InvalidExcelTemplateException;
import delivery.excel.ManualTestCase;
import delivery.excel.WorkbookUploadSupport;
import delivery.knowledge.KnowledgePack;
import delivery.knowledge.KnowledgePackSource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ingest + update knowledge packs on the project disk store.
 */
@Service
public class KnowledgePackService {
    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;
    private final KnowledgePackDiskStore disk = new KnowledgePackDiskStore();

    public KnowledgePackService(PortalStore store, GeneratedWorkbookService workbooks) {
        this.store = store;
        this.workbooks = workbooks;
    }

    public List<KnowledgePack> list(String projectId) throws Exception {
        return disk.list(store.projectDiskRoot(projectId), false);
    }

    public Optional<KnowledgePack> get(String projectId, String packId) throws Exception {
        return disk.load(store.projectDiskRoot(projectId), packId, true);
    }

    public KnowledgePack ingestLibrary(String projectId, List<String> tcIds, String title, int batchSize)
            throws Exception {
        if (tcIds == null || tcIds.isEmpty()) {
            throw new BadIngestException("tcIds is required");
        }
        if (!workbooks.hasWorkbook(projectId)) {
            throw new BadIngestException("No generated workbook / TC library for this project");
        }
        List<ManualTestCase> all = workbooks.readCases(projectId);
        Set<String> want = new LinkedHashSet<>();
        for (String id : tcIds) {
            if (id != null && !id.isBlank()) {
                want.add(id.trim());
            }
        }
        if (want.isEmpty()) {
            throw new BadIngestException("tcIds is required");
        }
        List<ManualTestCase> selected = new ArrayList<>();
        for (ManualTestCase tc : all) {
            if (tc.tcId() != null && want.contains(tc.tcId().trim())) {
                selected.add(tc);
            }
        }
        if (selected.isEmpty()) {
            throw new BadIngestException("None of the requested tcIds were found in the library");
        }
        String md = casesToMarkdown(selected);
        String packTitle = (title == null || title.isBlank())
                ? "Library pack (" + selected.size() + " cases)"
                : title.trim();
        return disk.saveNew(
                store.projectDiskRoot(projectId),
                projectId,
                newPackId(),
                packTitle,
                KnowledgePackSource.LIBRARY,
                batchSize,
                md
        );
    }

    public KnowledgePack ingestUploadTc(String projectId, MultipartFile file, String title, int batchSize)
            throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BadIngestException("file is required");
        }
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "keel-knowledge", projectId,
                UUID.randomUUID() + "-upload.xlsx");
        Files.createDirectories(tmp.getParent());
        List<ManualTestCase> cases;
        try {
            WorkbookUploadSupport.materializeExcel(file.getOriginalFilename(), file.getBytes(), tmp);
            cases = new ExcelTcReader().read(tmp);
        } catch (InvalidExcelTemplateException e) {
            throw new BadIngestException(e.getMessage() == null ? "Invalid TC file" : e.getMessage());
        } finally {
            Files.deleteIfExists(tmp);
        }
        if (cases == null || cases.isEmpty()) {
            throw new BadIngestException("No test cases found in upload");
        }
        String md = casesToMarkdown(cases);
        String packTitle = (title == null || title.isBlank())
                ? defaultTitleFromFilename(file.getOriginalFilename(), "Uploaded TC pack")
                : title.trim();
        return disk.saveNew(
                store.projectDiskRoot(projectId),
                projectId,
                newPackId(),
                packTitle,
                KnowledgePackSource.UPLOAD_TC,
                batchSize,
                md
        );
    }

    public KnowledgePack ingestUploadMd(String projectId, MultipartFile file, String title, int batchSize)
            throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BadIngestException("file is required");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.isEmpty() && !lower.endsWith(".md") && !lower.endsWith(".markdown") && !lower.endsWith(".txt")) {
            throw new BadIngestException("Expected a .md markdown file");
        }
        String md = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (md.isBlank()) {
            throw new BadIngestException("Markdown file is empty");
        }
        String packTitle = (title == null || title.isBlank())
                ? defaultTitleFromFilename(name, "Uploaded markdown pack")
                : title.trim();
        return disk.saveNew(
                store.projectDiskRoot(projectId),
                projectId,
                newPackId(),
                packTitle,
                KnowledgePackSource.UPLOAD_MD,
                batchSize,
                md
        );
    }

    public KnowledgePack update(String projectId, String packId, String title, Integer batchSize, String markdown)
            throws Exception {
        return disk.update(store.projectDiskRoot(projectId), packId, title, batchSize, markdown);
    }

    static String casesToMarkdown(List<ManualTestCase> cases) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Knowledge pack (test cases)\n\n");
        for (ManualTestCase tc : cases) {
            if (tc == null) {
                continue;
            }
            String id = tc.tcId() == null ? "" : tc.tcId().trim();
            String title = tc.title() == null ? "" : tc.title().trim();
            sb.append("## ").append(id.isBlank() ? "Case" : id);
            if (!title.isBlank()) {
                sb.append(" — ").append(title);
            }
            sb.append("\n\n");
            appendField(sb, "Preconditions", tc.preconditions());
            appendField(sb, "Steps", tc.steps());
            appendField(sb, "Expected result", tc.expectedResult());
            appendField(sb, "Priority", tc.priority());
            appendField(sb, "Tags", tc.tags());
            appendField(sb, "Visual assertion", tc.visualAssertion());
            appendField(sb, "Test data", tc.testData());
            appendField(sb, "Keel path", tc.keelPath());
            appendField(sb, "Call before", tc.callBefore());
        }
        return sb.toString().trim() + "\n";
    }

    private static void appendField(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append("**").append(label).append(":**\n\n");
        sb.append(value.trim()).append("\n\n");
    }

    private static String newPackId() {
        return "kp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String defaultTitleFromFilename(String filename, String fallback) {
        if (filename == null || filename.isBlank()) {
            return fallback;
        }
        String base = Path.of(filename).getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base.isBlank() ? fallback : base;
    }

    public static class BadIngestException extends RuntimeException {
        public BadIngestException(String message) {
            super(message);
        }
    }
}
