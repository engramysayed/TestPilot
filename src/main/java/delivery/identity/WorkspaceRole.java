package delivery.identity;

/**
 * Minimal workspace membership for P2-01. Personal workspaces map 1:1 to the
 * owning user. Dedicated installations use the same roles on {@code ws_install_*}.
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER
}
