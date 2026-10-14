package delivery.mobile.runner.device;

import delivery.mobile.result.FailureEvidenceManifest;

/**
 * On {@code APP_CRASHED}, healing (hierarchy re-bind / Precision) and vision fallback are skipped.
 */
public final class HealVisionGate {
    private HealVisionGate() {}

    public static boolean allowHeal(FailureEvidenceManifest evidence) {
        return evidence == null || !evidence.appCrashed();
    }

    public static boolean allowVision(FailureEvidenceManifest evidence) {
        return evidence == null || !evidence.appCrashed();
    }

    public static boolean allowHeal(CrashDetectionResult crash) {
        return crash == null || !crash.appCrashed();
    }

    public static boolean allowVision(CrashDetectionResult crash) {
        return crash == null || !crash.appCrashed();
    }
}
