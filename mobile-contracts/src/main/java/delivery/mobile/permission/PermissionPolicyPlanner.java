package delivery.mobile.permission;

import java.util.List;
import java.util.Map;

/**
 * Resolves case permission overrides; refuses broad grant-all and unsupported specials (M3-05).
 */
public final class PermissionPolicyPlanner {
    private PermissionPolicyPlanner() {
    }

    public static PermissionPlan plan(PermissionPolicy policy) {
        PermissionPolicy p = policy == null ? PermissionPolicy.empty() : policy;
        List<PermissionSpec> bad = p.unsupportedSpecials();
        if (!bad.isEmpty()) {
            return PermissionPlan.reject(
                    PermissionPolicy.PERMISSION_UNSUPPORTED,
                    "unsupported special permission: " + bad.get(0).name());
        }
        Map<String, PermissionGrant> grants = p.effectiveGrants();
        // Refuse implicit grant-all: empty named list is OK (UNCHANGED); a sentinel is not accepted.
        if (grants.containsKey("*") || grants.containsKey("ALL") || grants.containsKey("GRANT_ALL")) {
            return PermissionPlan.reject(
                    "PERMISSION_BROAD_GRANT_REFUSED",
                    "automatic broad grants are not allowed; name each permission");
        }
        return PermissionPlan.ok(grants);
    }
}
