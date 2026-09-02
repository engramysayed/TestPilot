package delivery.vision;

public record VisionAssertionResult(
        VisionAssertionStatus status,
        double confidence,
        String observation,
        String evidence,
        String error
) {
    public static VisionAssertionResult of(
            VisionAssertionStatus status,
            double confidence,
            String observation,
            String evidence) {
        return new VisionAssertionResult(
                status == null ? VisionAssertionStatus.UNCERTAIN : status,
                confidence,
                observation == null ? "" : observation,
                evidence == null ? "" : evidence,
                null);
    }

    public static VisionAssertionResult uncertain(String error) {
        return new VisionAssertionResult(
                VisionAssertionStatus.UNCERTAIN,
                0.5,
                "",
                "",
                error == null ? "uncertain" : error);
    }

    public boolean passed() {
        return status == VisionAssertionStatus.PASS;
    }
}
