package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.authoring.LoginStepDetector;
import delivery.authoring.StepIntentBinder;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rejects false PASSes via intent/action consistency only.
 * Does not hard-code product/site page names (cart, checkout, etc.) into URL checks —
 * mid-flow Excel wording must not punish a correct final URL.
 */
public final class SemanticPassGate {
    private static final Set<String> KNOWN_ASSERT_TYPES = Set.of(
            "visible", "textContains", "urlContains", "checked", "unchecked", "selected", "notVisible");

    private SemanticPassGate() {
    }

    public static String rejectReason(ManualTestCase tc, List<ProvenStep> provenSteps, String currentUrl) {
        if (provenSteps == null || provenSteps.isEmpty()) {
            return "Semantic PASS rejected: empty proven steps";
        }
        for (ProvenStep step : provenSteps) {
            String mismatch = intentActionMismatch(step);
            if (mismatch != null) {
                return mismatch;
            }
            String submitLogin = submitBecameLoginHref(tc, step, currentUrl);
            if (submitLogin != null) {
                return submitLogin;
            }
            String assertType = step.assertionType() == null ? "" : step.assertionType().trim();
            if (!assertType.isBlank() && !KNOWN_ASSERT_TYPES.contains(assertType)) {
                return "Semantic PASS rejected: unknown assertionType=" + assertType;
            }
        }
        if (honestyDemoteEnabled() && tc != null) {
            String bodyCountReject = bodyIntentCountReject(tc, provenSteps);
            if (bodyCountReject != null) {
                return bodyCountReject;
            }
        }
        // currentUrl reserved for future generic end-state checks (expectedResult-only)
        return null;
    }

    static boolean honestyDemoteEnabled() {
        String p = System.getProperty("delivery.honesty-demote");
        if (p == null || p.isBlank()) {
            p = System.getenv("DELIVERY_HONESTY_DEMOTE");
        }
        if (p == null || p.isBlank()) {
            p = utils.PropertyReader.getProperty("delivery.honesty-demote");
        }
        return "true".equalsIgnoreCase(p == null ? "" : p.trim()) || "1".equals(p == null ? "" : p.trim());
    }

    private static String bodyIntentCountReject(ManualTestCase tc, List<ProvenStep> provenSteps) {
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.bodyIntents(
                tc, LoginStepDetector.hasLoginSteps(tc) && !LoginStepDetector.isLoginFailureCase(tc));
        if (provenSteps.size() < intents.size()) {
            return "Semantic PASS rejected: proven body intents (" + provenSteps.size()
                    + ") < Excel body intents (" + intents.size() + ")";
        }
        return null;
    }

    private static String intentActionMismatch(ProvenStep step) {
        String rationale = step.rationale() == null ? "" : step.rationale();
        if (!rationale.startsWith("intent:")) {
            return null;
        }
        String kind = rationale.substring("intent:".length());
        int colon = kind.indexOf(':');
        if (colon > 0) {
            kind = kind.substring(0, colon);
        }
        String action = step.action() == null ? "" : step.action().toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "ASSERT_VISIBLE" -> {
                if (!"assert".equals(action) && (step.assertionType() == null || step.assertionType().isBlank())) {
                    yield "Semantic PASS rejected: ASSERT_VISIBLE bound to non-assert action=" + action;
                }
                if ("click".equals(action) && (step.assertionType() == null || step.assertionType().isBlank())) {
                    yield "Semantic PASS rejected: ASSERT_VISIBLE became click";
                }
                yield null;
            }
            case "CLICK" -> {
                if ("assert".equals(action)) {
                    yield "Semantic PASS rejected: CLICK bound to assert";
                }
                yield null;
            }
            case "TYPE_FIELD" -> {
                if (!("type".equals(action) || "select".equals(action) || "click".equals(action))) {
                    yield "Semantic PASS rejected: TYPE_FIELD bound to action=" + action;
                }
                yield null;
            }
            default -> null;
        };
    }

    private static String submitBecameLoginHref(ManualTestCase tc, ProvenStep step, String currentUrl) {
        if (step == null || !"click".equalsIgnoreCase(step.action())) {
            return null;
        }
        if (!excelWantsFormSubmit(tc)) {
            return null;
        }
        String loc = step.locatorValue() == null ? "" : step.locatorValue().toLowerCase(Locale.ROOT);
        boolean loginHref = loc.contains("/login") || loc.contains("/signin") || loc.contains("/sign-in");
        boolean landedLogin = currentUrl != null && (currentUrl.toLowerCase(Locale.ROOT).contains("/login")
                || currentUrl.toLowerCase(Locale.ROOT).contains("/signin"));
        if (loginHref || landedLogin && loc.contains("login")) {
            return "Semantic PASS rejected: Submit bound to a login href";
        }
        if (StepIntentBinder.looksLikeNonSubmitNavigationLocator(step.locatorValue())) {
            return "Semantic PASS rejected: Submit bound to a non-submit navigation href";
        }
        return null;
    }

    private static boolean excelWantsFormSubmit(ManualTestCase tc) {
        if (tc == null || tc.steps() == null) {
            return false;
        }
        String steps = tc.steps().toLowerCase(Locale.ROOT);
        return steps.contains("submit") && !steps.contains("login button") && !steps.contains("sign in");
    }
}
