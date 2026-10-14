package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileProof;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mobile proof invalidation (M4-05). Does not call web ReuseEligibility.
 * Unselected cases on a new app revision become stale/TODO; old packages stay downloadable
 * only when bound to the exact package version.
 */
public final class MobileProofInvalidation {

    public enum Decision { KEEP, REPROVE, STALE_TODO }

    public record CaseDecision(String tcId, Decision decision, String reason) {}

    private MobileProofInvalidation() {}

    public static List<CaseDecision> decide(
            List<MobileCaseIr> cases,
            Set<String> selectedTcIds,
            String storedFingerprint,
            MobilePackageFingerprint.Inputs current,
            String storedAppRevisionId) {
        boolean reproveAll = MobilePackageFingerprint.requiresReprove(storedFingerprint, current);
        boolean appChanged = storedAppRevisionId != null
                && current != null
                && !storedAppRevisionId.equals(current.appRevisionId());
        List<CaseDecision> out = new ArrayList<>();
        if (cases == null) {
            return out;
        }
        Set<String> selected = selectedTcIds == null ? Set.of() : selectedTcIds;
        for (MobileCaseIr c : cases) {
            if (c == null) {
                continue;
            }
            boolean selectedCase = selected.contains(c.tcId());
            if (!selectedCase && (appChanged || reproveAll)) {
                out.add(new CaseDecision(c.tcId(), Decision.STALE_TODO,
                        "unselected on changed fingerprint/app revision — stale/TODO, not carried PASS"));
                continue;
            }
            if (reproveAll || appChanged || !isProven(c.proof())) {
                out.add(new CaseDecision(c.tcId(), Decision.REPROVE,
                        "fingerprint or proof incomplete — re-prove (no web reuse shortcut)"));
                continue;
            }
            out.add(new CaseDecision(c.tcId(), Decision.KEEP, "fingerprint matches and case selected"));
        }
        return out;
    }

    /**
     * Download is bound to exact package version — mismatch refuses the download handle.
     */
    public static boolean downloadBound(String requestedVersion, String publishedVersion) {
        if (requestedVersion == null || requestedVersion.isBlank()
                || publishedVersion == null || publishedVersion.isBlank()) {
            return false;
        }
        return requestedVersion.trim().equals(publishedVersion.trim());
    }

    public static Map<String, String> manifest(MobilePackageFingerprint.Inputs in) {
        return MobilePackageFingerprint.manifestFields(in);
    }

    private static boolean isProven(MobileProof proof) {
        return proof != null && proof.status() != null
                && "PROVEN".equalsIgnoreCase(proof.status().trim());
    }
}
