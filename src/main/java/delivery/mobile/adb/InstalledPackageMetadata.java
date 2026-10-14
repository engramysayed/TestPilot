package delivery.mobile.adb;

import delivery.mobile.model.MobileVerification;

/**
 * Observed identity of an installed package on a specific ADB serial.
 * {@code verification} is always {@link MobileVerification#OBSERVED} (not uploaded-byte proof).
 */
public final class InstalledPackageMetadata {
    private final String serial;
    private final String packageName;
    private final Long versionCode;
    private final String versionName;
    private final String verification;

    public InstalledPackageMetadata(String serial, String packageName, Long versionCode, String versionName) {
        this.serial = serial;
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.verification = MobileVerification.OBSERVED.name();
    }

    public String getSerial() { return serial; }
    public String getPackageName() { return packageName; }
    public Long getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public String getVerification() { return verification; }
}
