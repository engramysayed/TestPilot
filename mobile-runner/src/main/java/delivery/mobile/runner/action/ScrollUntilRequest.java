package delivery.mobile.runner.action;

/**
 * SCROLL_UNTIL parameters (M3-04). Caps prevent infinite scroll/heal loops.
 */
public record ScrollUntilRequest(
        ScrollDirection direction,
        int maxSwipes,
        int noProgressLimit,
        int visibilityPercentage,
        boolean centerElement,
        long maxDurationMs
) {
    public static final int DEFAULT_MAX_SWIPES = 5;
    public static final int DEFAULT_NO_PROGRESS = 2;
    public static final int DEFAULT_VISIBILITY_PCT = 50;

    public ScrollUntilRequest {
        direction = direction == null ? ScrollDirection.DOWN : direction;
        if (maxSwipes < 1) {
            maxSwipes = DEFAULT_MAX_SWIPES;
        }
        if (maxSwipes > 20) {
            maxSwipes = 20; // hard ceiling — no infinite loop
        }
        if (noProgressLimit < 1) {
            noProgressLimit = DEFAULT_NO_PROGRESS;
        }
        if (visibilityPercentage < 0) {
            visibilityPercentage = 0;
        }
        if (visibilityPercentage > 100) {
            visibilityPercentage = 100;
        }
        if (maxDurationMs < 1) {
            maxDurationMs = 30_000;
        }
    }

    public static ScrollUntilRequest defaults(ScrollDirection dir) {
        return new ScrollUntilRequest(
                dir, DEFAULT_MAX_SWIPES, DEFAULT_NO_PROGRESS, DEFAULT_VISIBILITY_PCT, false, 30_000);
    }

    public String limitTuningMessage(String reason) {
        return "SCROLL_LIMIT (" + reason + "): maxSwipes=" + maxSwipes
                + " visibilityPercentage=" + visibilityPercentage
                + " centerElement=" + centerElement
                + " direction=" + direction
                + " noProgressLimit=" + noProgressLimit;
    }
}
