package delivery.mobile.transport;

import delivery.mobile.model.ProjectPlatform;
import keel.contract.runner.RunnerCapability;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * M1-05 capability negotiation: web-only runners cannot claim ANDROID work;
 * mobile claims require flag + {@link RunnerCapability#ANDROID_UIAUTOMATOR2}.
 */
public final class MobileClaimGate {
    private MobileClaimGate() {
    }

    public static Set<RunnerCapability> normalize(Collection<RunnerCapability> advertised) {
        if (advertised == null || advertised.isEmpty()) {
            // Backward compatible: omitted capabilities ⇒ web-only.
            return EnumSet.of(RunnerCapability.BROWSER_CHROME);
        }
        EnumSet<RunnerCapability> out = EnumSet.noneOf(RunnerCapability.class);
        for (RunnerCapability c : advertised) {
            if (c != null) {
                out.add(c);
            }
        }
        return out.isEmpty() ? EnumSet.of(RunnerCapability.BROWSER_CHROME) : out;
    }

    public static boolean canClaim(Collection<RunnerCapability> advertised,
                                   ProjectPlatform platform,
                                   boolean mobileEnabled) {
        ProjectPlatform p = platform == null ? ProjectPlatform.WEB : platform;
        Set<RunnerCapability> caps = normalize(advertised);
        if (p == ProjectPlatform.WEB) {
            return true;
        }
        if (p != ProjectPlatform.ANDROID) {
            return false;
        }
        if (!mobileEnabled) {
            return false;
        }
        return caps.contains(RunnerCapability.ANDROID_UIAUTOMATOR2);
    }

    public static void requireCanClaim(Collection<RunnerCapability> advertised,
                                       ProjectPlatform platform,
                                       boolean mobileEnabled) {
        if (!canClaim(advertised, platform, mobileEnabled)) {
            ProjectPlatform p = platform == null ? ProjectPlatform.WEB : platform;
            if (p == ProjectPlatform.ANDROID && !mobileEnabled) {
                throw new MobileTransportException(MobileTransportErrorCode.MOBILE_DISABLED,
                        "mobile feature flag is off");
            }
            throw new MobileTransportException(MobileTransportErrorCode.RUNNER_CAPABILITY_MISSING,
                    "web-only runner cannot claim mobile");
        }
    }

    public static Set<RunnerCapability> requiredFor(ProjectPlatform platform) {
        if (platform == ProjectPlatform.ANDROID) {
            return EnumSet.of(RunnerCapability.ANDROID_UIAUTOMATOR2);
        }
        return Collections.emptySet();
    }
}
