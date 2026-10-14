package delivery.portal.roles;

import delivery.portal.persistence.PortalUser;
import delivery.portal.security.CurrentUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Enforces {@code credits.admin} on /api/admin/credits/** (additive for parallel Credits slice).
 * When unauthenticated, defers to the SecurityFilterChain (avoids Boot double-registering this
 * {@code @Bean} Filter outside the chain and returning 401 before Basic auth runs).
 */
@Order(60)
public class CreditsAdminPermissionFilter extends OncePerRequestFilter {
    private final CurrentUserService currentUser;
    private final PermissionEnforcer enforcer;

    public CreditsAdminPermissionFilter(CurrentUserService currentUser, PermissionEnforcer enforcer) {
        this.currentUser = currentUser;
        this.enforcer = enforcer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/admin/credits");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            PortalUser user = currentUser.requireUser();
            enforcer.requireCreditsAdmin(user);
            filterChain.doFilter(request, response);
        } catch (RoleRequiredException e) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, e.errorCode(), e.getMessage());
        } catch (IllegalStateException e) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated");
        }
    }

    private static void writeJson(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safeMsg = message == null ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + safeMsg + "\"}");
    }
}