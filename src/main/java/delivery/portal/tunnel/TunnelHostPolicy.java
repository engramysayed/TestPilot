package delivery.portal.tunnel;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Tunnel origin isolation (A5 hardening).
 * <p>
 * Pages relayed through {@code /tunnel/p/{token}/**} are customer content. If they are served on the
 * portal's own origin, their JavaScript runs same-origin with the portal and can call {@code /api/**}
 * as whoever is logged in. So the relay must live on its <b>own host</b>
 * ({@code delivery.tunnel.public-base-url}, e.g. {@code https://tunnel.keel.example}), distinct from the
 * portal host ({@code delivery.public-base-url}).
 * <p>
 * Loopback hosts (localhost / 127.0.0.1 / ::1) are exempt so local dev and tests can run on one port.
 */
public final class TunnelHostPolicy {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");

    private TunnelHostPolicy() {
    }

    public static String host(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String h = URI.create(url.trim()).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isLoopback(String host) {
        return host != null && LOOPBACK.contains(host.toLowerCase(Locale.ROOT));
    }

    /** Result of evaluating the configured portal + tunnel URLs. */
    public record Evaluation(String portalHost, String tunnelHost, boolean devLoopback, boolean misconfigured, String reason) {
        /** Relay may be offered to jobs / served at all. */
        public boolean relayAllowed() {
            return !tunnelHost.isBlank() && !misconfigured;
        }

        /** Host checks are enforced per request (false in loopback dev mode). */
        public boolean enforce() {
            return relayAllowed() && !devLoopback;
        }
    }

    public static Evaluation evaluate(String portalPublicBaseUrl, String tunnelPublicBaseUrl) {
        String portal = host(portalPublicBaseUrl);
        String tunnel = host(tunnelPublicBaseUrl);
        if (tunnel.isBlank()) {
            return new Evaluation(portal, "", false, false, "delivery.tunnel.public-base-url not set");
        }
        if (isLoopback(tunnel)) {
            return new Evaluation(portal, tunnel, true, false, "loopback tunnel host (dev mode, no isolation)");
        }
        if (!portal.isBlank() && portal.equals(tunnel)) {
            return new Evaluation(portal, tunnel, false, true,
                    "delivery.tunnel.public-base-url host '" + tunnel + "' equals the portal host; "
                            + "use a dedicated host such as tunnel." + portal);
        }
        return new Evaluation(portal, tunnel, false, false, "dedicated tunnel host");
    }

    public static boolean isTunnelPath(String path) {
        return path != null && path.startsWith("/tunnel/p/");
    }

    /**
     * @return HTTP status to reject with, or 0 to allow.
     */
    public static int check(Evaluation ev, String requestHost, String path) {
        boolean tunnelPath = isTunnelPath(path);
        if (tunnelPath && !ev.relayAllowed()) {
            return 404;
        }
        if (!ev.enforce()) {
            return 0;
        }
        String h = requestHost == null ? "" : requestHost.toLowerCase(Locale.ROOT);
        boolean onTunnelHost = h.equals(ev.tunnelHost());
        if (tunnelPath && !onTunnelHost) {
            return 404; // relay content never served on the portal origin
        }
        if (!tunnelPath && onTunnelHost) {
            return 404; // portal UI/API never served on the tunnel origin
        }
        return 0;
    }
}
