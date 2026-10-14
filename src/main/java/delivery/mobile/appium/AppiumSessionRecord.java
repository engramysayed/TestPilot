package delivery.mobile.appium;

import java.time.Instant;

/**
 * Session create/quit bookkeeping only (no Appium Java-client / AndroidDriver on portal).
 */
public final class AppiumSessionRecord {
    public enum State { CREATING, ACTIVE, QUITTING, QUIT, FAILED }

    private final String sessionId;
    private final String leaseId;
    private final long fenceToken;
    private final String processRef;
    private final int serverPort;
    private final Instant createdAt;
    private final String deviceSerial;
    private volatile State state;
    private volatile Instant quitAt;

    public AppiumSessionRecord(String sessionId,
                               String leaseId,
                               long fenceToken,
                               String processRef,
                               int serverPort,
                               Instant createdAt,
                               State state) {
        this(sessionId, leaseId, fenceToken, processRef, serverPort, createdAt, state, null);
    }

    public AppiumSessionRecord(String sessionId,
                               String leaseId,
                               long fenceToken,
                               String processRef,
                               int serverPort,
                               Instant createdAt,
                               State state,
                               String deviceSerial) {
        this.sessionId = sessionId;
        this.leaseId = leaseId;
        this.fenceToken = fenceToken;
        this.processRef = processRef;
        this.serverPort = serverPort;
        this.createdAt = createdAt;
        this.state = state == null ? State.ACTIVE : state;
        this.deviceSerial = deviceSerial;
    }

    public String getSessionId() { return sessionId; }
    public String getLeaseId() { return leaseId; }
    public long getFenceToken() { return fenceToken; }
    public String getProcessRef() { return processRef; }
    public int getServerPort() { return serverPort; }
    public Instant getCreatedAt() { return createdAt; }
    public State getState() { return state; }
    public Instant getQuitAt() { return quitAt; }
    /** Leased ADB serial used as {@code appium:udid} (may be null on legacy records). */
    public String getDeviceSerial() { return deviceSerial; }

    void markQuit(Instant at) {
        this.state = State.QUIT;
        this.quitAt = at;
    }

    void markFailed() {
        this.state = State.FAILED;
    }

    void markQuitting() {
        this.state = State.QUITTING;
    }
}
