package delivery.mobile.runner.vision;

/** Screenshot geometry captured at decision time. */
public record VisionFrame(
        int width,
        int height,
        int rotation,
        int insetLeft,
        int insetTop,
        String contextKind,
        boolean blank,
        boolean secure,
        boolean redactionEstablished
) {}
