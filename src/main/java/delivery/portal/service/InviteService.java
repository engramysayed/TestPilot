package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.persistence.Invite;
import delivery.portal.persistence.InviteRepository;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class InviteService {
    public record InviteCreateResult(Invite invite, boolean emailSent, String acceptUrl) {
    }

    private final InviteRepository invites;
    private final PortalUserRepository users;
    private final PasswordEncoder encoder;
    private final DeliveryPortalProperties props;
    private final InviteMailService mail;

    public InviteService(InviteRepository invites, PortalUserRepository users,
                         PasswordEncoder encoder, DeliveryPortalProperties props,
                         InviteMailService mail) {
        this.invites = invites;
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.mail = mail;
    }

    @Transactional
    public InviteCreateResult createInvite(String email, Long createdByUserId, boolean sendEmail) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new IllegalArgumentException("User already exists for this email");
        }
        Invite invite = new Invite();
        invite.setEmail(normalized);
        invite.setToken(UUID.randomUUID().toString().replace("-", ""));
        invite.setExpiresAt(Instant.now().plus(props.getInviteExpiryDays(), ChronoUnit.DAYS));
        invite.setCreatedByUserId(createdByUserId);
        invite.setUsed(false);
        invites.save(invite);

        String acceptUrl = props.getPublicBaseUrl().replaceAll("/$", "") + "/invite/" + invite.getToken();
        boolean sent = false;
        if (sendEmail) {
            sent = mail.sendInvite(invite);
        }
        return new InviteCreateResult(invite, sent, acceptUrl);
    }

    public Invite requireValidInvite(String token) {
        Invite invite = invites.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite"));
        if (invite.isUsed()) {
            throw new IllegalArgumentException("Invite already used");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invite expired");
        }
        return invite;
    }

    @Transactional
    public PortalUser acceptInvite(String token, String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        Invite invite = requireValidInvite(token);
        if (users.existsByEmailIgnoreCase(invite.getEmail())) {
            throw new IllegalArgumentException("User already exists");
        }
        PortalUser user = new PortalUser();
        user.setEmail(invite.getEmail());
        user.setPasswordHash(encoder.encode(password));
        user.setRole(PortalUser.Role.USER);
        user.setEnabled(true);
        users.save(user);
        invite.setUsed(true);
        invites.save(invite);
        return user;
    }

    public boolean mailConfigured() {
        return mail.isConfigured();
    }
}
