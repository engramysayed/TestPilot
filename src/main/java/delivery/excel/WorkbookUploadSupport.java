package delivery.excel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Materialize an uploaded Keel workbook as .xlsx whether the browser sent .xlsx or .csv.
 * Generate downloads CSV; Automate/Execute historically accepted only .xlsx — this bridges both.
 */
public final class WorkbookUploadSupport {
    private WorkbookUploadSupport() {
    }

    /**
     * Write {@code destXlsx} from upload bytes. CSV is parsed then rewritten as Keel .xlsx.
     *
     * @return destXlsx
     */
    public static Path materializeExcel(String originalFilename, byte[] bytes, Path destXlsx)
            throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidExcelTemplateException("INVALID_EXCEL", "excel file is required");
        }
        Files.createDirectories(destXlsx.getParent());
        if (looksLikeCsv(originalFilename, bytes)) {
            String csv = new String(bytes, StandardCharsets.UTF_8);
            try {
                List<ManualTestCase> cases = GeneratedTcCsvParser.parse(csv);
                ManualTcExcelWriter.write(destXlsx, cases);
            } catch (IllegalArgumentException e) {
                throw new InvalidExcelTemplateException(
                        "INVALID_EXCEL",
                        "Could not parse CSV as Keel test cases: " + e.getMessage());
            }
            return destXlsx;
        }
        Files.write(destXlsx, bytes);
        return destXlsx;
    }

    static boolean looksLikeCsv(String originalFilename, byte[] bytes) {
        String name = originalFilename == null ? "" : originalFilename.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return true;
        }
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return false;
        }
        // ZIP/OOXML magic — treat as workbook even with a weird filename
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            return false;
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.UTF_8)
                .replace("\uFEFF", "")
                .trim()
                .toUpperCase(Locale.ROOT);
        return head.startsWith("TC_ID") || head.startsWith("TCID");
    }
}
