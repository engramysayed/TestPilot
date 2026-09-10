package delivery.job;

import delivery.codegen.ProvenStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Substitutes {@code ${TARGET_*}} with the selected credential profile for live
 * Selenium only. Callers must keep the unresolved token in IR / codegen.
 */
public final class LoginSecretResolver {
    private LoginSecretResolver() {
    }

    public static List<ProvenStep> resolveForLive(List<ProvenStep> steps, ConversionJobRequest request) {
        if (steps == null || steps.isEmpty()) {
            return steps == null ? List.of() : steps;
        }
        String user = request == null || request.username() == null ? "" : request.username();
        String pass = request == null || request.password() == null ? "" : request.password();
        List<ProvenStep> out = new ArrayList<>(steps.size());
        for (ProvenStep s : steps) {
            String v = s.value() == null ? "" : s.value();
            if ("${TARGET_USERNAME}".equals(v)) {
                v = user;
            } else if ("${TARGET_PASSWORD}".equals(v)) {
                v = pass;
            }
            out.add(new ProvenStep(
                    s.tcId(), s.pageName(), s.actionType(), s.action(),
                    s.locatorStrategy(), s.locatorValue(), v,
                    s.assertionType(), s.assertionExpected(), s.validated(), s.rationale(),
                    s.screenshotRelPath()));
        }
        return out;
    }
}
