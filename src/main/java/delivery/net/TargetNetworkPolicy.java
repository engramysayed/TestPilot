package delivery.net;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Browser-worker destination policy. Shared hosting stays on the approved public origin
 * and never opens loopback/private/link-local destinations. Dedicated installs may add
 * an explicit private allowlist that does not apply to shared-host jobs.
 */
public final class TargetNetworkPolicy {
    public enum Mode { SHARED, DEDICATED }

    public record Decision(boolean allowed, String reason, String url) {
        public static Decision allow(String url) {
            return new Decision(true, "allowed", url == null ? "" : url);
        }

        public static Decision block(String url, String reason) {
            return new Decision(false, reason == null ? "blocked" : reason, url == null ? "" : url);
        }
    }

    private final Mode mode;
    private final String approvedHost;
    private final int approvedPort;
    private final boolean approvedIsRestrictedHop;
    private final List<Cidr> dedicatedCidrs;
    private final List<String> dedicatedAllowlist;

    private TargetNetworkPolicy(
            Mode mode,
            String approvedHost,
            int approvedPort,
            List<Cidr> dedicatedCidrs,
            List<String> dedicatedAllowlist
    ) {
        this.mode = mode;
        this.approvedHost = approvedHost;
        this.approvedPort = approvedPort;
        this.approvedIsRestrictedHop = isRestrictedHop(approvedHost);
        this.dedicatedCidrs = dedicatedCidrs;
        this.dedicatedAllowlist = dedicatedAllowlist;
    }

    public static TargetNetworkPolicy shared(String approvedOrigin) {
        Origin o = originOf(approvedOrigin);
        return new TargetNetworkPolicy(Mode.SHARED, o.host, o.port, List.of(), List.of());
    }

    public static TargetNetworkPolicy dedicated(String approvedOrigin, List<String> extraCidrsOrHosts) {
        Origin o = originOf(approvedOrigin);
        List<Cidr> cidrs = new ArrayList<>();
        List<String> allow = new ArrayList<>();
        if (extraCidrsOrHosts != null) {
            for (String raw : extraCidrsOrHosts) {
                Cidr parsed = Cidr.parse(raw);
                if (parsed != null) {
                    cidrs.add(parsed);
                    allow.add(raw.trim());
                }
            }
        }
        return new TargetNetworkPolicy(Mode.DEDICATED, o.host, o.port, List.copyOf(cidrs), List.copyOf(allow));
    }

    public static TargetNetworkPolicy forJob(String approvedOrigin) {
        String mode = first("delivery.install.mode", "DELIVERY_INSTALL_MODE", "shared");
        if ("dedicated".equalsIgnoreCase(mode.trim())) {
            String raw = first("delivery.install.private-cidrs", "DELIVERY_INSTALL_PRIVATE_CIDRS", "");
            List<String> cidrs = new ArrayList<>();
            for (String part : raw.split(",")) {
                if (!part.isBlank()) {
                    cidrs.add(part.trim());
                }
            }
            return dedicated(approvedOrigin, cidrs);
        }
        return shared(approvedOrigin);
    }

