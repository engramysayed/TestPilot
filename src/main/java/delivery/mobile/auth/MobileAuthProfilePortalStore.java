package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import delivery.portal.DeliveryPortalProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Durable auth-profile revision store (M3-06).
 * Path: {@code {storeRoot}/tenants/{tenant}/projects/{projectId}/mobile/auth-profiles.json}.
 * Immutable revisions; edits publish new revision with expectedActiveRevisionId CAS.
 * Never stores raw secret values — declarations/refs only.
 */
@Component
public class MobileAuthProfilePortalStore {
    public static final String FILE = "auth-profiles.json";

    private final DeliveryPortalProperties portalProperties;
    private final ObjectMapper mapper;

    public MobileAuthProfilePortalStore(DeliveryPortalProperties portalProperties, ObjectMapper mapper) {
        this.portalProperties = portalProperties;
        this.mapper = mapper;
    }

    public Path projectMobileDir(String tenantId, String projectId) {
        Path storeRoot = Path.of(portalProperties.getStoreRoot());
        return ScopePaths.projectRoot(storeRoot, TenantId.parse(tenantId), projectId).resolve("mobile");
    }

    public Path storePath(String tenantId, String projectId) {
        return projectMobileDir(tenantId, projectId).resolve(FILE);
    }

    public synchronized List<AuthProfileRevision> listActive(String tenantId, String projectId) throws IOException {
        DurableState state = load(tenantId, projectId);
        List<AuthProfileRevision> out = new ArrayList<>();
        for (Map.Entry<String, ProfileMeta> e : state.profiles.entrySet()) {
            String active = e.getValue().activeRevisionId;
            AuthProfileRevision rev = state.revisions.get(active);
            if (rev != null) {
                out.add(sanitize(rev));
            }
        }
        return out;
    }

    public synchronized Optional<AuthProfileRevision> getActive(String tenantId, String projectId, String profileId)
            throws IOException {
        DurableState state = load(tenantId, projectId);
        ProfileMeta meta = state.profiles.get(profileId);
        if (meta == null) {
            return Optional.empty();
        }
        AuthProfileRevision rev = state.revisions.get(meta.activeRevisionId);
        return rev == null ? Optional.empty() : Optional.of(sanitize(rev));
    }

    public synchronized Optional<AuthProfileRevision> getRevision(
            String tenantId, String projectId, String profileId, String revisionId) throws IOException {
        DurableState state = load(tenantId, projectId);
        AuthProfileRevision rev = state.revisions.get(revisionId);
        if (rev == null || !rev.profileId().equals(profileId)) {
            return Optional.empty();
        }
        // cross-project isolation: revision must belong to this project
        if (!projectId.equals(rev.projectId())) {
            return Optional.empty();
        }
        return Optional.of(sanitize(rev));
    }

    public synchronized List<String> history(String tenantId, String projectId, String profileId) throws IOException {
        DurableState state = load(tenantId, projectId);
        ProfileMeta meta = state.profiles.get(profileId);
        return meta == null ? List.of() : List.copyOf(meta.history);
    }

    public synchronized String activeRevisionId(String tenantId, String projectId, String profileId) throws IOException {
        DurableState state = load(tenantId, projectId);
        ProfileMeta meta = state.profiles.get(profileId);
        return meta == null ? null : meta.activeRevisionId;
    }

    /** First revision for a new profile. */
    public synchronized AuthProfileRevision create(
            String tenantId, String projectId, AuthProfileRevision revision) throws IOException {
        DurableState state = load(tenantId, projectId);
        if (state.profiles.containsKey(revision.profileId())) {
            throw new AuthContractException("PROFILE_EXISTS", "profile already exists: " + revision.profileId());
        }
        if (state.revisions.containsKey(revision.revisionId())) {
            throw new AuthContractException("REVISION_EXISTS", "revision already published: " + revision.revisionId());
        }
        AuthProfileRevision clean = sanitize(withScope(revision, tenantId, projectId));
        ProfileMeta meta = new ProfileMeta();
        meta.activeRevisionId = clean.revisionId();
        meta.history = new ArrayList<>();
        meta.history.add(clean.revisionId());
        state.profiles.put(clean.profileId(), meta);
        state.revisions.put(clean.revisionId(), clean);
        save(tenantId, projectId, state);
        return clean;
    }

