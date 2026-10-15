package delivery.portal.record;

import delivery.portal.service.GeneratedWorkbookService;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One live recording per person. Chrome stays open until they stop or the portal shuts down. */
@Service
public class BrowserRecordingService {
    private static final Logger log = LogManager.getLogger(BrowserRecordingService.class);
    private static final long MAX_RECORDING_MS = 20L * 60L * 1000L;
    private static final long KEEP_STOPPED_MS = 30L * 60L * 1000L;

    private final GeneratedWorkbookService workbooks;
    private final RecordingOpener opener;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface RecordingOpener {
        RecordingBrowser open(String approvedOrigin, String url);
    }

    public record View(
            String sessionId,
            String status,
            String url,
            List<String> steps,
            String message,
            int locatedCount,
            int checkCount
    ) {
    }

    @Autowired
    public BrowserRecordingService(GeneratedWorkbookService workbooks) {
        this(workbooks, ChromeRecordingBrowser::launch);
    }

    BrowserRecordingService(GeneratedWorkbookService workbooks, RecordingOpener opener) {
        this.workbooks = workbooks;
        this.opener = opener;
    }

    public synchronized View start(String ownerKey, String projectId, String approvedOrigin, String requestedUrl, boolean useProjectSite) {
        String url = RecordingUrls.resolve(approvedOrigin, requestedUrl, useProjectSite);
        sweep(System.currentTimeMillis());
        RecordingBrowser browser = opener.open(approvedOrigin, url);
        stopOwned(ownerKey);
        Session session = new Session(newId(), ownerKey, projectId, approvedOrigin, url, browser);
        sessions.put(session.id, session);
        try {
            synchronized (session) {
                pull(session);
            }
        } catch (RuntimeException e) {
            forget(session);
            throw e;
        }
        log.info("Recording {} started for project {}", session.id, projectId);
        return view(session);
    }

    public View poll(String ownerKey, String projectId, String sessionId) {
        Session session = require(ownerKey, projectId, sessionId);
        synchronized (session) {
            if ("recording".equals(session.status) && System.currentTimeMillis() - session.startedAt > MAX_RECORDING_MS) {
                pullQuietly(session);
                closeBrowser(session);
                session.status = "stopped";
                session.message = "Recording stopped after 20 minutes. You can still save these steps.";
                return view(session);
            }
            if ("recording".equals(session.status)) {
                if (!session.browser.alive()) {
                    session.status = "stopped";
                    session.message = "The browser closed. You can still save the steps you have.";
                } else {
                    pullQuietly(session);
                }
            }
            return view(session);
        }
    }

    public View stop(String ownerKey, String projectId, String sessionId) {
        Session session = require(ownerKey, projectId, sessionId);
        synchronized (session) {
            if ("recording".equals(session.status)) {
                pullQuietly(session);
                closeBrowser(session);
                session.status = "stopped";
                if (session.message == null || session.message.isBlank()) {
                    session.message = "Recording stopped. Check the steps, then save.";
                }
            }
            return view(session);
        }
    }

    public Map<String, Object> save(
            String ownerKey,
            String projectId,
            String sessionId,
            String name,
            String expected,
            boolean signIn,
            String baseUrl
    ) throws Exception {
        Session session = require(ownerKey, projectId, sessionId);
        List<String> steps;
        synchronized (session) {
            if ("recording".equals(session.status)) {
                pullQuietly(session);
                closeBrowser(session);
                session.status = "stopped";
            }
            steps = session.steps;
        }
        List<RecordingLog.Capture> captures = session.log.captures();
        String text = RecordingLog.numbered(steps);
        if (text.isBlank()) {
            throw new IllegalArgumentException("Record at least one step before saving.");
        }
        String title = name == null ? "" : name.trim().replaceAll("[\\r\\n]+", " ");
        if (title.isBlank()) {
            throw new IllegalArgumentException("Give the test a short name first.");
        }
        if (title.length() > 180) {
            title = title.substring(0, 180).trim();
        }
        String expectedText = expected == null ? "" : expected.trim();
        if (expectedText.isBlank()) {
            expectedText = "The recorded steps finish on the page the visitor reached.";
        } else if (expectedText.length() > 1000) {
            expectedText = expectedText.substring(0, 1000).trim();
        }
        String preconditions = signIn ? "Sign in with a project login account before these steps." : "";
        Map<String, Object> saved = new LinkedHashMap<>(workbooks.appendRecordedCase(
                projectId, title, text, expectedText, preconditions, baseUrl));
        saved.put("steps", steps);
        saved.put("locatedCount", RecordingLog.locatedCount(captures));
        saved.put("checkCount", RecordingLog.checkCount(captures));
        saved.put("captures", captures);
        forget(session);
        return saved;
    }

