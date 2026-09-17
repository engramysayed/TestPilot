package delivery.net;

import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import utils.LogsManager;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Per-thread PAC so Chrome/Edge cannot fetch destinations the job policy rejects.
 * Loopback is bypassed so Chromedriver keeps working; {@link WorkerNetworkGuard}
 * still blocks unapproved loopback page loads. This is not a kernel firewall and
 * does not prove isolation on a deployed shared or dedicated host.
 */
public final class WorkerPac {
    private static final ThreadLocal<Path> INSTALLED = new ThreadLocal<>();

    private WorkerPac() {
    }

    public static Path installForJob(String approvedOrigin) {
        try {
            TargetNetworkPolicy policy = TargetNetworkPolicy.forJob(approvedOrigin);
            Path file = Files.createTempFile("testpilot-worker-", ".pac");
            Files.writeString(file, script(policy), StandardCharsets.UTF_8);
            INSTALLED.set(file);
            return file;
        } catch (Exception e) {
            LogsManager.warn("WORKER_PAC_SKIPPED: " + e.getMessage());
            INSTALLED.remove();
            return null;
        }
    }

    public static void apply(ChromeOptions options) {
        if (options == null) {
            return;
        }
        Path file = INSTALLED.get();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        options.addArguments("--proxy-pac-url=" + file.toAbsolutePath().toUri());
        options.addArguments("--proxy-bypass-list=<-loopback>");
    }

    public static void apply(EdgeOptions options) {
        if (options == null) {
            return;
        }
        Path file = INSTALLED.get();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        options.addArguments("--proxy-pac-url=" + file.toAbsolutePath().toUri());
        options.addArguments("--proxy-bypass-list=<-loopback>");
    }

    public static String script(TargetNetworkPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("function FindProxyForURL(url, host) {\n");
        sb.append("  host = (host || '').toLowerCase();\n");
        String approved = policy == null ? "" : sanitizeHost(policy.approvedHost());
        if (!approved.isBlank()) {
            sb.append("  if (host === '").append(approved).append("' || host === 'www.")
                    .append(approved).append("') return 'DIRECT';\n");
            if (approved.startsWith("www.")) {
                String bare = approved.substring(4);
                sb.append("  if (host === '").append(bare).append("') return 'DIRECT';\n");
            }
        }
        if (policy != null && policy.mode() == TargetNetworkPolicy.Mode.DEDICATED) {
            for (String raw : policy.dedicatedAllowlist()) {
                String[] net = ipv4NetAndMask(raw);
                if (net != null) {
                    sb.append("  if (isInNet(host, '").append(net[0]).append("', '")
                            .append(net[1]).append("')) return 'DIRECT';\n");
                }
            }
        }
        sb.append("  return 'PROXY 127.0.0.1:9';\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String[] ipv4NetAndMask(String cidrOrHost) {
        if (cidrOrHost == null || cidrOrHost.isBlank()) {
            return null;
        }
        String s = cidrOrHost.trim();
        int slash = s.indexOf('/');
        String host = slash < 0 ? s : s.substring(0, slash);
        try {
            InetAddress addr = InetAddress.getByName(host.replace("[", "").replace("]", ""));
            byte[] bytes = addr.getAddress();
            if (bytes.length != 4) {
                return null;
            }
            int prefix = slash < 0 ? 32 : Integer.parseInt(s.substring(slash + 1));
            if (prefix < 0 || prefix > 32) {
                return null;
            }
            int mask = prefix == 0 ? 0 : (int) (0xffffffffL << (32 - prefix));
            String dotted = String.format(Locale.ROOT, "%d.%d.%d.%d",
                    bytes[0] & 0xff, bytes[1] & 0xff, bytes[2] & 0xff, bytes[3] & 0xff);
            String maskDotted = String.format(Locale.ROOT, "%d.%d.%d.%d",
                    (mask >>> 24) & 0xff, (mask >>> 16) & 0xff, (mask >>> 8) & 0xff, mask & 0xff);
            return new String[]{dotted, maskDotted};
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitizeHost(String host) {
        if (host == null) {
            return "";
        }
        return host.toLowerCase(Locale.ROOT).replace("'", "").replace("\\", "").trim();
    }

    /** Visible for tests. */
    static Path installedFile() {
        return INSTALLED.get();
    }

    public static void clear() {
        INSTALLED.remove();
    }
}
