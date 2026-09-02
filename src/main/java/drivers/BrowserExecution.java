package drivers;

import utils.Config.AppConfigProvider;

/**
 * Resolves whether the local Chrome/Edge session should run headless.
 * Prefers {@code BROWSER_HEADLESS=true}, then {@code EXECUTION_TYPE=HEADLESS|LocalHeadless|Remote}.
 */
public final class BrowserExecution {

    private BrowserExecution() {
    }

    public static String executionType() {
        String raw = AppConfigProvider.get().executionType();
        return raw == null ? "LOCAL" : raw.trim();
    }

    public static boolean isHeadless() {
        if (AppConfigProvider.get().browserHeadless()) {
            return true;
        }
        String type = executionType();
        return "HEADLESS".equalsIgnoreCase(type)
                || "LocalHeadless".equalsIgnoreCase(type)
                || "Remote".equalsIgnoreCase(type);
    }

    public static boolean isLocalDriver() {
        String type = executionType();
        return "LOCAL".equalsIgnoreCase(type)
                || "HEADLESS".equalsIgnoreCase(type)
                || "LocalHeadless".equalsIgnoreCase(type)
                || AppConfigProvider.get().browserHeadless();
    }
}
