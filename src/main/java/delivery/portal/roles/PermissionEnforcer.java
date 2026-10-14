package delivery.portal.roles;

import delivery.portal.persistence.PortalUser;
import org.springframework.stereotype.Component;

/**
 * Gates feature job starts and credits.admin. Does not conflate with INSUFFICIENT_CREDITS.
 */
@Component
public class PermissionEnforcer {
    private final RolesService roles;

    public PermissionEnforcer(RolesService roles) {
        this.roles = roles;
    }

    public void requireApproved(PortalUser user) {
        ApprovalStatus status = user.getApprovalStatus();
        if (!status.canStartFeatureJobs()) {
            throw RoleRequiredException.account(status);
        }
    }

    public void requireFeatureJob(PortalUser user, String jobKind) {
        requireApproved(user);
        String code = PermissionCodes.forJobKind(jobKind);
        roles.requirePermission(user, code);
    }

    public void requireCreditsAdmin(PortalUser user) {
        roles.requirePermission(user, PermissionCodes.CREDITS_ADMIN);
    }

    public void requireRolesManage(PortalUser user) {
        roles.requireRolesManage(user);
    }
}
