package delivery.mobile.apk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded metadata extracted from an uploaded APK (never executes the APK). */
public final class ApkManifestInfo {
    private final String packageName;
    private final Long versionCode;
    private final String versionName;
    private final Integer minSdk;
    private final Integer targetSdk;
    private final List<String> abiList;
    private final List<String> launcherActivities;
    private final String certificateDigest;

    public ApkManifestInfo(String packageName,
                           Long versionCode,
                           String versionName,
                           Integer minSdk,
                           Integer targetSdk,
                           List<String> abiList,
                           List<String> launcherActivities,
                           String certificateDigest) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.minSdk = minSdk;
        this.targetSdk = targetSdk;
        this.abiList = abiList == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(abiList));
        this.launcherActivities = launcherActivities == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(launcherActivities));
        this.certificateDigest = certificateDigest;
    }

    public String getPackageName() { return packageName; }
    public Long getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public Integer getMinSdk() { return minSdk; }
    public Integer getTargetSdk() { return targetSdk; }
    public List<String> getAbiList() { return abiList; }
    public List<String> getLauncherActivities() { return launcherActivities; }
    public String getCertificateDigest() { return certificateDigest; }
}
