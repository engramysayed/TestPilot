package delivery.portal.api;

import delivery.portal.service.PortalStore;
import delivery.portal.tunnel.TunnelEnrollmentDiskStore;
import delivery.portal.tunnel.TunnelJobBaseUrlService;
import delivery.portal.tunnel.TunnelRelayService;
import delivery.portal.tunnel.TunnelTokenIndex;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@ConditionalOnProperty(name = "delivery.tunnel.enabled", havingValue = "true")
public class TunnelPublicProxyController {

    private static final Duration RELAY_TIMEOUT = Duration.ofSeconds(90);

    private final PortalStore store;
    private final TunnelRelayService relay;
    private final TunnelEnrollmentDiskStore disk = new TunnelEnrollmentDiskStore();
    private final TunnelJobBaseUrlService jobBaseUrl;

    public TunnelPublicProxyController(PortalStore store, TunnelRelayService relay, TunnelJobBaseUrlService jobBaseUrl) {
        this.store = store;
        this.relay = relay;
        this.jobBaseUrl = jobBaseUrl;
    }

    @RequestMapping("/tunnel/p/{token}/**")
    public ResponseEntity<byte[]> proxy(
            @PathVariable String token,
            HttpServletRequest request
    ) throws Exception {
        String pid = TunnelTokenIndex.projectIdForToken(store.storeRoot(), token)
                .orElse(null);
        if (pid == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Optional<Map<String, Object>> en = disk.latest(store.projectDiskRoot(pid));
        if (en.isEmpty() || !TunnelEnrollmentDiskStore.isLive(en.get(), jobBaseUrl.heartbeatTtl())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Tunnel agent offline".getBytes());
        }
        if (!token.equals(String.valueOf(en.get().getOrDefault("token", "")))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String path = subPath(request.getRequestURI(), token);
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            path = path + "?" + query;
        }
        Map<String, String> headers = hopByHopFilter(readHeaders(request));
        byte[] body = request.getInputStream().readAllBytes();
        TunnelRelayService.RelayResponse res;
        try {
            res = relay.relay(token, request.getMethod(), path, headers, body, RELAY_TIMEOUT);
        } catch (java.util.concurrent.TimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body("Tunnel agent did not answer in time".getBytes());
        } catch (TunnelRelayService.QueueFullException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "2")
                    .body("Tunnel relay busy".getBytes());
        }
        HttpHeaders out = new HttpHeaders();
        res.headers().forEach((k, values) -> {
            if (shouldStripResponseHeader(k) || values == null) {
                return;
            }
            for (String v : values) {
                if ("set-cookie".equalsIgnoreCase(k) && setsPortalCookie(v)) {
                    // Defence in depth (dev loopback mode shares the portal origin): never overwrite portal session.
                    continue;
                }
                out.add(k, v);
            }
        });
        return ResponseEntity.status(res.status()).headers(out).body(res.body());
    }

    private static String subPath(String uri, String token) {
        String prefix = "/tunnel/p/" + token;
        if (uri == null) {
            return "/";
        }
        if (uri.startsWith(prefix)) {
            String rest = uri.substring(prefix.length());
            return rest.isEmpty() ? "/" : rest;
        }
        return uri;
    }

    private static Map<String, String> readHeaders(HttpServletRequest request) {
        Map<String, String> h = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            h.put(n, request.getHeader(n));
        }
        return h;
    }

    private static boolean shouldStripResponseHeader(String name) {
        String k = name == null ? "" : name.toLowerCase();
        return k.equals("connection") || k.equals("transfer-encoding") || k.equals("keep-alive")
                || k.equals("proxy-authenticate") || k.equals("proxy-authorization");
    }

    private static Map<String, String> hopByHopFilter(Map<String, String> in) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
            if (k.equals("host") || k.equals("connection") || k.equals("content-length")
                    || k.equals("transfer-encoding") || k.equals("keep-alive") || k.equals("te")
                    || k.equals("trailer") || k.equals("upgrade") || k.equals("expect")
                    || k.equals("proxy-authorization") || k.equals("proxy-connection")) {
                continue;
            }
            if (k.equals("cookie")) {
                String filtered = stripPortalCookies(e.getValue());
                if (!filtered.isBlank()) {
                    out.put(e.getKey(), filtered);
                }
                continue;
            }
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** Portal auth cookies must never reach the customer's app through the tunnel. */
    private static final java.util.Set<String> PORTAL_COOKIES =
            java.util.Set.of("jsessionid", "session", "remember-me", "xsrf-token");

    static String stripPortalCookies(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : cookieHeader.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            String name = (eq < 0 ? p : p.substring(0, eq)).trim().toLowerCase(java.util.Locale.ROOT);
            if (PORTAL_COOKIES.contains(name)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(p);
        }
        return sb.toString();
    }

    static boolean setsPortalCookie(String setCookie) {
        if (setCookie == null) {
            return false;
        }
        int eq = setCookie.indexOf('=');
        String name = (eq < 0 ? setCookie : setCookie.substring(0, eq)).trim().toLowerCase(java.util.Locale.ROOT);
        return PORTAL_COOKIES.contains(name);
    }
}
