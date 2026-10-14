package delivery.mobile.appium;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M1-06 session create/quit bookkeeping. Portal never imports Appium driver classes;
 * AE/mobile-runner perform the real HTTP NewSession in M2 against {@link OwnedAppiumProcess#getBaseUrl()}.
 */
public final class AppiumSessionFactory {
    private final Clock clock;
    private final ConcurrentHashMap<String, AppiumSessionRecord> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> leaseToSession = new ConcurrentHashMap<>();

    public AppiumSessionFactory() {
        this(Clock.systemUTC());
    }

    public AppiumSessionFactory(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * Record a new session bound to the lease + owned process. Does not call Appium HTTP.
     */
    public AppiumSessionRecord createSession(String leaseId,
                                             long fenceToken,
                                             OwnedAppiumProcess process) {
        if (leaseId == null || leaseId.isBlank()) {
            throw new AppiumLifecycleException(AppiumErrorCode.INVALID_STATE, "leaseId required");
        }
        if (process == null) {
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE, "owned process required");
        }
        String sessionId = "sess_" + shortId();
        Instant now = clock.instant();
        AppiumSessionRecord rec = new AppiumSessionRecord(
                sessionId, leaseId, fenceToken, process.getProcessRef(),
                process.getServerPort(), now, AppiumSessionRecord.State.ACTIVE);
        byId.put(sessionId, rec);
        leaseToSession.put(leaseId, sessionId);
        return rec;
    }

    /** Quit bookkeeping for the session; idempotent if already QUIT. */
    public AppiumSessionRecord quitSession(String sessionId) {
        AppiumSessionRecord rec = byId.get(sessionId);
        if (rec == null) {
            throw new AppiumLifecycleException(AppiumErrorCode.INVALID_STATE, "session not found: " + sessionId);
        }
        if (rec.getState() == AppiumSessionRecord.State.QUIT) {
            return rec;
        }
        rec.markQuitting();
        rec.markQuit(clock.instant());
        return rec;
    }

    public Optional<AppiumSessionRecord> findByLeaseId(String leaseId) {
        String sid = leaseToSession.get(leaseId);
        if (sid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(sid));
    }

    public Optional<AppiumSessionRecord> find(String sessionId) {
        return Optional.ofNullable(byId.get(sessionId));
    }

    public Map<String, AppiumSessionRecord> snapshot() {
        return Map.copyOf(byId);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
