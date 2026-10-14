package project.runtime.auth;

import project.utils.dataReader.PropertyReader;
import project.utils.reports.Secrets;

/**
 * Customer-framework auth modes — string refs aligned with AE {@code AuthMode} /
 * {@code AuthProfileRevision.revisionId} contracts (M3-01/M3-02).
 * <p>Never embeds runner tokens or credential values. Secrets resolved only via
 * {@link project.config.SecretResolver} when a live login path is later wired.
 * Unit tests admit/reject without live credentials.
 */
public final class AuthCoordinator {

    public static final String PROP_MODE = "mobile.auth.mode";
    public static final String PROP_PROFILE_REVISION = "mobile.auth.profileRevisionId";
    public static final String PROP_LOGIN_TC = "mobile.auth.loginTcId";
    public static final String PROP_LOGOUT_TC = "mobile.auth.logoutTcId";
    public static final String PROP_SETUP_TC = "mobile.auth.setupTcId";

    /** Mode names match AE delivery.mobile.auth.AuthMode (contract reference only). */
    public enum Mode {
        ENSURE_LOGGED_IN,
        FRESH_LOGIN,
        LOGGED_OUT,
        LOGIN_UNDER_TEST;

        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return ENSURE_LOGGED_IN;
            }
            return Mode.valueOf(raw.trim().toUpperCase());
        }
    }

    public enum SessionObservation {
        SIGNED_IN,
        SIGNED_OUT,
        UNKNOWN
    }

    public record Plan(
            Mode mode,
            boolean accepted,
            String failureCode,
            String message,
            String profileRevisionId) {}

    private AuthCoordinator() {}

    /**
     * Plan auth setup against AE mode semantics. No device I/O; no credential values.
     *
     * @param profileRevisionId AE AuthProfileRevision.revisionId ref (opaque string)
     * @param loginTcId         AE profile loginTcId ref (may be blank)
     * @param logoutTcId        AE profile logoutTcId ref
     * @param setupTcId         AE profile setupTcId ref
     */
    public static Plan plan(
            Mode mode,
            String profileRevisionId,
            String loginTcId,
            String logoutTcId,
            String setupTcId,
            SessionObservation observation) {
        Mode m = mode == null ? Mode.ENSURE_LOGGED_IN : mode;
        SessionObservation obs = observation == null ? SessionObservation.UNKNOWN : observation;
        String rev = profileRevisionId == null ? "" : profileRevisionId.trim();

        return switch (m) {
            case LOGIN_UNDER_TEST -> new Plan(
                    m, true, "", "LOGIN_UNDER_TEST: body owns login; automatic auth skipped", rev);
            case FRESH_LOGIN -> {
                if (!hasSafeResetPath(logoutTcId, setupTcId)) {
                    yield new Plan(m, false, "AUTH_RESET_UNCONFIGURED",
                            "FRESH_LOGIN requires configured logout or reset/setup TC ref", rev);
                }
                yield new Plan(m, true, "", "FRESH_LOGIN planned (refs only; no live credentials)", rev);
            }
            case LOGGED_OUT -> {
                if (obs == SessionObservation.UNKNOWN) {
                    yield new Plan(m, false, "SESSION_CHECK_UNCERTAIN",
                            "LOGGED_OUT: unknown session check cannot proceed", rev);
                }
                yield new Plan(m, true, "", "LOGGED_OUT planned", rev);
            }
            case ENSURE_LOGGED_IN -> {
                if (obs == SessionObservation.UNKNOWN) {
                    yield new Plan(m, false, "SESSION_CHECK_UNCERTAIN",
                            "ENSURE_LOGGED_IN: unknown session cannot proceed", rev);
                }
                if (obs == SessionObservation.SIGNED_IN) {
                    yield new Plan(m, true, "", "already signed in — no login prelude", rev);
                }
                if (loginTcId == null || loginTcId.isBlank()) {
                    yield new Plan(m, false, "AUTH_LOGIN_UNCONFIGURED",
                            "ENSURE_LOGGED_IN requires loginTcId ref when signed out", rev);
                }
                yield new Plan(m, true, "", "ENSURE_LOGGED_IN: login ref=" + loginTcId, rev);
            }
        };
    }

    public static boolean hasSafeResetPath(String logoutTcId, String setupTcId) {
        return (logoutTcId != null && !logoutTcId.isBlank())
                || (setupTcId != null && !setupTcId.isBlank());
    }

    /**
     * Property-driven hook from {@link project.tests.BaseMobileTest#authSetupIfConfigured()}.
     * Admits/rejects using refs only — does not perform live login or touch SecretResolver
     * unless a future operator enables an explicit live path (not in M4-03 unit scope).
     */
    public static void applyConfigured() {
        String modeRaw;
        try {
            modeRaw = PropertyReader.mobileProperties().getOrDefault(PROP_MODE, "");
        } catch (Exception e) {
            return; // properties not loaded — no-op
        }
        if (modeRaw == null || modeRaw.isBlank()) {
            return;
        }
        String rev = PropertyReader.mobileProperties().getOrDefault(PROP_PROFILE_REVISION, "");
        String login = PropertyReader.mobileProperties().getOrDefault(PROP_LOGIN_TC, "");
        String logout = PropertyReader.mobileProperties().getOrDefault(PROP_LOGOUT_TC, "");
        String setup = PropertyReader.mobileProperties().getOrDefault(PROP_SETUP_TC, "");
        // Observation stays UNKNOWN in scaffold without live session predicates —
        // LOGIN_UNDER_TEST and FRESH_LOGIN (with reset refs) still admit; others reject safely.
        SessionObservation obs = SessionObservation.UNKNOWN;
        String obsProp = PropertyReader.mobileProperties().getOrDefault("mobile.auth.sessionObservation", "");
        if (!obsProp.isBlank()) {
            obs = SessionObservation.valueOf(obsProp.trim().toUpperCase());
        }
        Plan plan = plan(Mode.parse(modeRaw), rev, login, logout, setup, obs);
        if (!plan.accepted()) {
            throw new IllegalStateException(
                    "AuthCoordinator rejected mode=" + plan.mode()
                            + " code=" + plan.failureCode()
                            + " profileRev=" + Secrets.refLabel(plan.profileRevisionId().isBlank()
                            ? "profile" : plan.profileRevisionId())
                            + " msg=" + plan.message());
        }
        // Accepted: record refs only — no credential materialization in M4-03 scaffold.
    }

    /** @deprecated use {@link #applyConfigured()} */
    @Deprecated
    public static void ensureLoggedIn(String profileRef) {
        if (profileRef == null || profileRef.isBlank()) {
            return;
        }
        Plan plan = plan(Mode.ENSURE_LOGGED_IN, profileRef, "login-tc", "", "", SessionObservation.SIGNED_OUT);
        if (!plan.accepted()) {
            // With blank login and SIGNED_OUT we need loginTcId — pass through for legacy callers
            plan = plan(Mode.ENSURE_LOGGED_IN, profileRef, "legacy-login", "", "", SessionObservation.SIGNED_IN);
        }
        if (!plan.accepted()) {
            throw new IllegalStateException("ensureLoggedIn rejected: " + plan.message());
        }
    }
}
