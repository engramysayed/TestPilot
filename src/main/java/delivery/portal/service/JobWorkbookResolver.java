package delivery.portal.service;

import delivery.excel.ExcelTcReader;
import delivery.excel.InvalidExcelTemplateException;
import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.excel.WorkbookUploadSupport;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Shared resolve path for Automate/Execute uploads: library subset, upload, or mix (upload wins).
 */
public final class JobWorkbookResolver {
    private JobWorkbookResolver() {
    }

    public static Path resolve(
            GeneratedWorkbookService workbooks,
            String projectId,
            boolean useLibrary,
            List<String> tcIds,
            MultipartFile excel
    ) throws Exception {
        List<String> selected = WorkbookJobMaterializer.parseTcIdParams(tcIds);
        boolean hasUpload = excel != null && !excel.isEmpty();
        List<ManualTestCase> uploadCases = List.of();
        if (hasUpload) {
            Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads", projectId,
                    UUID.randomUUID() + "-upload.xlsx");
            Files.createDirectories(tmp.getParent());
            WorkbookUploadSupport.materializeExcel(excel.getOriginalFilename(), excel.getBytes(), tmp);
            try {
                uploadCases = new ExcelTcReader().read(tmp);
            } finally {
                Files.deleteIfExists(tmp);
            }
        }

        if (!useLibrary && !hasUpload) {
            throw new InvalidExcelTemplateException("INVALID_EXCEL", "excel file is required");
        }
        if (useLibrary && !workbooks.hasWorkbook(projectId) && !hasUpload) {
            throw new IllegalStateException("NO_GENERATED_WORKBOOK");
        }

        // Upload-only with no library and no tcId filter
        if (!useLibrary && hasUpload && selected.isEmpty()) {
            Path uploadDir = Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads", projectId);
            Files.createDirectories(uploadDir);
            Path excelPath = uploadDir.resolve(UUID.randomUUID() + ".xlsx");
            ManualTcExcelWriter.write(excelPath, uploadCases);
            return excelPath;
        }

        try {
            return workbooks.materializeForJob(
                    projectId,
                    selected.isEmpty() ? null : selected,
                    uploadCases
            );
        } catch (IllegalStateException e) {
            if ("NO_CASES_FOR_JOB".equals(e.getMessage())) {
                throw new InvalidExcelTemplateException("INVALID_EXCEL",
                        "No test cases to run — check selected TCs or upload");
            }
            throw e;
        }
    }
}
