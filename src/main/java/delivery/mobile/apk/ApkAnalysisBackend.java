package delivery.mobile.apk;

import java.nio.file.Path;

/**
 * Extracts manifest identity from a quarantined APK path.
 * Implementations must never execute the uploaded APK on the portal host.
 */
public interface ApkAnalysisBackend {
    ApkManifestInfo analyze(Path apkFile) throws ApkInspectionException;
}
