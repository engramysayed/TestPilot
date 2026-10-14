package delivery.mobile.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Runtime-observed app identity (M2-07). Must not mutate frozen admission inputs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObservedAppIdentity(
        String packageName,
        Long versionCode,
        String versionName,
        String launchActivity,
        String certDigest,
        AppIdentitySource source
) {
    public ObservedAppIdentity {
        packageName = packageName == null ? "" : packageName;
        versionName = versionName == null ? "" : versionName;
        launchActivity = launchActivity == null ? "" : launchActivity;
        certDigest = certDigest == null ? "" : certDigest;
        source = source == null ? AppIdentitySource.UNKNOWN : source;
    }

    public static ObservedAppIdentity empty() {
        return new ObservedAppIdentity("", null, "", "", "", AppIdentitySource.UNKNOWN);
    }
}
