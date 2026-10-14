package delivery.job;

import java.util.Locale;

/**
 * Retry-budget classification of one failed attempt (honest retry metrics, DEEP_RESEARCH item 6).
 *
 * <p>Unlike {@link FailureClassifier#suggest(String)} (triage UI, defaults unknown to INFRASTRUCTURE),
 * this classifier defaults unknown text to {@link #TEST}: a failure we cannot prove to be environmental
 * must consume the test's retry budget and must never be hidden from the flaky count (no-false-green).
 */
public enum RetryCategory {
    /** The product/test step failed (locator, assertion, bind, heal exhausted, unknown). Consumes test budget. */
    TEST,
    /** Environment failed (browser crash, network, lease, model unavailable). Separate infra budget. */
    INFRA,
    /**
     * Login / credential / environment-configuration problem (bad password, missing secret, wrong base URL).
     * Deterministic: never retried, excluded from pass-rate denominators like INFRA.
     */
    CONFIG,
    /** Engine-driven page recovery ("retry bind" after navigate recovery). Consumes test budget, not flaky. */
    RECOVERY;

    private static final String[] RECOVERY_MARKERS = {
            "recovered to excel open-path",
    };

    private static final String[] CONFIG_MARKERS = {
            "login_failed", "login failed", "invalid credentials", "invalid username or password",
            "wrong password", "missing credential", "no credentials", "credentials missing",
            "password not set", "secret not found", "secret_missing", "missing secret",
            "loginsecretresolver", "base url not set", "base url is blank", "invalid base url",
            "config_error", "missing config", "env var", "environment variable", "account locked",
    };

    private static final String[] INFRA_MARKERS = {
            // browser / driver
            "browser session died", "session deleted", "invalid session id", "no such window",
            "chrome not reachable", "devtools", "disconnected: not connected", "unable to receive message from renderer",
            "tab crashed", "session not created", "deadbrowsersession",
            // network
            "net::err_", "connection refused", "connection reset", "unknownhost", "unknown host",
            "no route to host", "name or service not known", "sockettimeout", "connect timed out",
            "env_network",
            // durable job / lease
            "lease_lost", "lease lost", "fenced", "interrupted_uncertain",
            // model / provider availability (not "model said no")
            "model unavailable", "model not found", "ollama unavailable", "ollama is not running",
            "503 service unavailable", "http 503", "http 502", "http 504", "rate limit", "429 too many",
            "timeout waiting for model", "provider unavailable",
            // host
            "no space left on device", "outofmemoryerror",
    };

    /** Classify a failure reason string (exception message or engine reason). Null/blank → TEST. */
    public static RetryCategory classify(String reason) {
        if (reason == null || reason.isBlank()) {
            return TEST;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        for (String m : RECOVERY_MARKERS) {
            if (lower.contains(m)) {
                return RECOVERY;
            }
        }
        // Explicit test verdicts win over infra words in the same message (e.g. "HEAL_EXHAUSTED ... timeout").
        if (lower.startsWith("heal_exhausted") || lower.contains("assert") || lower.contains("no dom candidate")
                || lower.startsWith("ambiguous:")) {
            return TEST;
        }
        for (String m : CONFIG_MARKERS) {
            if (lower.contains(m)) {
                return CONFIG;
            }
        }
        for (String m : INFRA_MARKERS) {
            if (lower.contains(m)) {
                return INFRA;
            }
        }
        return TEST;
    }

    /** Classify a throwable, walking the cause chain (DeadBrowserSession-style exceptions). */
    public static RetryCategory classify(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String name = c.getClass().getSimpleName();
            if (name.contains("NoSuchSession") || name.contains("SessionNotCreated")
                    || name.equals("UnreachableBrowserException") || name.equals("ConnectException")
                    || name.equals("UnknownHostException") || name.equals("SocketTimeoutException")) {
                return INFRA;
            }
            RetryCategory byMsg = classify(c.getMessage());
            if (byMsg != TEST) {
                return byMsg;
            }
        }
        return TEST;
    }
}
