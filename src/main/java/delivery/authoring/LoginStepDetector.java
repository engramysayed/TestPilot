package delivery.authoring;

import delivery.excel.ManualTestCase;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Login / session policy for conversion prove + codegen.
 * Site-agnostic: no product or host-specific keywords.
 */
public final class LoginStepDetector {
    /**
     * Phrases that mean the case does <em>not</em> need auth — must win over bare "login".
     */
    private static final Pattern LOGIN_NOT_NEEDED = Pattern.compile(
            "(?i)\\b("
                    + "no\\s+login\\s+required"
                    + "|login\\s+not\\s+required"
                    + "|does\\s+not\\s+require\\s+(a\\s+)?login"
                    + "|without\\s+(logging\\s+in|login)"
                    + "|unauthenticated"
                    + "|public\\s+page"
                    + ")\\b");

    /** Explicit login / sign-in actions in Excel Steps (not bare noun "login"). */
    private static final Pattern LOGIN_ACTION = Pattern.compile(
            "(?i)\\b("
                    + "log\\s+in"
                    + "|sign\\s+in"
                    + "|authenticate"
                    + "|login\\s+page"
                    + "|sign\\s*-?in\\s+page"
                    + ")\\b");

    private static final Pattern CRED_FIELDS = Pattern.compile(
            "(?i)\\b(username|user\\s*name|password|email)\\b.*\\b(enter|type|fill|input)\\b"
                    + "|\\b(enter|type|fill|input)\\b.*\\b(username|user\\s*name|password|email)\\b");

    private static final Pattern LOGIN_BUTTON = Pattern.compile(
            "(?i)\\b(click|press)\\b.*\\b(login|sign\\s*in)\\b|\\blogin\\s*button\\b");

    private static final Pattern LOGIN_FAILURE = Pattern.compile(
            "(?i)\\b(invalid|incorrect|wrong|error|fail(ed|ure)?)\\b.*\\b(password|username|login|credential)");

    /**
     * Preconditions that mean the case needs a job-provided session (not public pages).
     * Prefer writing auth need here; shared credentials come from project run config.
     */
    private static final Pattern REQUIRES_AUTH = Pattern.compile(
            "(?i)\\b("
                    + "already\\s+logged\\s+in"
                    + "|authenticated\\s+session"
                    + "|must\\s+be\\s+logged\\s+in"
                    + "|user\\s+is\\s+logged\\s+in"
                    + "|login\\s+required"
                    + "|requires\\s+(login|authentication|an?\\s+authenticated\\s+session)"
                    + "|log\\s*in\\s+before"
                    + ")\\b");

    private LoginStepDetector() {
    }

    public static boolean hasLoginSteps(ManualTestCase tc) {
        if (tc == null) {
            return false;
        }
        // Steps / title only — preconditions use REQUIRES_AUTH / LOGIN_NOT_NEEDED instead.
        String steps = stripLoginNotNeeded(safe(tc.steps()));
        String title = safe(tc.title());
        if (LOGIN_ACTION.matcher(steps).find() || LOGIN_BUTTON.matcher(steps).find()) {
            return true;
        }
        String blob = (title + "\n" + steps).toLowerCase(Locale.ROOT);
        boolean loginContext = blob.contains("login") || blob.contains("sign in") || blob.contains("sign-in");
        if (!loginContext) {
            // Registration and other public forms also have email/password fields.
            return false;
        }
        if (CRED_FIELDS.matcher(steps).find()) {
            return true;
        }
        String lower = steps.toLowerCase(Locale.ROOT);
        return lower.contains("login")
                && (lower.contains("password")
                || lower.contains("username")
                || lower.contains("user name"));
    }

    /**
     * Run login prelude only when credentials exist AND the case needs a pre-session:
     * <ul>
     *   <li>Preconditions require an authenticated session (and Steps do not themselves log in), or</li>
     *   <li>The live page already shows a login form (gated landing / base URL)</li>
     * </ul>
     * Cases whose Steps perform login/sign-in are authored in the body — no prelude.
     * Public pages with no login form must not force login just because the job has credentials.
     * Phrases like "No login required" never force auth unless the live page is a login form.
     */
    public static boolean needsAuthenticatedSession(ManualTestCase tc, boolean jobHasCredentials) {
        return needsAuthenticatedSession(tc, jobHasCredentials, false);
    }

    public static boolean needsAuthenticatedSession(
            ManualTestCase tc,
            boolean jobHasCredentials,
            boolean loginFormVisibleOnCurrentPage
    ) {
        if (!jobHasCredentials || tc == null) {
            return false;
        }
        if (isLoginFailureCase(tc)) {
            return false;
        }
        String pre = safe(tc.preconditions());
        String preForAuth = stripLoginNotNeeded(pre);
        // Cases that *perform* login in Steps author those steps in the body — never a separate
        // prelude (prelude + bodyOnlyCase was emptying login TCs and soft-TODO on form miss).
        if (hasLoginSteps(tc)) {
            return false;
        }
        if (LOGIN_NOT_NEEDED.matcher(pre).find() && !REQUIRES_AUTH.matcher(preForAuth).find()) {
            return loginFormVisibleOnCurrentPage;
        }
        if (REQUIRES_AUTH.matcher(preForAuth).find()) {
            return true;
        }
        // Live gate: current page is a login form (base URL is login, or target redirected to login)
        return loginFormVisibleOnCurrentPage;
    }

    public static boolean isLoginFailureCase(ManualTestCase tc) {
        if (tc == null) {
            return false;
        }
        String blob = safe(tc.title()) + "\n" + safe(tc.steps()) + "\n" + safe(tc.expectedResult());
        return LOGIN_FAILURE.matcher(blob).find();
    }

    private static String stripLoginNotNeeded(String text) {
        return LOGIN_NOT_NEEDED.matcher(text).replaceAll(" ");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
