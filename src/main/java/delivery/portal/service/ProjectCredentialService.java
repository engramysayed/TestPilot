package delivery.portal.service;

import delivery.portal.persistence.ProjectCredentialEntity;
import delivery.portal.persistence.ProjectCredentialRepository;
import delivery.portal.security.JobSecretCrypto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ProjectCredentialService {
    private final ProjectCredentialRepository repository;

    public ProjectCredentialService(ProjectCredentialRepository repository) {
        this.repository = repository;
    }

    public record CredentialSummary(String profileName, String username, boolean hasPassword) {
    }

    public record ResolvedCredential(String username, String passwordPlain) {
    }

    public List<CredentialSummary> list(String projectId) {
        return repository.findByProjectIdOrderByProfileNameAsc(projectId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public CredentialSummary create(String projectId, String profileName, String username, String password) {
        validateProfileName(profileName);
        validateUsername(username);
        requirePassword(password);
        if (repository.findByProjectIdAndProfileName(projectId, profileName).isPresent()) {
            throw new DuplicateProfileException(profileName);
        }
        ProjectCredentialEntity entity = new ProjectCredentialEntity();
        entity.setProjectId(projectId);
        entity.setProfileName(profileName.trim());
        entity.setUsername(username.trim());
        entity.setPasswordCipher(JobSecretCrypto.encrypt(password));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toSummary(repository.save(entity));
    }

    @Transactional
    public Optional<CredentialSummary> update(String projectId, String profileName, String username, String optionalPassword) {
        return repository.findByProjectIdAndProfileName(projectId, profileName)
                .map(entity -> {
                    if (username != null && !username.isBlank()) {
                        entity.setUsername(username.trim());
                    }
                    if (optionalPassword != null && !optionalPassword.isBlank()) {
                        entity.setPasswordCipher(JobSecretCrypto.encrypt(optionalPassword));
                    }
                    entity.setUpdatedAt(Instant.now());
                    return toSummary(repository.save(entity));
                });
    }

    @Transactional
    public boolean delete(String projectId, String profileName) {
        if (repository.findByProjectIdAndProfileName(projectId, profileName).isEmpty()) {
            return false;
        }
        repository.deleteByProjectIdAndProfileName(projectId, profileName);
        return true;
    }

    public ResolvedCredential resolveForJob(String projectId, String profileName) {
        ProjectCredentialEntity entity = repository.findByProjectIdAndProfileName(projectId, profileName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown credential profile: " + profileName));
        return new ResolvedCredential(
                entity.getUsername(),
                JobSecretCrypto.decrypt(entity.getPasswordCipher())
        );
    }

    private CredentialSummary toSummary(ProjectCredentialEntity entity) {
        return new CredentialSummary(
                entity.getProfileName(),
                entity.getUsername(),
                entity.getPasswordCipher() != null && !entity.getPasswordCipher().isBlank()
        );
    }

    private static void validateProfileName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("profileName is required");
        }
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
    }

    private static void requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
    }

    public static final class DuplicateProfileException extends RuntimeException {
        private final String profileName;

        public DuplicateProfileException(String profileName) {
            super("Profile name already used: " + profileName);
            this.profileName = profileName;
        }

        public String getProfileName() {
            return profileName;
        }
    }
}
