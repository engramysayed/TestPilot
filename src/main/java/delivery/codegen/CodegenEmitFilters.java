package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Steps kept in the customer framework ZIP (prove IR may still contain more). */
public final class CodegenEmitFilters {
    private CodegenEmitFilters() {
    }

    public static TcOutcome filterOutcome(TcOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        List<ProvenStep> proven = filterSteps(outcome.provenSteps());
        List<ProvenStep> login = outcome.loginSteps() == null ? null : filterSteps(outcome.loginSteps());
        List<ProvenStep> setup = filterSteps(outcome.setupSteps());

        TcStatus status = outcome.status();
        String reason = outcome.failureReason();
        String broken = findLocatorFreeTextContains(proven, login, setup);
        if (broken != null && status == TcStatus.PASSED) {
            status = TcStatus.PARTIAL;
            String added = "textContains step without a locator (IR must provide one): " + broken;
            reason = reason == null || reason.isBlank() ? added : reason + "; " + added;
        }

        return new TcOutcome(
                outcome.tcId(),
                outcome.title(),
                status,
                proven,
                reason,
                outcome.evidenceDir(),
                outcome.needsLoginBeforeMethod(),
                login,
                setup);
    }

    private static String findLocatorFreeTextContains(
            List<ProvenStep> proven, List<ProvenStep> login, List<ProvenStep> setup) {
        String hit = scanForLocatorFreeTextContains(proven);
        if (hit != null) return hit;
        hit = scanForLocatorFreeTextContains(login);
        if (hit != null) return hit;
        return scanForLocatorFreeTextContains(setup);
    }

    private static String scanForLocatorFreeTextContains(List<ProvenStep> steps) {
        if (steps == null) return null;
        for (ProvenStep step : steps) {
            if (step == null) continue;
            if ("textContains".equalsIgnoreCase(step.assertionType())
                    && (step.locatorValue() == null || step.locatorValue().isBlank())) {
                String expected = step.assertionExpected() == null ? "" : step.assertionExpected();
                return "\"" + expected + "\"";
            }
        }
        return null;
    }

    public static List<ProvenStep> filterSteps(List<ProvenStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<ProvenStep> out = new ArrayList<>(steps.size());
        for (ProvenStep step : steps) {
            if (emitStep(step)) {
                out.add(step);
            }
        }
        return out;
    }

    static boolean emitStep(ProvenStep step) {
        if (step == null) {
            return false;
        }
        String action = step.action() == null ? "" : step.action().trim().toLowerCase(Locale.ROOT);
        if (!"assert".equals(action)) {
            return true;
        }
        String rationale = step.rationale() == null ? "" : step.rationale();
        if (rationale.contains(":echo") || rationale.contains(":unknown-echo") || rationale.contains(":pageShown")) {
            return false;
        }
        if (rationale.contains("intent:ASSERT_VISIBLE:field=")) {
            return false;
        }
        String loc = step.locatorValue() == null ? "" : step.locatorValue().trim().toLowerCase(Locale.ROOT);
        if ("html".equals(loc) || loc.endsWith("html") && loc.length() <= 12) {
            return false;
        }
        return true;
    }
}
