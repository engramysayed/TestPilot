package delivery.mobile.permission;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionSpec(
        String name,
        boolean special,
        PermissionGrant grant
) {
    public PermissionSpec {
        if (name == null || name.isBlank()) {
            throw new PermissionPolicyException("PERMISSION_NAME", "permission name required");
        }
        name = name.trim();
        grant = grant == null ? PermissionGrant.UNCHANGED : grant;
    }
}
