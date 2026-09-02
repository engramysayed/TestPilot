package delivery.excel;

public class InvalidExcelTemplateException extends RuntimeException {
    private final String errorCode;

    public InvalidExcelTemplateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
