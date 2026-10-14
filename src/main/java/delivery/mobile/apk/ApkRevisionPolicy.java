package delivery.mobile.apk;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable-revision gates: package match, API/ABI compatibility, downgrade and signature
 * replacement. Never suggests or performs silent uninstall to make an install succeed.
 */
public final class ApkRevisionPolicy {
    public record PriorRevision(String packageName, Long versionCode, String certificateDigest) {
    }

    public record DeviceConstraints(Integer apiLevel, String abi) {
    }

    private ApkRevisionPolicy() {
    }

    /** Explicit: this policy never authorizes auto-uninstall. */
    public static boolean autoUninstallFallbackAllowed() {
        return false;
    }

    public static void enforce(ApkManifestInfo info,
                               String expectedPackageOrNull,
                               PriorRevision priorOrNull,
                               DeviceConstraints deviceOrNull,
                               boolean allowDowngrade,
                               boolean allowSignatureReplacement) throws ApkInspectionException {
        if (info == null || info.getPackageName() == null || info.getPackageName().isBlank()) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "package name required");
        }
        if (expectedPackageOrNull != null && !expectedPackageOrNull.isBlank()
                && !expectedPackageOrNull.equals(info.getPackageName())) {
            throw new ApkInspectionException(ApkErrorCode.APP_ID_MISMATCH,
                    "package " + info.getPackageName() + " does not match expected " + expectedPackageOrNull);
        }
        if (priorOrNull != null && priorOrNull.packageName() != null && !priorOrNull.packageName().isBlank()
                && !priorOrNull.packageName().equals(info.getPackageName())) {
            throw new ApkInspectionException(ApkErrorCode.APP_ID_MISMATCH,
                    "package " + info.getPackageName() + " does not match app package "
                            + priorOrNull.packageName());
        }
        if (priorOrNull != null && priorOrNull.versionCode() != null && info.getVersionCode() != null
                && info.getVersionCode() < priorOrNull.versionCode() && !allowDowngrade) {
            throw new ApkInspectionException(ApkErrorCode.DOWNGRADE_REJECTED,
                    "versionCode " + info.getVersionCode() + " < prior " + priorOrNull.versionCode()
                            + "; set delivery.mobile.allow-downgrade=true to approve");
        }
        if (priorOrNull != null && priorOrNull.certificateDigest() != null
                && !priorOrNull.certificateDigest().isBlank()
                && info.getCertificateDigest() != null && !info.getCertificateDigest().isBlank()) {
            String a = normalizeDigest(priorOrNull.certificateDigest());
            String b = normalizeDigest(info.getCertificateDigest());
            if (!a.equals(b) && !allowSignatureReplacement) {
                throw new ApkInspectionException(ApkErrorCode.SIGNATURE_REPLACEMENT_REJECTED,
                        "signing certificate changed; set delivery.mobile.allow-signature-replacement=true to approve");
            }
        }
        if (deviceOrNull != null && deviceOrNull.apiLevel() != null && info.getMinSdk() != null
                && deviceOrNull.apiLevel() < info.getMinSdk()) {
            throw new ApkInspectionException(ApkErrorCode.API_INCOMPATIBLE,
                    "device API " + deviceOrNull.apiLevel() + " < minSdk " + info.getMinSdk());
        }
        if (deviceOrNull != null && deviceOrNull.abi() != null && !deviceOrNull.abi().isBlank()
                && info.getAbiList() != null && !info.getAbiList().isEmpty()) {
            if (!abiCompatible(info.getAbiList(), deviceOrNull.abi())) {
                throw new ApkInspectionException(ApkErrorCode.ABI_INCOMPATIBLE,
                        "device ABI " + deviceOrNull.abi() + " not in APK ABIs " + info.getAbiList());
            }
        }
        if (autoUninstallFallbackAllowed()) {
            // unreachable by design; kept as a tripwire for tests
            throw new IllegalStateException("auto-uninstall must never be allowed");
        }
    }

    /**
     * Resolve launch/wait activities. Multiple launchers without an explicit choice → ACTIVITY_REQUIRED.
     * Distinguishes launch activity vs wait activity (wait may equal launch when only one).
     */
    public static ActivityChoice resolveActivities(ApkManifestInfo info,
                                                   String selectedLaunch,
                                                   String selectedWait) throws ApkInspectionException {
        List<String> candidates = info.getLauncherActivities() == null
                ? List.of()
                : info.getLauncherActivities();
        String launch = blankToNull(selectedLaunch);
        String wait = blankToNull(selectedWait);
        if (launch == null) {
            if (candidates.isEmpty()) {
                throw new ApkInspectionException(ApkErrorCode.ACTIVITY_REQUIRED,
                        "no launcher activity detected; provide launchActivity");
            }
            if (candidates.size() > 1) {
                throw new ApkInspectionException(ApkErrorCode.ACTIVITY_REQUIRED,
                        "multiple launcher activities require choice: " + candidates);
            }
            launch = candidates.get(0);
        }
        if (wait == null) {
            wait = launch;
        }
        return new ActivityChoice(launch, wait, List.copyOf(candidates));
    }

    public record ActivityChoice(String launchActivity, String waitActivity, List<String> launcherCandidates) {
    }

    private static boolean abiCompatible(List<String> apkAbis, String deviceAbi) {
        Set<String> set = new HashSet<>();
        for (String a : apkAbis) {
            if (a != null) {
                set.add(a.trim().toLowerCase(Locale.ROOT));
            }
        }
        String d = deviceAbi.trim().toLowerCase(Locale.ROOT);
        if (set.contains(d)) {
            return true;
        }
        // common 64-bit can run 32-bit counterpart when present
        if ("arm64-v8a".equals(d) && set.contains("armeabi-v7a")) {
            return true;
        }
        if ("x86_64".equals(d) && set.contains("x86")) {
            return true;
        }
        return false;
    }

    private static String normalizeDigest(String d) {
        return d.replace(":", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
