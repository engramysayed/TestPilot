package delivery.mobile;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mobile feature flags and upload/inspection/ADB/lease/transport/Appium-lifecycle limits (M0-05 + M1-02..M1-06).
 * Defaults keep web startup and default CI free of Android tooling.
 * <p>Bound from {@code delivery.mobile.*} (relaxed binding: {@code live-tests} → {@code liveTests}).
 */
@ConfigurationProperties(prefix = "delivery.mobile")
public class MobileProperties {
    /** Master switch for mobile admission/UI. Default false. */
    private boolean enabled = false;
    /** When false, tests must not launch devices/providers. Default false. */
    private boolean liveTests = false;

    /** Max APK upload size in bytes (default 500 MiB). */
    private long uploadMaxBytes = 500L * 1024L * 1024L;
    /** Preferred SDK apkanalyzer executable (argument-array only). */
    private String apkanalyzerPath = "apkanalyzer";
    /** Qualified aapt2 fallback executable. */
    private String aapt2Path = "aapt2";
    /** Bounded manifest inspection deadline (seconds). */
    private int inspectTimeoutSeconds = 30;
    /** When false, lower versionCode than prior revision is rejected. */
    private boolean allowDowngrade = false;
    /** When false, certificate digest change vs prior revision is rejected. */
    private boolean allowSignatureReplacement = false;
    /** Max ZIP entries while validating an uploaded APK. */
    private int zipMaxEntries = 50_000;
    /** Max total uncompressed ZIP payload while validating (default = uploadMaxBytes). */
    private long zipMaxUncompressedBytes = 500L * 1024L * 1024L;
    /** Preferred adb executable (argument-array only). */
    private String adbPath = "adb";
    /** Bounded ADB inventory / shell deadline (seconds). */
    private int adbTimeoutSeconds = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLiveTests() {
        return liveTests;
    }

    public void setLiveTests(boolean liveTests) {
        this.liveTests = liveTests;
    }

    public long getUploadMaxBytes() {
        return uploadMaxBytes;
    }

    public void setUploadMaxBytes(long uploadMaxBytes) {
        this.uploadMaxBytes = uploadMaxBytes;
    }

    public String getApkanalyzerPath() {
        return apkanalyzerPath;
    }

    public void setApkanalyzerPath(String apkanalyzerPath) {
        this.apkanalyzerPath = apkanalyzerPath;
    }

    public String getAapt2Path() {
        return aapt2Path;
    }

    public void setAapt2Path(String aapt2Path) {
        this.aapt2Path = aapt2Path;
    }

    public int getInspectTimeoutSeconds() {
        return inspectTimeoutSeconds;
    }

    public void setInspectTimeoutSeconds(int inspectTimeoutSeconds) {
        this.inspectTimeoutSeconds = inspectTimeoutSeconds;
    }

    public boolean isAllowDowngrade() {
        return allowDowngrade;
    }

    public void setAllowDowngrade(boolean allowDowngrade) {
        this.allowDowngrade = allowDowngrade;
    }

    public boolean isAllowSignatureReplacement() {
        return allowSignatureReplacement;
    }

    public void setAllowSignatureReplacement(boolean allowSignatureReplacement) {
        this.allowSignatureReplacement = allowSignatureReplacement;
    }

    public int getZipMaxEntries() {
        return zipMaxEntries;
    }

    public void setZipMaxEntries(int zipMaxEntries) {
        this.zipMaxEntries = zipMaxEntries;
    }

    public long getZipMaxUncompressedBytes() {
        return zipMaxUncompressedBytes;
    }

    public void setZipMaxUncompressedBytes(long zipMaxUncompressedBytes) {
        this.zipMaxUncompressedBytes = zipMaxUncompressedBytes;
    }

    public String getAdbPath() {
        return adbPath;
    }

    public void setAdbPath(String adbPath) {
        this.adbPath = adbPath;
    }

    public int getAdbTimeoutSeconds() {
        return adbTimeoutSeconds;
    }

    public void setAdbTimeoutSeconds(int adbTimeoutSeconds) {
        this.adbTimeoutSeconds = adbTimeoutSeconds;
    }

    /** Proposed worker heartbeat interval (seconds). */
    private int leaseHeartbeatSeconds = 5;
    /** Lease expiry without heartbeat (seconds). Expiry stops commands; does not release. */
    private int leaseExpirySeconds = 30;
    /**
     * Root directory for host-scoped physical device locks (not tenant-specific).
     * Empty → {@code {delivery.work-dir}/mobile-host-locks}.
     */
    private String hostLockRoot = "";

    public int getLeaseHeartbeatSeconds() {
        return leaseHeartbeatSeconds;
    }

    public void setLeaseHeartbeatSeconds(int leaseHeartbeatSeconds) {
        this.leaseHeartbeatSeconds = leaseHeartbeatSeconds;
    }

    public int getLeaseExpirySeconds() {
        return leaseExpirySeconds;
    }

    public void setLeaseExpirySeconds(int leaseExpirySeconds) {
        this.leaseExpirySeconds = leaseExpirySeconds;
    }

