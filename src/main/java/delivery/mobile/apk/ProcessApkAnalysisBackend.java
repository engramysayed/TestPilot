package delivery.mobile.apk;

import delivery.job.ProcessSupervisor;
import delivery.mobile.MobileProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prefers SDK {@code apkanalyzer}, falls back to qualified {@code aapt2 dump badging}.
 * Argument-array only via {@link ProcessSupervisor}; never executes the uploaded APK.
 */
public final class ProcessApkAnalysisBackend implements ApkAnalysisBackend {
    private static final Pattern PKG = Pattern.compile("package:\\s*name='([^']+)'");
    private static final Pattern VCODE = Pattern.compile("versionCode='(\\d+)'");
    private static final Pattern VNAME = Pattern.compile("versionName='([^']*)'");
    private static final Pattern SDK = Pattern.compile("sdkVersion:'(\\d+)'");
    private static final Pattern TARGET = Pattern.compile("targetSdkVersion:'(\\d+)'");
    private static final Pattern LAUNCH = Pattern.compile("launchable-activity(?:-name)?:\\s*name='([^']+)'");
    private static final Pattern LAUNCH_ALT = Pattern.compile("launchable-activity:\\s*name='([^']+)'");

    private final MobileProperties props;

    public ProcessApkAnalysisBackend(MobileProperties props) {
        this.props = props == null ? new MobileProperties() : props;
    }

    @Override
    public ApkManifestInfo analyze(Path apkFile) throws ApkInspectionException {
        if (apkFile == null || !Files.isRegularFile(apkFile)) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "APK path missing");
        }
        Duration deadline = Duration.ofSeconds(Math.max(5, props.getInspectTimeoutSeconds()));
        ApkInspectionException firstFailure = null;
        try {
            return analyzeWithApkanalyzer(apkFile, deadline);
        } catch (ApkInspectionException e) {
            firstFailure = e;
        }
        try {
            return analyzeWithAapt2(apkFile, deadline);
        } catch (ApkInspectionException e) {
            if (firstFailure != null) {
                throw new ApkInspectionException(ApkErrorCode.INSPECTION_FAILED,
                        "apkanalyzer/aapt2 unavailable or failed: " + firstFailure.getMessage()
                                + "; aapt2: " + e.getMessage());
            }
            throw e;
        }
    }

    private ApkManifestInfo analyzeWithApkanalyzer(Path apk, Duration deadline) throws ApkInspectionException {
        String bin = blankToNull(props.getApkanalyzerPath());
        if (bin == null) {
            throw new ApkInspectionException(ApkErrorCode.INSPECTION_FAILED, "apkanalyzer path not configured");
        }
        String packageName = runCapture(bin, deadline, "manifest", "application-id", apk.toString()).trim();
        String versionCodeRaw = runCapture(bin, deadline, "manifest", "version-code", apk.toString()).trim();
        String versionName = runCapture(bin, deadline, "manifest", "version-name", apk.toString()).trim();
        String minSdkRaw = runCapture(bin, deadline, "manifest", "min-sdk", apk.toString()).trim();
        Long versionCode = parseLongOrNull(versionCodeRaw);
        Integer minSdk = parseIntOrNull(minSdkRaw);
        if (packageName.isEmpty()) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "empty application-id from apkanalyzer");
        }
        List<String> launchers = new ArrayList<>();
        // apkanalyzer does not always list launchers in one call; try aapt2-style via dump if needed later
        String cert = null;
        try {
            String fingerprints = runCapture(bin, deadline, "apk", "file-summary", apk.toString());
            cert = extractSha256Digest(fingerprints);
        } catch (ApkInspectionException ignored) {
            // optional
        }
        return new ApkManifestInfo(packageName, versionCode, versionName.isEmpty() ? null : versionName,
                minSdk, null, List.of(), launchers, cert);
    }

    private ApkManifestInfo analyzeWithAapt2(Path apk, Duration deadline) throws ApkInspectionException {
        String bin = blankToNull(props.getAapt2Path());
        if (bin == null) {
            throw new ApkInspectionException(ApkErrorCode.INSPECTION_FAILED, "aapt2 path not configured");
        }
        String out = runCapture(bin, deadline, "dump", "badging", apk.toString());
        return parseBadging(out);
    }

    static ApkManifestInfo parseBadging(String out) throws ApkInspectionException {
        if (out == null || out.isBlank()) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "empty aapt2 badging");
        }
        Matcher pkg = PKG.matcher(out);
        if (!pkg.find()) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "package name missing in badging");
        }
        String packageName = pkg.group(1);
        Long versionCode = null;
        Matcher vc = VCODE.matcher(out);
        if (vc.find()) {
            versionCode = parseLongOrNull(vc.group(1));
        }
        String versionName = null;
        Matcher vn = VNAME.matcher(out);
        if (vn.find()) {
            versionName = vn.group(1);
        }
        Integer minSdk = null;
        Matcher sdk = SDK.matcher(out);
        if (sdk.find()) {
            minSdk = parseIntOrNull(sdk.group(1));
        }
        Integer targetSdk = null;
        Matcher tgt = TARGET.matcher(out);
        if (tgt.find()) {
            targetSdk = parseIntOrNull(tgt.group(1));
        }
        List<String> abis = new ArrayList<>();
        Matcher nat = Pattern.compile("native-code:(.*)").matcher(out);
        if (nat.find()) {
            Matcher quoted = Pattern.compile("'([^']+)'").matcher(nat.group(1));
            while (quoted.find()) {
                abis.add(quoted.group(1).trim());
            }
        }
        Set<String> launchers = new LinkedHashSet<>();
        Matcher la = LAUNCH.matcher(out);
        while (la.find()) {
            launchers.add(la.group(1));
        }
        Matcher la2 = LAUNCH_ALT.matcher(out);
        while (la2.find()) {
            launchers.add(la2.group(1));
        }
        return new ApkManifestInfo(packageName, versionCode, versionName, minSdk, targetSdk,
                abis, new ArrayList<>(launchers), null);
    }

    private String runCapture(String executable, Duration deadline, String... args) throws ApkInspectionException {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);
        for (String a : args) {
            cmd.add(a);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            ProcessSupervisor.Result result = ProcessSupervisor.run(pb, deadline, () -> false, 64_000);
            if (result.timedOut()) {
                throw new ApkInspectionException(ApkErrorCode.INSPECTION_FAILED, executable + " timed out");
            }
            if (!result.completedNormally()) {
                throw new ApkInspectionException(ApkErrorCode.INSPECTION_FAILED,
                        executable + " exit=" + result.exitCode() + " " + safe(result.output()));
            }
            return result.output() == null ? "" : result.output();
        } catch (ApkInspectionException e) {
            throw e;
        } catch (Exception e) {
            throw new ApkInspectionException(ApkErrorCode.INSPECTION_FAILED,
                    executable + " failed to start: " + e.getMessage());
        }
    }

    private static String extractSha256Digest(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?i)(?:SHA-?256|sha256)[:\\s]+([A-Fa-f0-9:]{32,})")
                .matcher(text);
        if (m.find()) {
            return m.group(1).replace(":", "").toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Long parseLongOrNull(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200);
    }
}
