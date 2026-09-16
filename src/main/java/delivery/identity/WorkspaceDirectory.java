package delivery.identity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

/**
 * File-backed workspace registry. Tenant ids are opaque; user ids and display
 * slugs are membership/metadata and can change without renaming storage.
 */
public final class WorkspaceDirectory {
    public static final String FILE_NAME = "directory.json";

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
        if (tenant == null || userId <= 0 || role == null) {
            throw new IllegalArgumentException("tenant, user and role are required");
        }
        mutate(root -> {
            JSONArray memberships = root.getJSONArray("memberships");
            for (int i = 0; i < memberships.length(); i++) {
                JSONObject row = memberships.getJSONObject(i);
                if (tenant.value().equals(row.optString("tenantId")) && row.optLong("userId") == userId) {
                    row.put("role", role.name());
                    return tenant;
                }
            }
            addMembership(root, tenant, userId, role);
            return tenant;
        });
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