    private static String first(String prop, String env, String fallback) {
        String fromProp = System.getProperty(prop, "");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.trim();
        }
        String fromEnv = System.getenv(env);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return fallback;
    }

    public Mode mode() {
        return mode;
    }

    public String approvedHost() {
        return approvedHost;
    }

    /** Raw dedicated CIDR/host strings. Empty on shared installs. */
    public List<String> dedicatedAllowlist() {
        return dedicatedAllowlist;
    }

    public Decision inspect(String url) {
        return inspectInternal(url, false);
    }

    public Decision inspectRedirect(String location, String fromUrl) {
        String resolved = resolveRedirect(location, fromUrl);
        return inspectInternal(resolved, false);
    }

    public Decision inspectSubresource(String url) {
        return inspectInternal(url, true);
    }

    /**
     * Server-initiated outbound destinations (job-status webhooks). Public CI hosts
     * are allowed; loopback/private/link-local/metadata hops follow install mode.
     */
    public Decision inspectOutbound(String url) {
        return inspectInternal(url, true);
    }

    public Decision inspectResolved(String hostname, List<InetAddress> addrs) {
        if (addrs == null || addrs.isEmpty()) {
            return Decision.block(hostname, "unresolved host");
        }
        for (InetAddress addr : addrs) {
            if (addr == null) {
                continue;
            }
            if (isForbiddenAddress(addr) && !dedicatedAllows(addr)) {
                return Decision.block(hostname, "resolved to blocked address " + addr.getHostAddress());
            }
        }
        return Decision.allow(hostname);
    }

    public boolean credentialsAllowedAt(String currentUrl) {
        Decision d = inspect(currentUrl);
        return d.allowed();
    }

    private Decision inspectInternal(String url, boolean subresource) {
        if (url == null || url.isBlank()) {
            return Decision.block(url, "blank url");
        }
        String trimmed = url.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return Decision.block(trimmed, "malformed url");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Decision.block(trimmed, "blocked scheme " + (scheme.isEmpty() ? "(none)" : scheme));
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            // IPv6 literals sometimes need brackets stripped by URI; try authority
            host = hostFromAuthority(uri.getAuthority());
        }
        if (host == null || host.isBlank()) {
            return Decision.block(trimmed, "missing host");
        }
        String hostNorm = stripBrackets(host).toLowerCase(Locale.ROOT);
        InetAddress literal = literalAddress(hostNorm);
        int port = uri.getPort();
        if (matchesApprovedOrigin(hostNorm, port, scheme)) {
            return Decision.allow(trimmed);
        }
        if (isLoopbackHost(hostNorm)) {
            return Decision.block(trimmed, "blocked loopback destination");
        }
        if (literal != null && isForbiddenAddress(literal) && !dedicatedAllows(literal)) {
            return Decision.block(trimmed, reasonFor(literal));
        }
        if (!subresource && !dedicatedAllowsHostOrLiteral(hostNorm, literal)) {
            return Decision.block(trimmed, "host is not the approved origin");
        }
        if (subresource && literal != null && isForbiddenAddress(literal) && !dedicatedAllows(literal)) {
            return Decision.block(trimmed, reasonFor(literal));
        }
        if (subresource && literal == null && isLocalHostname(hostNorm)) {
            return Decision.block(trimmed, "blocked loopback destination");
        }
        return Decision.allow(trimmed);
    }

    private boolean matchesApprovedOrigin(String hostNorm, int port, String scheme) {
        if (!hostMatchesApproved(hostNorm)) {
            return false;
        }
        if (!approvedIsRestrictedHop) {
            return true;
        }
        int expected = approvedPort;
        if (expected < 0) {
            expected = "https".equals(scheme) ? 443 : 80;
        }
        int actual = port < 0 ? ("https".equals(scheme) ? 443 : 80) : port;
        return actual == expected;
    }

    private boolean dedicatedAllowsHostOrLiteral(String hostNorm, InetAddress literal) {
        if (mode != Mode.DEDICATED) {
            return false;
        }
        if (hostMatchesApproved(hostNorm)) {
            return true;
        }
        return literal != null && dedicatedAllows(literal);
    }

    private boolean dedicatedAllows(InetAddress addr) {
        if (mode != Mode.DEDICATED || addr == null) {
            return false;
        }
        if (hostMatchesApproved(stripBrackets(approvedHost))) {
            InetAddress approvedLiteral = literalAddress(approvedHost);
            if (approvedLiteral != null && approvedLiteral.equals(addr)) {
                return true;
            }
        }
        for (Cidr cidr : dedicatedCidrs) {
            if (cidr.contains(addr)) {
                return true;
            }
        }
        return false;
    }

    private boolean hostMatchesApproved(String hostNorm) {
        if (approvedHost == null || approvedHost.isBlank() || hostNorm == null) {
            return false;
        }
        String approved = stripBrackets(approvedHost).toLowerCase(Locale.ROOT);
        return hostNorm.equals(approved) || hostNorm.equals("www." + approved) || ("www." + hostNorm).equals(approved);
    }

    private static String reasonFor(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()) {
            return "blocked loopback destination";
        }
        if (addr.isLinkLocalAddress()) {
            return "blocked link-local destination";
        }
        return "blocked private destination";
    }

    private static boolean isForbiddenAddress(InetAddress addr) {
        if (addr == null) {
            return true;
        }
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 16 && (raw[0] & 0xfe) == 0xfc) {
            return true; // fc00::/7 unique local
        }
        return false;
    }

    private static boolean isLoopbackHost(String hostNorm) {
        return "localhost".equals(hostNorm)
                || "localhost.localdomain".equals(hostNorm)
                || "ip6-localhost".equals(hostNorm);
    }

    private static boolean isLocalHostname(String hostNorm) {
        return isLoopbackHost(hostNorm);
    }

    private static InetAddress literalAddress(String hostNorm) {
        if (hostNorm == null || hostNorm.isBlank()) {
            return null;
        }
        String h = stripBrackets(hostNorm);
        boolean ipv4 = h.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '.');
        boolean ipv6 = h.indexOf(':') >= 0;
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(h);
        } catch (Exception e) {
            return null;
        }
    }

    private static String hostOf(String origin) {
        return originOf(origin).host;
    }

    private static Origin originOf(String origin) {
        if (origin == null || origin.isBlank()) {
            return new Origin("", -1);
        }
        try {
            URI uri = URI.create(origin.trim());
            String host = uri.getHost() == null ? hostFromAuthority(uri.getAuthority()) : uri.getHost();
            host = stripBrackets(host).toLowerCase(Locale.ROOT);
            return new Origin(host, uri.getPort());
        } catch (IllegalArgumentException e) {
            return new Origin(hostOfFallback(origin), -1);
        }
    }

    private static String hostOfFallback(String origin) {
        if (origin == null || origin.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(origin.trim());
            if (uri.getHost() != null) {
                return stripBrackets(uri.getHost()).toLowerCase(Locale.ROOT);
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        String trimmed = origin.trim();
        int slash = trimmed.indexOf("://");
        String rest = slash >= 0 ? trimmed.substring(slash + 3) : trimmed;
        int cut = rest.indexOf('/');
        if (cut >= 0) {
            rest = rest.substring(0, cut);
        }
        int colon = rest.lastIndexOf(':');
        if (colon > 0 && rest.indexOf(']') < 0) {
            rest = rest.substring(0, colon);
        }
        return stripBrackets(rest).toLowerCase(Locale.ROOT);
    }

    private static String hostFromAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return "";
        }
        String a = authority;
        int at = a.lastIndexOf('@');
        if (at >= 0) {
            a = a.substring(at + 1);
        }
        if (a.startsWith("[")) {
            int end = a.indexOf(']');
            return end > 0 ? a.substring(1, end) : a;
        }
        int colon = a.lastIndexOf(':');
        return colon > 0 ? a.substring(0, colon) : a;
    }

    private static String stripBrackets(String host) {
        if (host == null) {
            return "";
        }
        String h = host.trim();
        if (h.startsWith("[") && h.endsWith("]")) {
            return h.substring(1, h.length() - 1);
        }
        return h;
    }

    private static String resolveRedirect(String location, String fromUrl) {
        if (location == null || location.isBlank()) {
            return "";
        }
        String loc = location.trim();
        if (loc.contains("://")) {
            return loc;
        }
        if (fromUrl == null || fromUrl.isBlank()) {
            return loc;
        }
        try {
            return URI.create(fromUrl).resolve(loc).toString();
        } catch (IllegalArgumentException e) {
            return loc;
        }
    }

    private static boolean isRestrictedHop(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        if (isLoopbackHost(host.toLowerCase(Locale.ROOT))) {
            return true;
        }
        InetAddress literal = literalAddress(host);
        return literal != null && isForbiddenAddress(literal);
    }

    private record Origin(String host, int port) {
    }

    private record Cidr(byte[] network, int prefix, int length) {
        static Cidr parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String s = raw.trim();
            int slash = s.indexOf('/');
            String host = slash < 0 ? s : s.substring(0, slash);
            int prefix;
            try {
                InetAddress addr = InetAddress.getByName(stripBrackets(host));
                byte[] bytes = addr.getAddress();
                prefix = slash < 0 ? bytes.length * 8 : Integer.parseInt(s.substring(slash + 1));
                if (prefix < 0 || prefix > bytes.length * 8) {
                    return null;
                }
                return new Cidr(bytes, prefix, bytes.length);
            } catch (Exception e) {
                return null;
            }
        }

        boolean contains(InetAddress addr) {
            byte[] other = addr.getAddress();
            if (other.length != length) {
                return false;
            }
            int full = prefix / 8;
            for (int i = 0; i < full; i++) {
                if (network[i] != other[i]) {
                    return false;
                }
            }
            int rem = prefix % 8;
            if (rem == 0) {
                return true;
            }
            int mask = 0xff << (8 - rem);
            return (network[full] & mask) == (other[full] & mask);
        }
    }
}
