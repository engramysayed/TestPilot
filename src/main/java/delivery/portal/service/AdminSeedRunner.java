package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeedRunner implements ApplicationRunner {
    private static final Logger log = LogManager.getLogger(AdminSeedRunner.class);

    private final PortalUserRepository users;
    private final PasswordEncoder encoder;
    private final DeliveryPortalProperties props;

    public AdminSeedRunner(PortalUserRepository users, PasswordEncoder encoder, DeliveryPortalProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = props.getAdminEmail();
        String password = props.getAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Admin seed skipped — set delivery.admin-email and delivery.admin-password");
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
        users.save(admin);
        log.info("Seeded admin user {}", admin.getEmail());
    }
}