    @PreDestroy
    public void shutdown() {
        for (Session session : new ArrayList<>(sessions.values())) {
            forget(session);
        }
    }

    private void pull(Session session) {
        if (!session.browser.alive()) {
            return;
        }
        String json = session.browser.readEvents();
        if (json == null || json.isBlank()) {
            session.browser.ensureInstalled();
            json = session.browser.readEvents();
        }
        RecordingEvents.Snapshot snapshot = RecordingEvents.parse(json);
        if (snapshot == null) {
            if (session.steps.isEmpty() && session.url != null && !session.url.isBlank()) {
                session.steps = session.log.accept("start", List.of(), session.url);
            }
            return;
        }
        String location = session.browser.location();
        if (!RecordingUrls.allowed(session.approvedOrigin, location)) {
            location = session.lastAllowedUrl;
        } else {
            session.lastAllowedUrl = location;
        }
        List<RecordingLog.Event> events = new ArrayList<>();
        for (RecordingLog.Event event : snapshot.events()) {
            if (event != null && "open".equalsIgnoreCase(event.kind())
                    && !RecordingUrls.allowed(session.approvedOrigin, event.label())) {
                continue;
            }
            events.add(event);
        }
        session.steps = session.log.accept(snapshot.generation(), events, location);
        if (location != null && !location.isBlank()) {
            session.url = location;
        }
    }

    private void pullQuietly(Session session) {
        try {
            pull(session);
        } catch (RuntimeException e) {
            log.warn("Recording {} lost the browser: {}", session.id, e.toString());
            session.status = "stopped";
            session.message = "The browser closed. You can still save the steps you have.";
            closeBrowser(session);
        }
    }

    private void stopOwned(String ownerKey) {
        for (Session session : sessions.values()) {
            if (ownerKey.equals(session.ownerKey) && "recording".equals(session.status)) {
                synchronized (session) {
                    pullQuietly(session);
                    closeBrowser(session);
                    session.status = "stopped";
                    session.message = "Recording stopped because a new one started.";
                }
            }
        }
    }

    private void sweep(long now) {
        for (Session session : new ArrayList<>(sessions.values())) {
            if (now - session.startedAt > KEEP_STOPPED_MS) {
                forget(session);
            }
        }
    }

    private void forget(Session session) {
        sessions.remove(session.id);
        closeBrowser(session);
    }

    private void closeBrowser(Session session) {
        if (session.closed) {
            return;
        }
        session.closed = true;
        try {
            session.browser.close();
        } catch (RuntimeException e) {
            log.warn("Recording {} close failed: {}", session.id, e.toString());
        }
    }

    private Session require(String ownerKey, String projectId, String sessionId) {
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null || !session.ownerKey.equals(ownerKey) || !session.projectId.equals(projectId)) {
            throw new IllegalArgumentException("That recording is no longer open. Start it again.");
        }
        return session;
    }

    private static View view(Session session) {
        List<RecordingLog.Capture> captures = session.log.captures();
        return new View(session.id, session.status, session.url, session.steps,
                session.message == null ? "" : session.message,
                RecordingLog.locatedCount(captures),
                RecordingLog.checkCount(captures));
    }

    private static String newId() {
        return "rec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static final class Session {
        final String id;
        final String ownerKey;
        final String projectId;
        final String approvedOrigin;
        final RecordingBrowser browser;
        final RecordingLog log = new RecordingLog();
        final long startedAt = System.currentTimeMillis();
        String url;
        String lastAllowedUrl;
        String status = "recording";
        String message = "";
        List<String> steps = List.of();
        boolean closed;

        Session(String id, String ownerKey, String projectId, String approvedOrigin, String url, RecordingBrowser browser) {
            this.id = id;
            this.ownerKey = ownerKey;
            this.projectId = projectId;
            this.approvedOrigin = approvedOrigin == null ? "" : approvedOrigin;
            this.url = url;
            this.lastAllowedUrl = url;
            this.browser = browser;
        }
    }

}
