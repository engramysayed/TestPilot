package delivery.mobile.transport;

import java.util.regex.Pattern;

/**
 * Redacts runner tokens and secret envelopes from request/diagnostic logs (M1-05).
 */
public final class RunnerTransportRedactor {
    public static final String MASK = "***";

    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Authorization\\s*[:=]\\s*Bearer\\s+)\\S+");
    private static final Pattern TP_RUN = Pattern.compile("\\btp_run_[A-Za-z0-9_\\-]{8,}\\b");
    private static final Pattern SECRET_ENV = Pattern.compile(
            "(?i)(\"(?:ciphertext|mac|secretEnvelope|secretEnvelopeRef|password|passwd|api[_-]?key)\"\\s*:\\s*\")([^\"]+)(\")");
    private static final Pattern HEADER_TOKEN = Pattern.compile(
            "(?i)((?:X-Keel-Signature|X-Keel-Runner-Signature)\\s*[:=]\\s*)\\S+");

    private RunnerTransportRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String out = BEARER.matcher(text).replaceAll("$1" + MASK);
        out = TP_RUN.matcher(out).replaceAll(MASK);
        out = SECRET_ENV.matcher(out).replaceAll("$1" + MASK + "$3");
        out = HEADER_TOKEN.matcher(out).replaceAll("$1" + MASK);
        return out;
    }

    public static boolean containsTokenLeak(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return TP_RUN.matcher(text).find()
                || BEARER.matcher(text).find() && !text.contains("Bearer " + MASK);
    }
}
