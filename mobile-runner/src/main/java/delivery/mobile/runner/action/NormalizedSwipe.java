package delivery.mobile.runner.action;

/**
 * Bounded normalized swipe geometry in 0..1 screen space (M3-04).
 * Resolved to device px at execution using current window size — never raw resolution constants.
 */
public record NormalizedSwipe(
        double startX,
        double startY,
        double endX,
        double endY,
        long durationMs
) {
    public NormalizedSwipe {
        startX = clamp01(startX);
        startY = clamp01(startY);
        endX = clamp01(endX);
        endY = clamp01(endY);
        if (durationMs < 1) {
            durationMs = 1;
        }
        if (durationMs > 60_000) {
            durationMs = 60_000;
        }
    }

    public int[] toDevicePx(int windowW, int windowH) {
        if (windowW <= 0 || windowH <= 0) {
            throw new IllegalArgumentException("window size required to resolve swipe");
        }
        return new int[] {
                (int) Math.round(startX * (windowW - 1)),
                (int) Math.round(startY * (windowH - 1)),
                (int) Math.round(endX * (windowW - 1)),
                (int) Math.round(endY * (windowH - 1)),
                (int) durationMs
        };
    }

    public static NormalizedSwipe forDirection(ScrollDirection direction, double travel) {
        double t = Math.min(0.8, Math.max(0.1, travel));
        return switch (direction) {
            case UP -> new NormalizedSwipe(0.5, 0.7, 0.5, 0.7 - t, 300);
            case DOWN -> new NormalizedSwipe(0.5, 0.3, 0.5, 0.3 + t, 300);
            case LEFT -> new NormalizedSwipe(0.8, 0.5, 0.8 - t, 0.5, 300);
            case RIGHT -> new NormalizedSwipe(0.2, 0.5, 0.2 + t, 0.5, 300);
        };
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0.5;
        }
        return Math.min(1.0, Math.max(0.0, v));
    }
}
