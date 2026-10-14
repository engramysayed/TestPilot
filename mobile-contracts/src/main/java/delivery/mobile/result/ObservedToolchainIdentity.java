package delivery.mobile.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Runtime-observed Appium/driver/toolchain versions (M2-07). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObservedToolchainIdentity(
        String appiumVersion,
        String uiautomator2Version,
        String chromedriverVersion,
        String runnerProtocolVersion
) {
    public ObservedToolchainIdentity {
        appiumVersion = appiumVersion == null ? "" : appiumVersion;
        uiautomator2Version = uiautomator2Version == null ? "" : uiautomator2Version;
        chromedriverVersion = chromedriverVersion == null ? "" : chromedriverVersion;
        runnerProtocolVersion = runnerProtocolVersion == null ? "" : runnerProtocolVersion;
    }

    public static ObservedToolchainIdentity empty() {
        return new ObservedToolchainIdentity("", "", "", "");
    }
}
