package delivery.identity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * File-backed workspace registry. Tenant ids are opaque; user ids and display
 * slugs are membership/metadata and can change without renaming storage.
 */
public final class WorkspaceDirectory {
    public static final String FILE_NAME = "directory.json";

    public record AuditEvent(String at, long actorUserId, String action, long targetUserId, String detail) {
    }

    public record ServiceIdentity(String id, TenantId tenant, WorkspaceRole role, String label, boolean revoked) {
    }

    public record CreatedService(String id, WorkspaceRole role, String token) {
    }

    public record PendingInvite(String email, WorkspaceRole role, long invitedBy, String at) {
    }

    private final Path storeRoot;
    private final Path file;

    private WorkspaceDirectory(Path storeRoot, Path file) {
        this.storeRoot = storeRoot;
        this.file = file;
    }

    public static WorkspaceDirectory open(Path storeRoot) {
        if (storeRoot == null) {
            throw new IllegalArgumentException("storeRoot is required");
        }
        Path file = storeRoot.resolve("tenants").resolve(FILE_NAME);
        return new WorkspaceDirectory(storeRoot, file);
    }

    public TenantId ensurePersonalWorkspace(long userId) throws Exception {
        if (userId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        return mutate(root -> {
            TenantId existing = ownerTenant(root, userId);
            if (existing != null) {
                return existing;
            }
            TenantId minted = TenantId.mint();
            putTenant(root, minted, "PERSONAL", "", "user:" + userId);
            addMembership(root, minted, userId, WorkspaceRole.OWNER);
            return minted;
        });
    }

    public TenantId ensureDedicatedWorkspace(String displaySlug) throws Exception {
        String slug = normalizeSlug(displaySlug);
        return mutate(root -> {
            TenantId existing = tenantBySlug(root, slug);
            if (existing != null) {
                return existing;
            }
            TenantId minted = TenantId.mint();
            putTenant(root, minted, "DEDICATED", slug, "");
            return minted;
        });
    }

    public void renameDisplaySlug(TenantId tenant, String displaySlug) throws Exception {
        String slug = normalizeSlug(displaySlug);
        mutate(root -> {
            JSONObject tenants = root.getJSONObject("tenants");
            JSONObject row = tenants.optJSONObject(tenant.value());
            if (row == null) {
                throw new IllegalArgumentException("unknown tenant: " + tenant);
            }
            row.put("displaySlug", slug);
            return tenant;
        });
    }

    public String displaySlug(TenantId tenant) throws Exception {
        JSONObject root = read();
        JSONObject row = root.getJSONObject("tenants").optJSONObject(tenant.value());
        return row == null ? "" : row.optString("displaySlug", "");
    }

    public void rebindAccount(long fromUserId, long toUserId) throws Exception {
        if (fromUserId <= 0 || toUserId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        mutate(root -> {
            JSONArray memberships = root.getJSONArray("memberships");
            TenantId rebound = null;
            for (int i = 0; i < memberships.length(); i++) {
                JSONObject row = memberships.getJSONObject(i);
                if (row.optLong("userId") == fromUserId) {
                    row.put("userId", toUserId);
                    String tenantId = row.optString("tenantId", "");
                    JSONObject tenant = root.getJSONObject("tenants").optJSONObject(tenantId);
                    if (tenant != null && ("user:" + fromUserId).equals(tenant.optString("bootstrapAlias"))) {
                        tenant.put("bootstrapAlias", "user:" + toUserId);
                    }
                    if (rebound == null && !tenantId.isBlank()) {
                        rebound = TenantId.parse(tenantId);
                    }
                }
            }
            if (rebound == null) {
                throw new IllegalArgumentException("no membership for user " + fromUserId);
            }
            return rebound;
        });
    }

    public void addMember(TenantId tenant, long userId, WorkspaceRole role) throws Exception {
        addMember(tenant, userId, role, 0L);
    }

    public void addMember(TenantId tenant, long userId, WorkspaceRole role, long actorUserId) throws Exception {
        if (tenant == null || userId <= 0 || role == null) {
            throw new IllegalArgumentException("tenant, user and role are required");
        }
        if (role == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("use transferOwnership to grant OWNER");
        }
        mutate(root -> {
            requireActorAdministers(root, tenant, actorUserId);
            JSONArray memberships = root.getJSONArray("memberships");
            for (int i = 0; i < memberships.length(); i++) {
                JSONObject row = memberships.getJSONObject(i);
                if (tenant.value().equals(row.optString("tenantId")) && row.optLong("userId") == userId) {
                    row.put("role", role.name());
                    appendAudit(root, tenant, actorUserId, "SET_ROLE", userId, role.name());
                    return tenant;
                }
            }
            addMembership(root, tenant, userId, role);
            appendAudit(root, tenant, actorUserId, "ADD_MEMBER", userId, role.name());
            return tenant;
        });
    }

    public void removeMember(TenantId tenant, long userId, long actorUserId) throws Exception {
        mutate(root -> {
            requireActorAdministers(root, tenant, actorUserId);
            JSONArray memberships = root.getJSONArray("memberships");
            int found = -1;
            String foundRole = "";
            for (int i = 0; i < memberships.length(); i++) {
                JSONObject row = memberships.getJSONObject(i);
                if (tenant.value().equals(row.optString("tenantId")) && row.optLong("userId") == userId) {
                    found = i;
                    foundRole = row.optString("role", "");
                    break;
                }
            }
            if (found < 0) {
                throw new IllegalArgumentException("user " + userId + " is not a member of " + tenant);
            }
            if (WorkspaceRole.OWNER.name().equals(foundRole)) {
                throw new IllegalStateException("cannot remove the workspace owner; transfer ownership first");
            }
            memberships.remove(found);
            appendAudit(root, tenant, actorUserId, "REMOVE_MEMBER", userId, foundRole);
            return tenant;
        });
    }

    public void transferOwnership(TenantId tenant, long actorUserId, long toUserId) throws Exception {
        if (tenant == null || actorUserId <= 0 || toUserId <= 0) {
            throw new IllegalArgumentException("tenant and users are required");
        }
        if (actorUserId == toUserId) {
            throw new IllegalArgumentException("cannot transfer ownership to self");
        }
        mutate(root -> {
            requireActorAdministers(root, tenant, actorUserId);
            JSONObject target = membershipRow(root, tenant, toUserId);
            if (target == null) {
                throw new IllegalArgumentException("target is not a member of " + tenant);
            }
            JSONObject actor = membershipRow(root, tenant, actorUserId);
            if (actor == null) {
                throw new SecurityException("user " + actorUserId + " cannot administer " + tenant);
            }
            actor.put("role", WorkspaceRole.ADMIN.name());
            target.put("role", WorkspaceRole.OWNER.name());
            appendAudit(root, tenant, actorUserId, "TRANSFER_OWNERSHIP", toUserId, "from=" + actorUserId);
            return tenant;
        });
    }

    public List<AuditEvent> audit(TenantId tenant) throws Exception {
        JSONObject root = read();
        JSONArray events = root.optJSONArray("audit");
        List<AuditEvent> out = new ArrayList<>();
        if (events == null || tenant == null) {
            return List.of();
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject row = events.getJSONObject(i);
            if (tenant.value().equals(row.optString("tenantId"))) {
                out.add(new AuditEvent(
                        row.optString("at"),
                        row.optLong("actorUserId"),
                        row.optString("action"),
                        row.optLong("targetUserId"),
                        row.optString("detail")));
            }
        }
        return List.copyOf(out);
    }

    public CreatedService createServiceIdentity(
            TenantId tenant, long actorUserId, WorkspaceRole role, String label
    ) throws Exception {
        if (role == null || role == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("service identity cannot be OWNER");
        }
        String token = "tp_svc_" + UUID.randomUUID().toString().replace("-", "");
        String id = "svc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        mutate(root -> {
            requireActorAdministers(root, tenant, actorUserId);
            JSONArray services = ensureArray(root, "services");
            JSONObject row = new JSONObject();
            row.put("id", id);
            row.put("tenantId", tenant.value());
            row.put("role", role.name());
            row.put("label", label == null ? "" : label);
            row.put("tokenHash", sha256(token));
            row.put("createdBy", actorUserId);
            row.put("createdAt", Instant.now().toString());
            row.put("revokedAt", "");
            services.put(row);
            appendAudit(root, tenant, actorUserId, "CREATE_SERVICE", 0L, id + ":" + role.name());
            return tenant;
        });
        return new CreatedService(id, role, token);
    }

