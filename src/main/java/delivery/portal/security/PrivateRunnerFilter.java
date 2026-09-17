package delivery.portal.security;

import delivery.identity.WorkspaceDirectory;
import delivery.portal.DeliveryPortalProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class PrivateRunnerFilter extends OncePerRequestFilter {
    public static final String TOKEN_PREFIX = "tp_run_";

    private final DeliveryPortalProperties props;

    public PrivateRunnerFilter(DeliveryPortalProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (!path.startsWith("/api/v1/runners")) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "runner token required");
            return;
        }
        String token = header.substring(7).trim();
        if (!token.startsWith(TOKEN_PREFIX)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "runner token required");
            return;
        }
        var identity = WorkspaceDirectory.open(Path.of(props.getStoreRoot())).authenticateRunner(token);
        if (identity.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "revoked or unknown runner");
            return;
        }
        RunnerToken auth = new RunnerToken(identity.get(), token);
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    public static final class RunnerToken extends AbstractAuthenticationToken {
        private final WorkspaceDirectory.RunnerEnrollment enrollment;
        private final String token;

        public RunnerToken(WorkspaceDirectory.RunnerEnrollment enrollment, String token) {
            super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
            this.enrollment = enrollment;
            this.token = token;
            setAuthenticated(true);
        }

        public WorkspaceDirectory.RunnerEnrollment enrollment() {
            return enrollment;
        }

        public String token() {
            return token;
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Object getPrincipal() {
            return enrollment;
        }

        @Override
        public String getName() {
            return enrollment.id();
        }
    }
}
