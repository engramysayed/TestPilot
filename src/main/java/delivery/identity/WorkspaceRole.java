package delivery.identity;

/**
 * Product role contract (not an automatic consequence of tenant isolation):
 * MEMBER is read-only; OWNER and ADMIN may operate (mutate, execute, export);
 * only OWNER may delete the project.
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER
}
