package delivery.excel;

/** Normalizes manual TC text fields from LLM CSV / Excel. */
public final class ExcelStepText {
    private ExcelStepText() {
    }

    /**
     * LLMs often emit the two-character sequence {@code \n} inside CSV cells instead of real
     * line breaks. Step parsing splits on actual newlines only.
     */
    public static String normalizeMultiline(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n");
    }
}
