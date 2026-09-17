package delivery.portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class LoginThrottleFilter extends OncePerRequestFilter {
    private final LoginThrottle throttle;
    private final AntPathRequestMatcher loginPost = new AntPathRequestMatcher("/login", "POST");

    public LoginThrottleFilter(LoginThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (loginPost.matches(request)) {
            String key = LoginThrottle.key(request.getParameter("username"), request.getRemoteAddr());
            Instant now = Instant.now();
            if (!throttle.allow(key, now)) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(throttle.retryAfterSeconds(key, now)));
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Too many login attempts. Try again later.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
