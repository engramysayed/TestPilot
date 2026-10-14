package delivery.portal.api;

import delivery.portal.roles.RolesService;
import delivery.portal.security.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MePermissionsController {
    private final RolesService roles;
    private final CurrentUserService currentUser;

    public MePermissionsController(RolesService roles, CurrentUserService currentUser) {
        this.roles = roles;
        this.currentUser = currentUser;
    }

    @GetMapping("/permissions")
    public Map<String, Object> me() {
        return roles.mePermissions(currentUser.requireUser());
    }
}
