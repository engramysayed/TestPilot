package delivery.excel;

public final class ExcelValidationMessages {
    public static final String INVALID_EXCEL = "INVALID_EXCEL";

    private ExcelValidationMessages() {
    }

    public static String missingColumn(String column) {
        return "Missing required column: " + column;
    }

    public static String blankTcId(int rowNumber) {
        return "Blank TC_ID at data row " + rowNumber;
    }

    public static String duplicateTcId(String tcId) {
        return "Duplicate TC_ID: " + tcId;
    }

    public static String noDataRows() {
        return "Excel contains no data rows";
    }
}
