package delivery.portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * D11: state-changing {@code /api/**} calls must send {@code X-Keel-Requested-With: Keel}
 * (CSRF remains off for multipart convenience; this header is the substitute gate).
 * Legacy {@code X-TestPilot-Requested-With: TestPilot} is still accepted.
 */
public class ApiRequestHeaderFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Keel-Requested-With";
    public static final String VALUE = "Keel";
    public static final String LEGACY_HEADER = "X-TestPilot-Requested-With";
    public static final String LEGACY_VALUE = "TestPilot";

    private static final Set<String> SAFE = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        if (path.startsWith("/api/") && !SAFE.contains(method)) {
            if (!headerOk(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Missing or invalid " + HEADER + " header");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean headerOk(HttpServletRequest request) {
        String keel = request.getHeader(HEADER);
        if (keel != null && VALUE.equalsIgnoreCase(keel.trim())) {
            return true;
        }
        String legacy = request.getHeader(LEGACY_HEADER);
        return legacy != null && LEGACY_VALUE.equalsIgnoreCase(legacy.trim());
    }
}
