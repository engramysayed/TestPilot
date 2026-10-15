package delivery.mobile.runner.vision;

public record VisionOutcome(
        boolean tap,
        boolean passed,
        boolean retry,
        String code,
        int x,
        int y,
        String model
) {
    static VisionOutcome blocked(String code) {
        return new VisionOutcome(false, false, false, code, -1, -1, "");
    }

    static VisionOutcome tap(int x, int y, String model) {
        return new VisionOutcome(true, true, false, "", x, y, model);
    }
}
