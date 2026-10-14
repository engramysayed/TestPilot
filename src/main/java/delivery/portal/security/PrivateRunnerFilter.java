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

/**
 * Runner authentication for /api/v1/runners (bearer runner token) and /api/v2/runners (bearer runner token
 * + HMAC request signature, B06). B06 audit: there is no loopback or IP-based trust; a request from
 * 127.0.0.1 without credentials gets 401 like any other.
 */
public class PrivateRunnerFilter extends OncePerRequestFilter {
    public static final String TOKEN_PREFIX = "tp_run_";

    private final DeliveryPortalProperties props;
    private final RunnerRequestVerifier verifier;

    public PrivateRunnerFilter(DeliveryPortalProperties props) {
        this(props, new RunnerRequestVerifier());
    }

    public PrivateRunnerFilter(DeliveryPortalProperties props, RunnerRequestVerifier verifier) {
        this.props = props;
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        boolean v2 = path.equals(keel.contract.runner.RunnerHeaders.V2_PREFIX)
                || path.startsWith(keel.contract.runner.RunnerHeaders.V2_PREFIX + "/");
        if (!path.startsWith(keel.contract.runner.RunnerHeaders.V1_PREFIX) && !v2) {
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
        HttpServletRequest forward = request;
        if (v2) {
            CachedBodyRequest cached;
            try {
                cached = new CachedBodyRequest(request);
            } catch (CachedBodyRequest.BodyTooLarge e) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "request body too large");
                return;
            }
            RunnerRequestVerifier.Result result = verifier.verify(token, request.getMethod(), path,
                    request.getQueryString(),
                    request.getHeader(keel.contract.runner.RunnerHeaders.TIMESTAMP),
                    request.getHeader(keel.contract.runner.RunnerHeaders.NONCE),
                    cached.body(),
                    request.getHeader(keel.contract.runner.RunnerHeaders.SIGNATURE));
            if (result != RunnerRequestVerifier.Result.OK) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "runner signature rejected: " + result);
                return;
            }
            forward = cached;
        }
        RunnerToken auth = new RunnerToken(identity.get(), token);
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(forward, response);
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
