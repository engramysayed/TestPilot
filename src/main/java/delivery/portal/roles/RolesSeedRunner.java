package delivery.portal.roles;

import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.persistence.roles.RoleEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds OWNER/ADMIN/MEMBER and backfills seeded admin to APPROVED + OWNER roleId.
 * Does not grant credits.
 */
@Component
@Order(50)
public class RolesSeedRunner implements ApplicationRunner {
    private static final Logger log = LogManager.getLogger(RolesSeedRunner.class);

    private final RolesService rolesService;
    private final PortalUserRepository users;

    public RolesSeedRunner(RolesService rolesService, PortalUserRepository users) {
        this.rolesService = rolesService;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        rolesService.ensureSystemRolesSeeded();
        RoleEntity owner = rolesService.findByName(SystemRoleNames.OWNER).orElse(null);
        if (owner == null) {
            return;
        }
        for (PortalUser u : users.findAllByOrderByIdAsc()) {
            if (u.getRole() == PortalUser.Role.ADMIN
                    && (u.getRoleId() == null || u.getRoleId().isBlank())) {
                u.setRoleId(owner.getRoleId());
                if (u.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL
                        || u.getApprovalStatus() == null) {
                    // Seeded platform admin is considered approved (bootstrap only)
                    u.setApprovalStatus(ApprovalStatus.APPROVED);
                    u.setApprovedAt(java.time.Instant.now());
                }
                users.save(u);
                log.info("Backfilled admin {} → OWNER role + APPROVED", u.getEmail());
            }
        }
    }
}
