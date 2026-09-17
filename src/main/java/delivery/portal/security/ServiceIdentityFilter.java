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

public class ServiceIdentityFilter extends OncePerRequestFilter {
    public static final String TOKEN_PREFIX = "tp_svc_";

    private final DeliveryPortalProperties props;

    public ServiceIdentityFilter(DeliveryPortalProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(7).trim();
        if (!token.startsWith(TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        var identity = WorkspaceDirectory.open(Path.of(props.getStoreRoot())).authenticateService(token);
        if (identity.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "revoked or unknown service identity");
            return;
        }
        ServiceToken auth = new ServiceToken(identity.get());
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    public static final class ServiceToken extends AbstractAuthenticationToken {
        private final WorkspaceDirectory.ServiceIdentity identity;

        public ServiceToken(WorkspaceDirectory.ServiceIdentity identity) {
            super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
            this.identity = identity;
            setAuthenticated(true);
        }

        public WorkspaceDirectory.ServiceIdentity identity() {
            return identity;
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Object getPrincipal() {
            return identity;
        }

        @Override
        public String getName() {
            return identity.id();
        }
    }
}
