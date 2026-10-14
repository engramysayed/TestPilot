package delivery.portal.roles;

public final class SystemRoleNames {
    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";

    private SystemRoleNames() {
    }

    public static boolean isBuiltIn(String name) {
        return OWNER.equalsIgnoreCase(name) || ADMIN.equalsIgnoreCase(name) || MEMBER.equalsIgnoreCase(name);
    }
}