    /**
     * Publish edit as new revision; {@code expectedActiveRevisionId} must match active
     * or {@code REVISION_CONFLICT}.
     */
    public synchronized AuthProfileRevision publishEdit(
            String tenantId, String projectId, String expectedActiveRevisionId, AuthProfileRevision newRevision)
            throws IOException {
        DurableState state = load(tenantId, projectId);
        ProfileMeta meta = state.profiles.get(newRevision.profileId());
        if (meta == null) {
            throw new AuthContractException("PROFILE_MISSING", "unknown profile: " + newRevision.profileId());
        }
        if (!meta.activeRevisionId.equals(expectedActiveRevisionId)) {
            throw new AuthContractException(
                    "REVISION_CONFLICT",
                    "expected active " + expectedActiveRevisionId + " but was " + meta.activeRevisionId);
        }
        if (state.revisions.containsKey(newRevision.revisionId())) {
            throw new AuthContractException("REVISION_EXISTS", "revision already published: " + newRevision.revisionId());
        }
        AuthProfileRevision clean = sanitize(withScope(newRevision, tenantId, projectId));
        state.revisions.put(clean.revisionId(), clean);
        meta.history.add(clean.revisionId());
        meta.activeRevisionId = clean.revisionId();
        save(tenantId, projectId, state);
        return clean;
    }

    private AuthProfileRevision withScope(AuthProfileRevision r, String tenantId, String projectId) {
        return new AuthProfileRevision(
                r.profileId(), r.revisionId(), tenantId, projectId,
                r.loginTcId(), r.libraryRevisionId(), r.defaultMode(),
                r.sessionPredicates(), r.signedOutPredicates(),
                r.logoutTcId(), r.setupTcId(), r.account(),
                r.inputDeclarations(), r.protectedCredentialRefs(),
                r.createdAtEpochMs());
    }

    /** Drop any accidental value-bearing fields by rebuilding from contract types only. */
    static AuthProfileRevision sanitize(AuthProfileRevision r) {
        if (r == null) {
            return null;
        }
        List<SecretInputDeclaration> decls = r.inputDeclarations() == null
                ? List.of()
                : r.inputDeclarations().stream()
                .map(d -> new SecretInputDeclaration(d.key(), d.kind(), d.requiredPerRun()))
                .toList();
        List<ProtectedCredentialRef> refs = r.protectedCredentialRefs() == null
                ? List.of()
                : r.protectedCredentialRefs().stream()
                .map(c -> new ProtectedCredentialRef(c.credentialId(), c.logicalKey()))
                .toList();
        LogicalAccountRef account = r.account() == null ? null
                : new LogicalAccountRef(r.account().accountId(), r.account().displayLabel());
        return new AuthProfileRevision(
                r.profileId(), r.revisionId(), r.tenantId(), r.projectId(),
                r.loginTcId(), r.libraryRevisionId(), r.defaultMode(),
                r.sessionPredicates(), r.signedOutPredicates(),
                r.logoutTcId(), r.setupTcId(), account,
                decls, refs, r.createdAtEpochMs());
    }

    private DurableState load(String tenantId, String projectId) throws IOException {
        Path path = storePath(tenantId, projectId);
        if (!Files.isRegularFile(path)) {
            return new DurableState();
        }
        DurableState state = mapper.readValue(Files.readAllBytes(path), DurableState.class);
        if (state.profiles == null) {
            state.profiles = new LinkedHashMap<>();
        }
        if (state.revisions == null) {
            state.revisions = new LinkedHashMap<>();
        }
        return state;
    }

    private void save(String tenantId, String projectId, DurableState state) throws IOException {
        Path dir = projectMobileDir(tenantId, projectId);
        Files.createDirectories(dir);
        Path target = dir.resolve(FILE);
        Path tmp = dir.resolve(FILE + ".tmp");
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(state);
        Files.write(tmp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFail) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class DurableState {
        public Map<String, ProfileMeta> profiles = new LinkedHashMap<>();
        public Map<String, AuthProfileRevision> revisions = new LinkedHashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ProfileMeta {
        public String activeRevisionId;
        public List<String> history = new ArrayList<>();
    }
}
