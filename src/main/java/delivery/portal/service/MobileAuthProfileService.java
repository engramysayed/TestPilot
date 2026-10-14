package delivery.portal.service;

import delivery.mobile.auth.AuthContractException;
import delivery.mobile.auth.AuthMode;
import delivery.mobile.auth.AuthProfileRevision;
import delivery.mobile.auth.LogicalAccountRef;
import delivery.mobile.auth.MobileAuthProfilePortalStore;
import delivery.mobile.auth.ProtectedCredentialRef;
import delivery.mobile.auth.SecretInputDeclaration;
import delivery.mobile.auth.SecretInputKind;
import delivery.portal.model.ProjectRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portal auth-profile orchestration (M3-06). OWNER/ADMIN mutate via controller gate.
 * Never accepts or echoes raw secret values.
 */
@Service
public class MobileAuthProfileService {
    private final PortalStore store;
    private final MobileAuthProfilePortalStore profiles;

    public MobileAuthProfileService(PortalStore store, MobileAuthProfilePortalStore profiles) {
        this.store = store;
        this.profiles = profiles;
    }

    public ProjectRecord requireProject(String projectId, Long userId) {
        return store.getOwnedProject(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown project"));
    }

    public List<Map<String, Object>> listSummaries(String projectId, Long userId) {
        ProjectRecord project = requireProject(projectId, userId);
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (AuthProfileRevision rev : profiles.listActive(project.getTenantId(), projectId)) {
                rows.add(toSummary(rev));
            }
            return rows;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "auth profile store error");
        }
    }

    public AuthProfileRevision create(String projectId, Long userId, Map<String, Object> body) {
        ProjectRecord project = requireProject(projectId, userId);
        rejectSecretValues(body);
        String profileId = "ap_" + shortId();
        String revisionId = "apr_" + shortId();
        AuthProfileRevision rev = fromBody(
                profileId, revisionId, project.getTenantId(), projectId, body, System.currentTimeMillis());
        try {
            return profiles.create(project.getTenantId(), projectId, rev);
        } catch (AuthContractException e) {
            throw mapAuth(e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "auth profile store error");
        }
    }

    public Map<String, Object> getProfile(String projectId, String profileId, Long userId, boolean canEdit) {
        ProjectRecord project = requireProject(projectId, userId);
        try {
            AuthProfileRevision active = profiles.getActive(project.getTenantId(), projectId, profileId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown auth profile"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("profileId", active.profileId());
            out.put("activeRevisionId", active.revisionId());
            out.put("revision", active);
            out.put("canEdit", canEdit);
            out.put("history", profiles.history(project.getTenantId(), projectId, profileId));
            return out;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "auth profile store error");
        }
    }

    public AuthProfileRevision getRevision(
            String projectId, String profileId, String revisionId, Long userId) {
        ProjectRecord project = requireProject(projectId, userId);
        try {
            return profiles.getRevision(project.getTenantId(), projectId, profileId, revisionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown revision"));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "auth profile store error");
        }
    }

    public AuthProfileRevision publishEdit(
            String projectId, String profileId, Long userId, Map<String, Object> body) {
        ProjectRecord project = requireProject(projectId, userId);
        rejectSecretValues(body);
        if (body == null || body.get("expectedActiveRevisionId") == null
                || String.valueOf(body.get("expectedActiveRevisionId")).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedActiveRevisionId required");
        }
        String expected = String.valueOf(body.get("expectedActiveRevisionId")).trim();
        String revisionId = "apr_" + shortId();
        AuthProfileRevision rev = fromBody(
                profileId, revisionId, project.getTenantId(), projectId, body, System.currentTimeMillis());
        try {
            // ensure profile exists
            profiles.getActive(project.getTenantId(), projectId, profileId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown auth profile"));
            return profiles.publishEdit(project.getTenantId(), projectId, expected, rev);
        } catch (AuthContractException e) {
            throw mapAuth(e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "auth profile store error");
        }
    }

    public static Map<String, Object> toSummary(AuthProfileRevision rev) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("profileId", rev.profileId());
        row.put("activeRevisionId", rev.revisionId());
        row.put("defaultMode", rev.defaultMode() == null ? null : rev.defaultMode().name());
        row.put("loginTcId", rev.loginTcId());
        String label = rev.account() == null ? null : rev.account().displayLabel();
        if (label != null && !label.isBlank()) {
            row.put("accountLabel", label);
        }
        row.put("createdAtEpochMs", rev.createdAtEpochMs());
        return row;
    }

    @SuppressWarnings("unchecked")
    static AuthProfileRevision fromBody(
            String profileId, String revisionId, String tenantId, String projectId,
            Map<String, Object> body, long createdAt) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        String loginTcId = str(body.get("loginTcId"));
        if (loginTcId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "loginTcId required");
        }
        AuthMode mode;
        try {
            Object rawMode = body.get("defaultMode");
            mode = rawMode == null || String.valueOf(rawMode).isBlank()
                    ? AuthMode.ENSURE_LOGGED_IN
                    : AuthMode.parse(String.valueOf(rawMode));
        } catch (AuthContractException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        List<String> sessionPredicates = stringList(body.get("sessionPredicates"));
        List<String> signedOutPredicates = stringList(body.get("signedOutPredicates"));
        String logoutTcId = str(body.get("logoutTcId"));
        String setupTcId = str(body.get("setupTcId"));
        String libraryRevisionId = str(body.get("libraryRevisionId"));
        LogicalAccountRef account = parseAccount(body.get("account"));
        List<SecretInputDeclaration> decls = parseDecls(body.get("inputDeclarations"));
        List<ProtectedCredentialRef> refs = parseCreds(body.get("protectedCredentialRefs"));
        return new AuthProfileRevision(
                profileId, revisionId, tenantId, projectId,
                loginTcId, libraryRevisionId, mode,
                sessionPredicates, signedOutPredicates,
                logoutTcId, setupTcId, account, decls, refs, createdAt);
    }

    @SuppressWarnings("unchecked")
    private static LogicalAccountRef parseAccount(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> m)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account must be object");
        }
        String accountId = str(m.get("accountId"));
        if (accountId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account.accountId required");
        }
        return new LogicalAccountRef(accountId, str(m.get("displayLabel")));
    }

    @SuppressWarnings("unchecked")
    private static List<SecretInputDeclaration> parseDecls(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inputDeclarations must be array");
        }
        List<SecretInputDeclaration> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inputDeclarations entries must be objects");
            }
            // reject value-bearing keys
            if (m.containsKey("value") || m.containsKey("password") || m.containsKey("otp")
                    || m.containsKey("pin") || m.containsKey("secret")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "secret values are not accepted; declarations only");
            }
            String key = str(m.get("key"));
            SecretInputKind kind = SecretInputKind.parse(str(m.get("kind")));
            boolean required = m.get("requiredPerRun") instanceof Boolean b ? b : kind.requiresPerRunSupply();
            try {
                out.add(new SecretInputDeclaration(key, kind, required));
            } catch (AuthContractException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<ProtectedCredentialRef> parseCreds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "protectedCredentialRefs must be array");
        }
        List<ProtectedCredentialRef> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "protectedCredentialRefs entries must be objects");
            }
            if (m.containsKey("value") || m.containsKey("password") || m.containsKey("secret")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "secret values are not accepted; refs only");
            }
            try {
                out.add(new ProtectedCredentialRef(str(m.get("credentialId")), str(m.get("logicalKey"))));
            } catch (AuthContractException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
        return out;
    }

    private static List<String> stringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expected string array");
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null && !String.valueOf(o).isBlank()) {
                out.add(String.valueOf(o).trim());
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Reject top-level secret value keys on create/edit bodies. */
    static void rejectSecretValues(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        for (String banned : List.of("password", "otp", "pin", "secret", "secretValue", "rawSecret")) {
            if (body.containsKey(banned)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "secret values are not accepted; declarations/refs only");
            }
        }
        Object supplied = body.get("suppliedSecrets");
        if (supplied != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "secret values are not accepted; declarations/refs only");
        }
    }

    private static ResponseStatusException mapAuth(AuthContractException e) {
        if ("REVISION_CONFLICT".equals(e.code())) {
            return new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        if ("PROFILE_MISSING".equals(e.code())) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Expose conflict code for controller ApiError mapping. */
    public static String authErrorCode(Throwable t) {
        if (t instanceof ResponseStatusException rse && rse.getStatusCode().value() == 409) {
            return "REVISION_CONFLICT";
        }
        return null;
    }
}
