package delivery.portal.tunnel;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs before Spring Security: keeps relayed customer pages and the portal on different origins.
 * See {@link TunnelHostPolicy}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@ConditionalOnProperty(name = "delivery.tunnel.enabled", havingValue = "true")
public class TunnelHostIsolationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TunnelHostIsolationFilter.class);

    private final TunnelHostPolicy.Evaluation evaluation;

    public TunnelHostIsolationFilter(
            @Value("${delivery.public-base-url:}") String portalBaseUrl,
            @Value("${delivery.tunnel.public-base-url:}") String tunnelBaseUrl
    ) {
        this.evaluation = TunnelHostPolicy.evaluate(portalBaseUrl, tunnelBaseUrl);
        if (evaluation.misconfigured()) {
            log.error("Tunnel relay DISABLED: {}", evaluation.reason());
        } else if (evaluation.devLoopback()) {
            log.warn("Tunnel relay on loopback host '{}' — no origin isolation (dev only)", evaluation.tunnelHost());
        } else if (evaluation.relayAllowed()) {
            log.info("Tunnel relay isolated on host '{}' (portal '{}')", evaluation.tunnelHost(), evaluation.portalHost());
        }
    }

    public TunnelHostPolicy.Evaluation evaluation() {
        return evaluation;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        int status = TunnelHostPolicy.check(evaluation, request.getServerName(), path);
        if (status != 0) {
            response.setStatus(status);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(TunnelHostPolicy.isTunnelPath(path) && evaluation.misconfigured()
                    ? "Tunnel relay disabled: " + evaluation.reason()
                    : "Not found");
            return;
        }
        if (TunnelHostPolicy.isTunnelPath(path)) {
            // Token lives in the URL: never leak it via Referer; keep relayed pages out of search indexes.
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader("X-Robots-Tag", "noindex, nofollow");
        }
        chain.doFilter(request, response);
    }
}
