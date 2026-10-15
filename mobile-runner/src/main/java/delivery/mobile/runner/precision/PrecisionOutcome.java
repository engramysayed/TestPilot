package delivery.mobile.runner.precision;

public record PrecisionOutcome(
        String code,
        String chosenId,
        String usedProvider,
        String fallbackReason,
        boolean countsAsCursor
) {
    static PrecisionOutcome chosen(String id, String provider) {
        boolean cursor = "cursor".equalsIgnoreCase(provider);
        return new PrecisionOutcome("", id, provider, "", cursor);
    }

    static PrecisionOutcome rejected(String code) {
        return new PrecisionOutcome(code, "", "", "", false);
    }

    static PrecisionOutcome fallback(String reason) {
        return new PrecisionOutcome("", "", "keel", reason, false);
    }
}
