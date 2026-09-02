package delivery.vision;

public record DesignCompareResult(
        DesignCompareStatus status,
        double confidence,
        String observation,
        String evidence,
        String error
) {
    public static DesignCompareResult uncertain(String reason) {
        return new DesignCompareResult(
                DesignCompareStatus.UNCERTAIN,
                0.5,
                "",
                "",
                reason == null ? "" : reason);
    }

    public static DesignCompareResult skipped(String reason) {
        return new DesignCompareResult(
                DesignCompareStatus.SKIPPED,
                0.0,
                "",
                reason == null ? "" : reason,
                "");
    }
}
