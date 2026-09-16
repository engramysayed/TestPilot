package delivery.identity;

public record WorkspaceMembership(TenantId tenant, long userId, WorkspaceRole role) {
    public WorkspaceMembership {
        if (tenant == null) {
            throw new IllegalArgumentException("tenant is required");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        if (role == null) {
            role = WorkspaceRole.MEMBER;
        }
    }

    public static WorkspaceMembership personalOwner(long userId) {
        return new WorkspaceMembership(TenantId.personal(userId), userId, WorkspaceRole.OWNER);
    }

    public boolean canRead() {
        return true;
    }

    public boolean canMutate() {
        return role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN;
    }

    public boolean belongsTo(TenantId other) {
        return tenant.equals(other);
    }
}
