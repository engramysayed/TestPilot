package delivery.mobile.appium;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hard rules for M1-06 process teardown:
 * <ul>
 *   <li>Never kill a PID that is not in the owned set for the lease/job.</li>
 *   <li>Never issue {@code adb kill-server} (or any kill-server) on Appium/ADB paths.</li>
 * </ul>
 */
public final class AppiumProcessKillPolicy {
    private AppiumProcessKillPolicy() {
    }

    public static void assertMayKill(long pid, Set<Long> ownedPids) {
        if (ownedPids == null || !ownedPids.contains(pid)) {
            throw new AppiumLifecycleException(AppiumErrorCode.FORBIDDEN_KILL,
                    "refusing to kill non-owned pid=" + pid + " owned=" + ownedPids);
        }
    }

    public static void assertMayKill(OwnedAppiumProcess process, Collection<OwnedAppiumProcess> owned) {
        if (process == null) {
            throw new AppiumLifecycleException(AppiumErrorCode.NOT_OWNED, "null process");
        }
        if (owned == null) {
            throw new AppiumLifecycleException(AppiumErrorCode.FORBIDDEN_KILL, "no owned process set");
        }
        boolean found = false;
        for (OwnedAppiumProcess o : owned) {
            if (o != null && process.getProcessRef().equals(o.getProcessRef()) && o.getPid() == process.getPid()) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new AppiumLifecycleException(AppiumErrorCode.FORBIDDEN_KILL,
                    "refusing to kill non-owned processRef=" + process.getProcessRef());
        }
    }

    /** True when any arg would run {@code adb kill-server} or a bare kill-server. */
    public static boolean containsForbiddenKillServer(List<String> args) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a == null) {
                continue;
            }
            String lower = a.toLowerCase(Locale.ROOT);
            if ("kill-server".equals(lower) || "kill_server".equals(lower)) {
                return true;
            }
            // reject "adb kill-server" even if fused oddly
            if (lower.contains("kill-server")) {
                return true;
            }
        }
        return false;
    }

    public static void rejectForbiddenArgs(List<String> args) {
        if (containsForbiddenKillServer(args)) {
            throw new AppiumLifecycleException(AppiumErrorCode.FORBIDDEN_KILL,
                    "adb kill-server / kill-server is forbidden on mobile worker paths");
        }
    }
}
