package delivery.portal.service;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.portal.model.JobRecord;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Builds an Excel workbook of execute results with embedded step screenshots.
 */
@Service
public class ExecuteResultsExcelService {
    private final ExecuteRunService executeRuns;

    public ExecuteResultsExcelService(ExecuteRunService executeRuns) {
        this.executeRuns = executeRuns;
    }

    public Optional<byte[]> buildWorkbook(String jobId, Long ownerUserId) throws Exception {
        Optional<JobRecord> jobOpt = executeRuns.requireOwnedExecuteJob(jobId, ownerUserId);
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }
        JobRecord job = jobOpt.get();
        List<TcDraft> drafts = executeRuns.readDraftsForJob(jobId);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeSummary(workbook, jobId, drafts);
            for (TcDraft draft : drafts) {
                writeTcSheet(workbook, job, draft);
            }
            workbook.write(out);
            return Optional.of(out.toByteArray());
        }
    }

    private static void writeSummary(XSSFWorkbook workbook, String jobId, List<TcDraft> drafts) {
        Sheet sheet = workbook.createSheet("Summary");
        Row header = sheet.createRow(0);
        String[] cols = {
                "TC_ID", "Title", "QA", "Proven steps", "Blocker step", "What went wrong", "Technical detail"
        };
        for (int i = 0; i < cols.length; i++) {
            header.createCell(i).setCellValue(cols[i]);
        }
        int r = 1;
        for (TcDraft d : drafts) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(nullToEmpty(d.tcId()));
            row.createCell(1).setCellValue(nullToEmpty(d.title()));
            row.createCell(2).setCellValue(ExecuteRunService.qaStatus(d.status()));
            row.createCell(3).setCellValue(d.provenSteps() == null ? 0 : d.provenSteps().size());
            row.createCell(4).setCellValue(d.blockerStepIndex());
            row.createCell(5).setCellValue(FailureReasonHumanizer.forUser(d.failureReason()));
            row.createCell(6).setCellValue(nullToEmpty(d.failureReason()));
        }
        Row meta = sheet.createRow(r + 1);
        meta.createCell(0).setCellValue("Execute job");
        meta.createCell(1).setCellValue(jobId);
        for (int i = 0; i < cols.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeTcSheet(XSSFWorkbook workbook, JobRecord job, TcDraft draft) throws IOException {
        String sheetName = safeSheetName(draft.tcId());
        XSSFSheet sheet = workbook.createSheet(sheetName);
        Row info0 = sheet.createRow(0);
        info0.createCell(0).setCellValue("Title");
        info0.createCell(1).setCellValue(nullToEmpty(draft.title()));
        Row info1 = sheet.createRow(1);
        info1.createCell(0).setCellValue("QA");
        info1.createCell(1).setCellValue(ExecuteRunService.qaStatus(draft.status()));
        Row info2 = sheet.createRow(2);
        info2.createCell(0).setCellValue("What went wrong");
        info2.createCell(1).setCellValue(FailureReasonHumanizer.forUser(draft.failureReason()));

        Row header = sheet.createRow(4);
        header.createCell(0).setCellValue("Step #");
        header.createCell(1).setCellValue("Action");
        header.createCell(2).setCellValue("Locator");
        header.createCell(3).setCellValue("Value");
        header.createCell(4).setCellValue("Screenshot");

        List<ProvenStep> steps = draft.provenSteps() == null ? List.of() : draft.provenSteps();
        int rowIndex = 5;
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        for (int i = 0; i < steps.size(); i++) {
            ProvenStep step = steps.get(i);
            Row row = sheet.createRow(rowIndex);
            row.setHeightInPoints(90);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(nullToEmpty(step.action())
                    + (step.pageName() == null || step.pageName().isBlank() ? "" : " @" + step.pageName()));
            String locator = nullToEmpty(step.locatorStrategy()) + "=" + nullToEmpty(step.locatorValue());
            row.createCell(2).setCellValue(locator);
            row.createCell(3).setCellValue(nullToEmpty(step.value()));
            String shot = step.screenshotRelPath();
            if (shot != null && !shot.isBlank()) {
                Optional<Path> file = executeRuns.resolveScreenshot(job.getJobId(), draft.tcId(), shot);
                if (file.isPresent()) {
                    embedPng(workbook, drawing, file.get(), 4, rowIndex);
                    row.createCell(4).setCellValue(shot);
                } else {
                    row.createCell(4).setCellValue("(missing: " + shot + ")");
                }
            }
            rowIndex++;
        }

        if ("FAIL".equals(ExecuteRunService.qaStatus(draft.status()))) {
            Optional<Path> failure = executeRuns.resolveScreenshot(job.getJobId(), draft.tcId(), "failure.png");
            if (failure.isPresent()) {
                Row row = sheet.createRow(rowIndex);
                row.setHeightInPoints(90);
                row.createCell(0).setCellValue("FAIL");
                row.createCell(1).setCellValue(nullToEmpty(draft.blockerIntent()));
                row.createCell(4).setCellValue("failure.png");
                embedPng(workbook, drawing, failure.get(), 4, rowIndex);
            }
        }

        sheet.setColumnWidth(0, 2500);
        sheet.setColumnWidth(1, 9000);
        sheet.setColumnWidth(2, 9000);
        sheet.setColumnWidth(3, 6000);
        sheet.setColumnWidth(4, 12000);
    }

    private static void embedPng(
            XSSFWorkbook workbook,
            XSSFDrawing drawing,
            Path png,
            int col,
            int row
    ) throws IOException {
        byte[] bytes = Files.readAllBytes(png);
        int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
        ClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, col, row, col + 1, row + 1);
        drawing.createPicture(anchor, pictureIdx);
    }

    private static String safeSheetName(String tcId) {
        String raw = tcId == null || tcId.isBlank() ? "TC" : tcId.trim();
        String cleaned = raw.replaceAll("[\\\\/*?\\[\\]:]", "_");
        if (cleaned.length() > 31) {
            cleaned = cleaned.substring(0, 31);
        }
        return cleaned;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
