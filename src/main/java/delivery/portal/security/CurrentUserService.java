package delivery.portal.security;

import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService {
    private final PortalUserRepository users;

    public CurrentUserService(PortalUserRepository users) {
        this.users = users;
    }

    public PortalUser requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("Not authenticated");
        }
        String email = auth.getName();
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    public Long requireUserId() {
        return requireUser().getId();
    }
}
