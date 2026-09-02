package delivery.portal.service;

import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final PortalUserRepository users;
    private final PasswordEncoder encoder;

    public AccountService(PortalUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }
        PortalUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
    }

    @Transactional
    public PortalUser changeEmail(Long userId, String newEmail, String currentPassword) {
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalized = newEmail.trim().toLowerCase();
        PortalUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (!normalized.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(normalized)) {
            throw new IllegalArgumentException("That email is already in use");
        }
        user.setEmail(normalized);
        return users.save(user);
    }
}
