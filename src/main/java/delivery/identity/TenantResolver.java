package delivery.identity;

import java.nio.file.Path;

/** Resolves a persisted opaque tenant, bootstrapping a personal workspace when needed. */
public final class TenantResolver {
    private TenantResolver() {
    }

    public static TenantId parseOrNull(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return TenantId.parse(stored);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static TenantId forJob(Path storeRoot, String storedTenantId, Long ownerUserId) {
        TenantId parsed = parseOrNull(storedTenantId);
        if (parsed != null) {
            return parsed;
        }
        if (storeRoot == null || ownerUserId == null || ownerUserId <= 0) {
            return null;
        }
        try {
            return WorkspaceDirectory.open(storeRoot).ensurePersonalWorkspace(ownerUserId);
        } catch (Exception e) {
            return null;
        }
    }
}