    public void revokeServiceIdentity(TenantId tenant, String serviceId, long actorUserId) throws Exception {
        mutate(root -> {
            requireActorAdministers(root, tenant, actorUserId);
            JSONObject row = serviceRow(root, tenant, serviceId);
            if (row == null) {
                throw new IllegalArgumentException("unknown service identity: " + serviceId);
            }
            row.put("revokedAt", Instant.now().toString());
            appendAudit(root, tenant, actorUserId, "REVOKE_SERVICE", 0L, serviceId);
            return tenant;
        });
    }

    public Optional<ServiceIdentity> authenticateService(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            String hash = sha256(token);
            JSONObject root = read();
            JSONArray services = root.optJSONArray("services");
            if (services == null) {
                return Optional.empty();
            }
            for (int i = 0; i < services.length(); i++) {
                JSONObject row = services.getJSONObject(i);
                if (hash.equals(row.optString("tokenHash")) && row.optString("revokedAt", "").isBlank()) {
                    return Optional.of(new ServiceIdentity(
                            row.optString("id"),
                            TenantId.parse(row.getString("tenantId")),
                            WorkspaceRole.valueOf(row.optString("role")),
                            row.optString("label"),
                            false));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public WorkspaceRole role(TenantId tenant, long userId) {
        try {
            JSONObject root = read();
            JSONArray memberships = root.getJSONArray("memberships");
            for (int i = 0; i < memberships.length(); i++) {
                JSONObject row = memberships.getJSONObject(i);
                if (tenant.value().equals(row.optString("tenantId")) && row.optLong("userId") == userId) {
                    return WorkspaceRole.valueOf(row.optString("role", WorkspaceRole.MEMBER.name()));
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean canOperate(TenantId tenant, long userId) {
        WorkspaceRole role = role(tenant, userId);
        return role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN;
    }

    public boolean canAdminister(TenantId tenant, long userId) {
        return role(tenant, userId) == WorkspaceRole.OWNER;
    }

    public void requireOperate(TenantId tenant, long userId) {
        if (!canOperate(tenant, userId)) {
            throw new SecurityException("user " + userId + " cannot operate on " + tenant);
        }
    }

    public void requireAdminister(TenantId tenant, long userId) {
        if (!canAdminister(tenant, userId)) {
            throw new SecurityException("user " + userId + " cannot administer " + tenant);
        }
    }

    public boolean isMember(TenantId tenant, long userId) {
        try {
            JSONObject root = read();
            JSONArray memberships = root.getJSONArray("memberships");
            for (int i = 0; i < memberships.length(); i++) {
                JSONObject row = memberships.getJSONObject(i);
                if (tenant.value().equals(row.optString("tenantId")) && row.optLong("userId") == userId) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void requireMember(TenantId tenant, long userId) {
        if (!isMember(tenant, userId)) {
            throw new SecurityException("user " + userId + " is not a member of " + tenant);
        }
    }

    public List<WorkspaceMembership> listMembers(TenantId tenant) throws Exception {
        JSONObject root = read();
        JSONArray memberships = root.getJSONArray("memberships");
        List<WorkspaceMembership> out = new ArrayList<>();
        if (tenant == null) {
            return List.of();
        }
        for (int i = 0; i < memberships.length(); i++) {
            JSONObject row = memberships.getJSONObject(i);
            if (tenant.value().equals(row.optString("tenantId"))) {
                out.add(new WorkspaceMembership(
                        tenant,
                        row.optLong("userId"),
                        WorkspaceRole.valueOf(row.optString("role", WorkspaceRole.MEMBER.name()))));
            }
        }
        return List.copyOf(out);
    }

    public List<ServiceIdentity> listServices(TenantId tenant) throws Exception {
        JSONObject root = read();
        JSONArray services = root.optJSONArray("services");
        List<ServiceIdentity> out = new ArrayList<>();
        if (services == null || tenant == null) {
            return List.of();
        }
        for (int i = 0; i < services.length(); i++) {
            JSONObject row = services.getJSONObject(i);
            if (tenant.value().equals(row.optString("tenantId"))) {
                out.add(new ServiceIdentity(
                        row.optString("id"),
                        tenant,
                        WorkspaceRole.valueOf(row.optString("role", WorkspaceRole.MEMBER.name())),
                        row.optString("label"),
                        !row.optString("revokedAt", "").isBlank()));
            }
        }
        return List.copyOf(out);
    }

    public void inviteEmail(TenantId tenant, long actorUserId, String email, WorkspaceRole role) throws Exception {
        if (tenant == null || actorUserId <= 0 || role == null) {
            throw new IllegalArgumentException("tenant, actor and role are required");
        }
        if (role == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("use transferOwnership to grant OWNER");
        }
        String normalized = normalizeEmail(email);
        mutate(root -> {
            requireActorAdministers(root, tenant, actorUserId);
            JSONArray invites = ensureArray(root, "invites");
            for (int i = 0; i < invites.length(); i++) {
                JSONObject row = invites.getJSONObject(i);
                if (tenant.value().equals(row.optString("tenantId"))
                        && normalized.equals(row.optString("email"))) {
                    row.put("role", role.name());
                    row.put("invitedBy", actorUserId);
                    row.put("at", Instant.now().toString());
                    appendAudit(root, tenant, actorUserId, "INVITE", 0L, normalized + ":" + role.name());
                    return tenant;
                }
            }
            JSONObject row = new JSONObject();
            row.put("tenantId", tenant.value());
            row.put("email", normalized);
            row.put("role", role.name());
            row.put("invitedBy", actorUserId);
            row.put("at", Instant.now().toString());
            invites.put(row);
            appendAudit(root, tenant, actorUserId, "INVITE", 0L, normalized + ":" + role.name());
            return tenant;
        });
    }

    public List<PendingInvite> pendingInvites(TenantId tenant) throws Exception {
        JSONObject root = read();
        JSONArray invites = root.optJSONArray("invites");
        List<PendingInvite> out = new ArrayList<>();
        if (invites == null || tenant == null) {
            return List.of();
        }
        for (int i = 0; i < invites.length(); i++) {
            JSONObject row = invites.getJSONObject(i);
            if (tenant.value().equals(row.optString("tenantId"))) {
                out.add(new PendingInvite(
                        row.optString("email"),
                        WorkspaceRole.valueOf(row.optString("role", WorkspaceRole.MEMBER.name())),
                        row.optLong("invitedBy"),
                        row.optString("at")));
            }
        }
        return List.copyOf(out);
    }

    public int consumePendingInvites(String email, long userId) throws Exception {
        if (userId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        String normalized = normalizeEmail(email);
        return mutate(root -> {
            JSONArray invites = ensureArray(root, "invites");
            int consumed = 0;
            for (int i = invites.length() - 1; i >= 0; i--) {
                JSONObject row = invites.getJSONObject(i);
                if (!normalized.equals(row.optString("email"))) {
                    continue;
                }
                TenantId tenant = TenantId.parse(row.getString("tenantId"));
                WorkspaceRole role = WorkspaceRole.valueOf(row.optString("role", WorkspaceRole.MEMBER.name()));
                if (role == WorkspaceRole.OWNER) {
                    role = WorkspaceRole.MEMBER;
                }
                if (membershipRow(root, tenant, userId) == null) {
                    addMembership(root, tenant, userId, role);
                    appendAudit(root, tenant, row.optLong("invitedBy"), "ADD_MEMBER", userId, role.name());
                }
                invites.remove(i);
                consumed++;
            }
            return consumed;
        });
    }

    public Path file() {
        return file;
    }

    public Path storeRoot() {
        return storeRoot;
    }

    private JSONObject read() throws Exception {
        if (!Files.isRegularFile(file)) {
            JSONObject empty = new JSONObject();
            empty.put("version", 1);
            empty.put("tenants", new JSONObject());
            empty.put("memberships", new JSONArray());
            empty.put("audit", new JSONArray());
            empty.put("services", new JSONArray());
            empty.put("invites", new JSONArray());
            return empty;
        }
        return new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    private interface Mutator<T> {
        T runWith(JSONObject root) throws Exception;
    }

    private <T> T mutate(Mutator<T> mutator) throws Exception {
        Files.createDirectories(file.getParent());
        return PublicationLock.call(file.resolveSibling("directory.lock"), () -> {
            JSONObject root = read();
            T result = mutator.runWith(root);
            Files.writeString(file, root.toString(2), StandardCharsets.UTF_8);
            return result;
        });
    }

    private static TenantId ownerTenant(JSONObject root, long userId) {
        JSONArray memberships = root.getJSONArray("memberships");
        for (int i = 0; i < memberships.length(); i++) {
            JSONObject row = memberships.getJSONObject(i);
            if (row.optLong("userId") == userId
                    && WorkspaceRole.OWNER.name().equals(row.optString("role"))) {
                return TenantId.parse(row.getString("tenantId"));
            }
        }
        return null;
    }

    private static TenantId tenantBySlug(JSONObject root, String slug) {
        JSONObject tenants = root.getJSONObject("tenants");
        Iterator<String> keys = tenants.keys();
        while (keys.hasNext()) {
            String id = keys.next();
            JSONObject row = tenants.optJSONObject(id);
            if (row != null && slug.equals(row.optString("displaySlug"))) {
                return TenantId.parse(id);
            }
        }
        return null;
    }

    private static void putTenant(
            JSONObject root, TenantId tenant, String kind, String slug, String alias
    ) {
        JSONObject row = new JSONObject();
        row.put("kind", kind);
        row.put("displaySlug", slug == null ? "" : slug);
        row.put("bootstrapAlias", alias == null ? "" : alias);
        root.getJSONObject("tenants").put(tenant.value(), row);
    }

    private static void addMembership(JSONObject root, TenantId tenant, long userId, WorkspaceRole role) {
        JSONObject row = new JSONObject();
        row.put("tenantId", tenant.value());
        row.put("userId", userId);
        row.put("role", role.name());
        root.getJSONArray("memberships").put(row);
    }

    private static void requireActorAdministers(JSONObject root, TenantId tenant, long actorUserId) {
        if (actorUserId <= 0) {
            return;
        }
        JSONObject row = membershipRow(root, tenant, actorUserId);
        if (row == null || !WorkspaceRole.OWNER.name().equals(row.optString("role"))) {
            throw new SecurityException("user " + actorUserId + " cannot administer " + tenant);
        }
    }

    private static JSONObject membershipRow(JSONObject root, TenantId tenant, long userId) {
        JSONArray memberships = root.getJSONArray("memberships");
        for (int i = 0; i < memberships.length(); i++) {
            JSONObject row = memberships.getJSONObject(i);
            if (tenant.value().equals(row.optString("tenantId")) && row.optLong("userId") == userId) {
                return row;
            }
        }
        return null;
    }

    private static JSONObject serviceRow(JSONObject root, TenantId tenant, String serviceId) {
        JSONArray services = ensureArray(root, "services");
        for (int i = 0; i < services.length(); i++) {
            JSONObject row = services.getJSONObject(i);
            if (tenant.value().equals(row.optString("tenantId")) && serviceId.equals(row.optString("id"))) {
                return row;
            }
        }
        return null;
    }

    private static JSONArray ensureArray(JSONObject root, String key) {
        JSONArray existing = root.optJSONArray(key);
        if (existing != null) {
            return existing;
        }
        JSONArray created = new JSONArray();
        root.put(key, created);
        return created;
    }

    private static void appendAudit(
            JSONObject root, TenantId tenant, long actorUserId, String action, long targetUserId, String detail
    ) {
        JSONArray audit = ensureArray(root, "audit");
        JSONObject row = new JSONObject();
        row.put("at", Instant.now().toString());
        row.put("tenantId", tenant.value());
        row.put("actorUserId", actorUserId);
        row.put("action", action);
        row.put("targetUserId", targetUserId);
        row.put("detail", detail == null ? "" : detail);
        audit.put(row);
    }

    private static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("sha256 unavailable", e);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSlug(String displaySlug) {
        if (displaySlug == null || displaySlug.isBlank()) {
            throw new IllegalArgumentException("display slug is required");
        }
        String slug = displaySlug.trim().toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9][a-z0-9-]{0,62}")) {
            throw new IllegalArgumentException("invalid display slug: " + displaySlug);
        }
        return slug;
    }
}