    public String getHostLockRoot() {
        return hostLockRoot;
    }

    public void setHostLockRoot(String hostLockRoot) {
        this.hostLockRoot = hostLockRoot;
    }

    /** M1-05: short-lived secret envelope TTL (seconds). */
    private int secretEnvelopeTtlSeconds = 300;
    /** M1-05: max compressed runner input ZIP bytes. */
    private long runnerInputZipMaxBytes = 64L * 1024L * 1024L;
    /** M1-05: max entries in runner input ZIP. */
    private int runnerInputZipMaxEntries = 10_000;
    /** M1-05: max uncompressed runner input ZIP bytes. */
    private long runnerInputZipMaxUncompressedBytes = 64L * 1024L * 1024L;
    /** M1-05: max artifact body bytes accepted for HMAC verification. */
    private long runnerHmacMaxBodyBytes = 64L * 1024L * 1024L;
    /** M1-05: runner protocol version advertised/expected. */
    private int runnerProtocolVersion = 1;

    public int getSecretEnvelopeTtlSeconds() {
        return secretEnvelopeTtlSeconds;
    }

    public void setSecretEnvelopeTtlSeconds(int secretEnvelopeTtlSeconds) {
        this.secretEnvelopeTtlSeconds = secretEnvelopeTtlSeconds;
    }

    public long getRunnerInputZipMaxBytes() {
        return runnerInputZipMaxBytes;
    }

    public void setRunnerInputZipMaxBytes(long runnerInputZipMaxBytes) {
        this.runnerInputZipMaxBytes = runnerInputZipMaxBytes;
    }

    public int getRunnerInputZipMaxEntries() {
        return runnerInputZipMaxEntries;
    }

    public void setRunnerInputZipMaxEntries(int runnerInputZipMaxEntries) {
        this.runnerInputZipMaxEntries = runnerInputZipMaxEntries;
    }

    public long getRunnerInputZipMaxUncompressedBytes() {
        return runnerInputZipMaxUncompressedBytes;
    }

    public void setRunnerInputZipMaxUncompressedBytes(long runnerInputZipMaxUncompressedBytes) {
        this.runnerInputZipMaxUncompressedBytes = runnerInputZipMaxUncompressedBytes;
    }

    public long getRunnerHmacMaxBodyBytes() {
        return runnerHmacMaxBodyBytes;
    }

    public void setRunnerHmacMaxBodyBytes(long runnerHmacMaxBodyBytes) {
        this.runnerHmacMaxBodyBytes = runnerHmacMaxBodyBytes;
    }

    public int getRunnerProtocolVersion() {
        return runnerProtocolVersion;
    }

    public void setRunnerProtocolVersion(int runnerProtocolVersion) {
        this.runnerProtocolVersion = runnerProtocolVersion;
    }

    /** M1-06: Appium CLI executable (argument-array only; never a jar on portal classpath). */
    private String appiumPath = "appium";
    /** M1-06: inclusive loopback port range start for owned Appium/system/chromedriver ports. */
    private int appiumPortRangeStart = 4723;
    /** M1-06: inclusive loopback port range end. */
    private int appiumPortRangeEnd = 4923;
    /** M1-06: bounded Appium process start wait (seconds). */
    private int appiumStartTimeoutSeconds = 60;
    /** M1-06: bounded session-create wait (seconds) — bookkeeping budget on portal. */
    private int sessionStartTimeoutSeconds = 60;
    /**
     * M1-06: teardown deadline (quit → stop owned server → release ACK), separate from case/job deadline.
     * Default 30s per architecture.
     */
    private int teardownDeadlineSeconds = 30;

    public String getAppiumPath() {
        return appiumPath;
    }

    public void setAppiumPath(String appiumPath) {
        this.appiumPath = appiumPath;
    }

    public int getAppiumPortRangeStart() {
        return appiumPortRangeStart;
    }

    public void setAppiumPortRangeStart(int appiumPortRangeStart) {
        this.appiumPortRangeStart = appiumPortRangeStart;
    }

    public int getAppiumPortRangeEnd() {
        return appiumPortRangeEnd;
    }

    public void setAppiumPortRangeEnd(int appiumPortRangeEnd) {
        this.appiumPortRangeEnd = appiumPortRangeEnd;
    }

    public int getAppiumStartTimeoutSeconds() {
        return appiumStartTimeoutSeconds;
    }

    public void setAppiumStartTimeoutSeconds(int appiumStartTimeoutSeconds) {
        this.appiumStartTimeoutSeconds = appiumStartTimeoutSeconds;
    }

    public int getSessionStartTimeoutSeconds() {
        return sessionStartTimeoutSeconds;
    }

    public void setSessionStartTimeoutSeconds(int sessionStartTimeoutSeconds) {
        this.sessionStartTimeoutSeconds = sessionStartTimeoutSeconds;
    }

    public int getTeardownDeadlineSeconds() {
        return teardownDeadlineSeconds;
    }

    public void setTeardownDeadlineSeconds(int teardownDeadlineSeconds) {
        this.teardownDeadlineSeconds = teardownDeadlineSeconds;
    }

}
