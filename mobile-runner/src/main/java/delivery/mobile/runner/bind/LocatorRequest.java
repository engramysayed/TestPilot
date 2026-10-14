package delivery.mobile.runner.bind;

/**
 * Author / override locator request. Selector value bytes are preserved exactly.
 */
public final class LocatorRequest {
    private final LocatorStrategy strategy;
    private final String value;
    private final LocatorOrigin origin;
    private final ActionKind action;
    private final String contextKind; // NATIVE | WEBVIEW
    private final Integer expectedMatches;

    public LocatorRequest(
            LocatorStrategy strategy,
            String value,
            LocatorOrigin origin,
            ActionKind action,
            String contextKind,
            Integer expectedMatches
    ) {
        this.strategy = strategy;
        this.value = value == null ? "" : value;
        this.origin = origin == null ? LocatorOrigin.HIERARCHY : origin;
        this.action = action == null ? ActionKind.ANY : action;
        this.contextKind = contextKind == null || contextKind.isBlank() ? "NATIVE" : contextKind.trim().toUpperCase();
        this.expectedMatches = expectedMatches;
    }

    public static LocatorRequest user(LocatorStrategy strategy, String value, ActionKind action) {
        return new LocatorRequest(strategy, value, LocatorOrigin.USER, action, "NATIVE", 1);
    }

    public static LocatorRequest hierarchy(LocatorStrategy strategy, String value, ActionKind action) {
        return new LocatorRequest(strategy, value, LocatorOrigin.HIERARCHY, action, "NATIVE", 1);
    }

    public LocatorStrategy strategy() { return strategy; }
    public String value() { return value; }
    public LocatorOrigin origin() { return origin; }
    public ActionKind action() { return action; }
    public String contextKind() { return contextKind; }
    public Integer expectedMatches() { return expectedMatches; }

    public LocatorRequest withOrigin(LocatorOrigin o) {
        return new LocatorRequest(strategy, value, o, action, contextKind, expectedMatches);
    }
}
