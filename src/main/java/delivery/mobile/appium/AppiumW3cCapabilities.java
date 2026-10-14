package delivery.mobile.appium;

/**
 * AE-L6: pure W3C NewSession capabilities builder for attach-by-UDID.
 * Never launches an AVD/emulator; never omits {@code appium:udid}; never invents a serial.
 */
public final class AppiumW3cCapabilities {
    private AppiumW3cCapabilities() {
    }

    /**
     * Build the JSON body for {@code POST /session} with exact leased ADB serial as {@code appium:udid}.
     *
     * @throws AppiumLifecycleException {@link AppiumErrorCode#INVALID_STATE} when serial is null/blank
     */
    public static String newSessionBody(String deviceSerial) {
        String serial = requireSerial(deviceSerial);
        // Minimal JSON (no dependency on jackson for this hot path). Escape serial for JSON string safety.
        return "{"
                + "\"capabilities\":{"
                + "\"alwaysMatch\":{"
                + "\"platformName\":\"Android\","
                + "\"appium:automationName\":\"UiAutomator2\","
                + "\"appium:udid\":\"" + jsonEscape(serial) + "\""
                + "}"
                + "}"
                + "}";
    }

    /** Reject blank/null serial — no invent, no first-device fallback. */
    public static String requireSerial(String deviceSerial) {
        if (deviceSerial == null || deviceSerial.isBlank()) {
            throw new AppiumLifecycleException(AppiumErrorCode.INVALID_STATE,
                    "deviceSerial (appium:udid) required; refuse blank/null and first-device fallback");
        }
        return deviceSerial.trim();
    }

    static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
