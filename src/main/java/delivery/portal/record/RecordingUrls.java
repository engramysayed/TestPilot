package delivery.portal.record;

import delivery.net.TargetNetworkPolicy;

import java.net.URI;

/** Decides which address a recording may open. */
public final class RecordingUrls {
    private RecordingUrls() {
    }

    public static String resolve(String approvedOrigin, String requested, boolean useProjectSite) {
        if (useProjectSite) {
            if (approvedOrigin == null || approvedOrigin.isBlank()) {
                throw new IllegalArgumentException("Set the project website in Settings first.");
            }
            return check(approvedOrigin, withScheme(approvedOrigin.trim()));
        }
        String typed = requested == null ? "" : requested.trim();
        if (!typed.matches("(?i)https?://\\S+")) {
            throw new IllegalArgumentException("Type a full http or https address.");
        }
        return check(approvedOrigin, typed);
    }

    /** False when the live page has left the project's allowed website. */
    public static boolean allowed(String approvedOrigin, String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String cleaned = safeUrl(url);
            if (!cleaned.matches("(?i)https?://\\S+")) {
                return false;
            }
            return TargetNetworkPolicy.forJob(approvedOrigin == null ? "" : approvedOrigin)
                    .inspect(cleaned).allowed();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static String safeUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().replaceAll("[\\r\\n]", "");
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            URI uri = URI.create(trimmed);
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                        uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            }
            return uri.toString();
        } catch (Exception e) {
            return trimmed;
        }
    }

    private static String check(String approvedOrigin, String url) {
        String cleaned = safeUrl(url);
        if (!cleaned.matches("(?i)https?://\\S+")) {
            throw new IllegalArgumentException("Type a full http or https address.");
        }
        TargetNetworkPolicy.Decision decision = TargetNetworkPolicy
                .forJob(approvedOrigin == null ? "" : approvedOrigin)
                .inspect(cleaned);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(blockedMessage(decision.reason()));
        }
        return cleaned;
    }

    static String blockedMessage(String reason) {
        if (reason == null || reason.isBlank()) {
            return "That address is blocked.";
        }
        if ("host is not the approved origin".equals(reason)) {
            return "That address has to be this project's website.";
        }
        if (reason.contains("loopback") || reason.contains("blocked address") || reason.contains("private")) {
            return "That address is on a private network and is blocked.";
        }
        return "That address is blocked (" + reason + ").";
    }

    private static String withScheme(String raw) {
        if (raw.regionMatches(true, 0, "http://", 0, 7) || raw.regionMatches(true, 0, "https://", 0, 8)) {
            return raw;
        }
        return "https://" + raw;
    }
}
