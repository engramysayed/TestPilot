package delivery.mobile.appium;

/**
 * Loopback port exclusively owned by one lease/job. Always bound to {@code 127.0.0.1}.
 */
public final class OwnedPort {
    public static final String LOOPBACK = "127.0.0.1";

    private final int port;
    private final String ownerKey;
    private final String role;

    public OwnedPort(int port, String ownerKey, String role) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("invalid port: " + port);
        }
        this.port = port;
        this.ownerKey = ownerKey == null ? "" : ownerKey;
        this.role = role == null ? "server" : role;
    }

    public int getPort() { return port; }
    public String getOwnerKey() { return ownerKey; }
    public String getRole() { return role; }
    public String getBindAddress() { return LOOPBACK; }

    public String baseUrl() {
        return "http://" + LOOPBACK + ":" + port;
    }
}
