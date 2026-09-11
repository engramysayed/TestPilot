package delivery.hunt;

import java.util.Locale;

/** Helpers for login-feature detection and happy-strategy auto-advance. */
public final class HuntFeatureHints {
    private HuntFeatureHints() {
    }

    public static boolean looksLikeLoginFeature(String baseUrl, String userStory, String briefMd) {
        String blob = (nullToEmpty(baseUrl) + " " + nullToEmpty(userStory) + " " + nullToEmpty(briefMd))
                .toLowerCase(Locale.ROOT);
        return blob.contains("login") || blob.contains("sign in") || blob.contains("signin")
                || blob.contains("sign-in") || blob.contains("/login");
    }

    public static boolean looksLikeLoginUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("/login") || u.contains("signin") || u.contains("sign-in")
                || u.contains("/auth") || u.contains("logon");
    }

    /**
     * Advance off happy when stuck on login without progress.
     */
    public static boolean shouldAdvanceHappy(
            String mode,
            int happyStreak,
            String currentUrl,
            boolean hasCredentials,
            boolean leftLoginOnce
    ) {
        if (!"happy".equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            return false;
        }
        if (happyStreak < 2) {
            return false;
        }
        if (leftLoginOnce) {
            return false;
        }
        if (!looksLikeLoginUrl(currentUrl)) {
            return false;
        }
        // Blocked on login screen for multiple happy cycles — advance even with creds
        // (auto-login / token login may have failed).
        return true;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
