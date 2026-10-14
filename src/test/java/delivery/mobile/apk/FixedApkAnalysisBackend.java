package delivery.mobile.apk;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/** Test double: returns a fixed or path-derived manifest without invoking SDK tools. */
public final class FixedApkAnalysisBackend implements ApkAnalysisBackend {
    private final Function<Path, ApkManifestInfo> fn;

    public FixedApkAnalysisBackend(ApkManifestInfo fixed) {
        this.fn = p -> fixed;
    }

    public FixedApkAnalysisBackend(Function<Path, ApkManifestInfo> fn) {
        this.fn = fn;
    }

    @Override
    public ApkManifestInfo analyze(Path apkFile) {
        return fn.apply(apkFile);
    }

    public static ApkManifestInfo sample(String pkg, long versionCode, int minSdk, List<String> abis,
                                  List<String> launchers, String cert) {
        return new ApkManifestInfo(pkg, versionCode, "1.0." + versionCode, minSdk, minSdk + 1,
                abis, launchers, cert);
    }
}
