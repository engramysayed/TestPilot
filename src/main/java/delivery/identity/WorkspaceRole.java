package delivery.identity;

/**
 * Minimal workspace membership. Roles are bound to an opaque tenant id;
 * account numbers and installation slugs are not encoded in the tenant.
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER
}
