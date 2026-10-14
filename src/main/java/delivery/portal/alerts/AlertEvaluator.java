package delivery.portal.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B12, AL-1 in-portal engine dead-man check (SPLIT_DESIGN rev 10 §8). For each enrolled, non-revoked engine: no
 * heartbeat (claim poll, lease extension or v1 heartbeat) for more than {@code keel.alerts.engine-deadman.minutes}
 * (default 5) raises {@code ENGINE_HEARTBEAT_MISSING} (critical); the next heartbeat sends one RESOLVED.
 * De-duplicated per rule + engine, repeated at most once per {@code keel.alerts.mute-minutes} (default 60) while it
 * keeps firing. Suppressed while {@code keel.alerts.engine-deadman.enabled=false} (<b>default in 0a</b>), for engines
 * an operator lists in {@code keel.alerts.engine-deadman.muted-engines} (draining/stopped), and during the startup
 * grace (one interval after portal start). With no enrolled engine the rule is idle.
 */
@Component
public class AlertEvaluator {
    public static final String ENGINE_HEARTBEAT_MISSING = "ENGINE_HEARTBEAT_MISSING";
    private static final Logger LOG = LoggerFactory.getLogger(AlertEvaluator.class);

    public record Settings(boolean enabled, Duration deadman, Duration mute, Set<String> mutedEngines) {
        public static Settings from(Environment env) {
            long minutes = Long.parseLong(env.getProperty("keel.alerts.engine-deadman.minutes", "5").trim());
            if (minutes < 1) {
                throw new IllegalStateException("keel.alerts.engine-deadman.minutes must be >= 1");
            }
            Set<String> muted = new HashSet<>();
            Arrays.stream(env.getProperty("keel.alerts.engine-deadman.muted-engines", "").split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).forEach(muted::add);
            return new Settings(Boolean.parseBoolean(env.getProperty("keel.alerts.engine-deadman.enabled", "false").trim()),
                    Duration.ofMinutes(minutes),
                    Duration.ofMinutes(Long.parseLong(env.getProperty("keel.alerts.mute-minutes", "60").trim())), muted);
        }
    }

    private final EngineHeartbeats heartbeats;
    private final List<AlertChannel> channels;
    private final Clock clock;
    private final Settings settings;
    private final Instant startedAt;
    /** dedupKey -> last time we notified (RAISED or REPEAT). Present = currently firing. */
    private final Map<String, Instant> firing = new HashMap<>();

    @Autowired
    public AlertEvaluator(EngineHeartbeats heartbeats, List<AlertChannel> channels, Environment env) {
        this(heartbeats, channels, Clock.systemUTC(), Settings.from(env));
    }

    public AlertEvaluator(EngineHeartbeats heartbeats, List<AlertChannel> channels, Clock clock, Settings settings) {
        this.heartbeats = heartbeats;
        this.channels = List.copyOf(channels);
        this.clock = clock;
        this.settings = settings;
        this.startedAt = clock.instant();
    }

    public Settings settings() {
        return settings;
    }

    @Scheduled(fixedDelayString = "${keel.alerts.evaluate-ms:60000}", initialDelayString = "${keel.alerts.evaluate-ms:60000}")
    public void tick() {
        if (!settings.enabled()) {
            return;
        }
        try {
            evaluate();
        } catch (Exception e) {
            LOG.warn("alert evaluation failed: {}", e.toString());
        }
    }

    /** One evaluation cycle; returns the notifications produced (already fanned out to enabled channels). */
    public synchronized List<Alert> evaluate() throws Exception {
        List<Alert> out = new ArrayList<>();
        if (!settings.enabled()) {
            firing.clear();
            return out;
        }
        Instant now = clock.instant();
        boolean grace = now.isBefore(startedAt.plus(settings.deadman()));
        Set<String> seen = new HashSet<>();
        for (EngineHeartbeats.Engine e : heartbeats.engines()) {
            if (e.revoked() || settings.mutedEngines().contains(e.id())) {
                continue;
            }
            String subject = "engine:" + e.id();
            String key = ENGINE_HEARTBEAT_MISSING + "|" + subject;
            seen.add(key);
            Instant last = e.lastHeartbeat() != null ? e.lastHeartbeat() : e.enrolledAt();
            boolean missing = last == null || Duration.between(last, now).compareTo(settings.deadman()) > 0;
            Instant notified = firing.get(key);
            String name = e.label() == null || e.label().isBlank() ? e.id() : e.label() + " (" + e.id() + ")";
            if (missing && !grace) {
                long mins = last == null ? -1 : Duration.between(last, now).toMinutes();
                if (notified == null) {
                    out.add(new Alert(ENGINE_HEARTBEAT_MISSING, subject, Alert.Severity.CRITICAL, Alert.Kind.RAISED,
                            "Engine " + name + ": no heartbeat for " + (mins < 0 ? "ever" : mins + " min")
                                    + " (limit " + settings.deadman().toMinutes() + " min)", now));
                    firing.put(key, now);
                } else if (!now.isBefore(notified.plus(settings.mute()))) {
                    out.add(new Alert(ENGINE_HEARTBEAT_MISSING, subject, Alert.Severity.CRITICAL, Alert.Kind.REPEAT,
                            "Engine " + name + ": still no heartbeat (" + mins + " min)", now));
                    firing.put(key, now);
                }
            } else if (!missing && notified != null) {
                out.add(new Alert(ENGINE_HEARTBEAT_MISSING, subject, Alert.Severity.CRITICAL, Alert.Kind.RESOLVED,
                        "Engine " + name + ": heartbeat is back", now));
                firing.remove(key);
            }
        }
        firing.keySet().removeIf(k -> !seen.contains(k)); // revoked / muted / removed engines stop firing silently
        for (Alert a : out) {
            fanOut(a);
        }
        return out;
    }

    private void fanOut(Alert a) {
        for (AlertChannel c : channels) {
            if (!c.enabled() || a.severity().compareTo(c.minSeverity()) < 0) {
                continue;
            }
            try {
                c.send(a);
            } catch (Exception ex) {
                LOG.warn("alert channel {} failed: {}", c.id(), ex.toString());
            }
        }
    }
}
