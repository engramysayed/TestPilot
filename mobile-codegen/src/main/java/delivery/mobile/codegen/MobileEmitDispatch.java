package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobilePlatform;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Platform dispatch for emit/package (M4-04).
 * ANDROID → {@link MobileCodeWriter} / {@link MobileFrameworkPackager}.
 * WEB (and unknown) refused on the mobile path — never fall through to web CodeWriter here.
 */
public final class MobileEmitDispatch {

    public enum PlatformKind { ANDROID, WEB, UNKNOWN }

    private MobileEmitDispatch() {}

    public static PlatformKind classify(String platform) {
        if (platform == null || platform.isBlank()) {
            return PlatformKind.UNKNOWN;
        }
        String p = platform.trim().toUpperCase(Locale.ROOT);
        if ("ANDROID".equals(p)) {
            return PlatformKind.ANDROID;
        }
        if ("WEB".equals(p) || "BROWSER".equals(p)) {
            return PlatformKind.WEB;
        }
        return PlatformKind.UNKNOWN;
    }

    public static boolean acceptsPlatform(String platform) {
        return classify(platform) == PlatformKind.ANDROID;
    }

    public static boolean accepts(MobilePlatform platform) {
        return platform == MobilePlatform.ANDROID;
    }

    /**
     * Emit mobile framework under {@code outputRoot}. Refuses WEB payloads.
     */
    public static void emitAndroid(
            Path outputRoot,
            List<MobileCaseIr> cases,
            NamingMap names,
            Set<String> loginTcIds) throws Exception {
        if (cases != null) {
            for (MobileCaseIr c : cases) {
                if (c != null && c.platform() != MobilePlatform.ANDROID) {
                    throw new IllegalArgumentException(
                            "MobileEmitDispatch refuses non-ANDROID case platform=" + c.platform());
                }
            }
        }
        new MobileCodeWriter(loginTcIds).writeVerified(outputRoot, cases, names);
    }

    public static void refuseWeb(String platform) {
        if (classify(platform) == PlatformKind.WEB) {
            throw new IllegalArgumentException(
                    "Mobile emit/package path refuses WEB — use web CodeWriter / FrameworkPackager");
        }
        if (classify(platform) != PlatformKind.ANDROID) {
            throw new IllegalArgumentException(
                    "Mobile emit/package requires platform=ANDROID (got: " + platform + ")");
        }
    }
}
