package delivery.portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects /api/tunnel/agent/** POSTs whose Content-Length is over 25 MB BEFORE Jackson reads the body.
 * This is the pre-deserialization bound; TunnelAgentController still enforces a post-deserialization
 * base64 length check as defence in depth.
 */
@Component
public class TunnelAgentBodyLimitFilter extends OncePerRequestFilter {
    static final long MAX_CONTENT_LENGTH = 25L * 1024 * 1024;

    private final AntPathRequestMatcher matcher = new AntPathRequestMatcher("/api/tunnel/agent/**", "POST");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (matcher.matches(request)) {
            long len = request.getContentLengthLong();
            if (len > MAX_CONTENT_LENGTH) {
                response.setStatus(413);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Tunnel agent body exceeds " + MAX_CONTENT_LENGTH + " bytes");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
