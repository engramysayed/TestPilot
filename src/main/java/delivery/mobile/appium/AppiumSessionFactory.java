package delivery.mobile.appium;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AE-L6 session create/quit: W3C NewSession over raw HTTP against owned loopback Appium,
 * plus ConcurrentHashMap bookkeeping. Portal never imports Appium driver / java-client jars.
 */
public final class AppiumSessionFactory {
    private final Clock clock;
    private final AppiumSessionTransporter transporter;
    private final ConcurrentHashMap<String, AppiumSessionRecord> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> leaseToSession = new ConcurrentHashMap<>();

    public AppiumSessionFactory() {
        this(Clock.systemUTC(), new HttpAppiumSessionTransporter());
    }

    public AppiumSessionFactory(Clock clock) {
        this(clock, new HttpAppiumSessionTransporter());
    }

    /** Injectable transporter for offline unit tests (mock NewSession without network). */
    public AppiumSessionFactory(Clock clock, AppiumSessionTransporter transporter) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.transporter = Objects.requireNonNull(transporter, "transporter");
    }

    /**
     * POST W3C NewSession with {@code appium:udid}=deviceSerial; store the real Appium session id.
     * Blank/null serial → {@link AppiumErrorCode#INVALID_STATE}. Non-loopback / HTTP failure →
     * {@link AppiumErrorCode#APPIUM_UNAVAILABLE}.
     */
    public AppiumSessionRecord createSession(String leaseId,
                                             long fenceToken,
                                             OwnedAppiumProcess process,
                                             String deviceSerial) {
        if (leaseId == null || leaseId.isBlank()) {
            throw new AppiumLifecycleException(AppiumErrorCode.INVALID_STATE, "leaseId required");
        }
        if (process == null) {
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE, "owned process required");
        }
        String serial = AppiumW3cCapabilities.requireSerial(deviceSerial);
        String sessionId = transporter.createSession(process.getBaseUrl(), serial);
        if (sessionId == null || sessionId.isBlank()) {
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE,
                    "NewSession returned blank sessionId");
        }
        Instant now = clock.instant();
        AppiumSessionRecord rec = new AppiumSessionRecord(
                sessionId, leaseId, fenceToken, process.getProcessRef(),
                process.getServerPort(), now, AppiumSessionRecord.State.ACTIVE, serial);
        byId.put(sessionId, rec);
        leaseToSession.put(leaseId, sessionId);
        return rec;
    }

    /**
     * Quit: best-effort HTTP DELETE /session/{id}, then bookkeeping QUIT (idempotent if already QUIT).
     */
    public AppiumSessionRecord quitSession(String sessionId) {
        AppiumSessionRecord rec = byId.get(sessionId);
        if (rec == null) {
            throw new AppiumLifecycleException(AppiumErrorCode.INVALID_STATE, "session not found: " + sessionId);
        }
        if (rec.getState() == AppiumSessionRecord.State.QUIT) {
            return rec;
        }
        rec.markQuitting();
        // Best-effort HTTP delete against owned loopback derived from recorded server port.
        String baseUrl = "http://127.0.0.1:" + rec.getServerPort();
        try {
            transporter.deleteSession(baseUrl, sessionId);
        } catch (RuntimeException ignored) {
            // bookkeeping still marks QUIT
        }
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

    /** Visible for tests. */
    AppiumSessionTransporter transporter() {
        return transporter;
    }
}
