package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.persistence.PortalUser;
import delivery.portal.roles.ApprovalStatus;
import delivery.portal.roles.RolesService;
import delivery.portal.roles.SystemRoleNames;
import delivery.portal.persistence.PortalUserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AdminSeedRunner implements ApplicationRunner {
    private static final Logger log = LogManager.getLogger(AdminSeedRunner.class);

    private final PortalUserRepository users;
    private final PasswordEncoder encoder;
    private final DeliveryPortalProperties props;
    private final RolesService rolesService;

    public AdminSeedRunner(PortalUserRepository users, PasswordEncoder encoder, DeliveryPortalProperties props, RolesService rolesService) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.rolesService = rolesService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = props.getAdminEmail();
        String password = props.getAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Admin seed skipped — set delivery.admin-email and delivery.admin-password");
            return;
        }
        if ("please-change".equalsIgnoreCase(password.trim())) {
            log.warn("Admin seed skipped — KEEL_ADMIN_PASSWORD still 'please-change'. Set a real value before first boot.");
            return;
        }
        if (users.existsByEmailIgnoreCase(email.trim())) {
            return;
        }
        PortalUser admin = new PortalUser();
        admin.setEmail(email.trim().toLowerCase());
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(PortalUser.Role.ADMIN);
        admin.setEnabled(true);
        admin.setApprovalStatus(ApprovalStatus.APPROVED);
        admin.setApprovedAt(Instant.now());
        rolesService.ensureSystemRolesSeeded();
        rolesService.findByName(SystemRoleNames.OWNER).ifPresent(r -> admin.setRoleId(r.getRoleId()));
        users.save(admin);
        log.info("Seeded admin user {}", admin.getEmail());
    }
}
