package delivery.job;

import java.util.Locale;

/**
 * Detects a Selenium session whose window is gone so prove can restart instead of
 * looping on empty HTML.
 */
public final class DeadBrowserSession {
    private DeadBrowserSession() {
    }

    public static boolean isDead(Throwable error) {
        return error != null && isDead(error.getMessage());
    }

    public static boolean isDead(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        return m.contains("no such window")
                || m.contains("target window already closed")
                || m.contains("web view not found")
                || m.contains("invalid session id")
                || m.contains("chrome not reachable")
                || m.contains("disconnected: not connected to devtools");
    }
}
