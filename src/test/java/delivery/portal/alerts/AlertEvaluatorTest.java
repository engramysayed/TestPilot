package delivery.portal.alerts;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** B12: clock-injected AL-1 dead-man tests (alert, recovery, de-dup, disabled, grace, revoked/muted, idle, fan-out). */
public class AlertEvaluatorTest {
    static final Instant T0 = Instant.parse("2026-10-13T12:00:00Z");

    static final class MutableClock extends Clock {
        Instant now = T0;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void plusMinutes(long m) { now = now.plus(Duration.ofMinutes(m)); }
    }

    static final class Rec extends StubAlertChannel {
        Rec(String id, boolean on, Alert.Severity min) { super(id, on, min); }
    }

    MutableClock clock;
    List<EngineHeartbeats.Engine> engines;
    Rec telegram;
    Rec email;

    @org.testng.annotations.BeforeMethod
    public void fresh() {
        clock = new MutableClock();
        engines = new ArrayList<>();
        telegram = new Rec("telegram", true, Alert.Severity.WARNING);
        email = new Rec("email", true, Alert.Severity.CRITICAL);
    }

    AlertEvaluator evaluator(boolean enabled, Set<String> muted) {
        return new AlertEvaluator(() -> List.copyOf(engines), List.of(telegram, email), clock,
                new AlertEvaluator.Settings(enabled, Duration.ofMinutes(5), Duration.ofMinutes(60), muted));
    }

    void beat(String id) {
        engines.removeIf(e -> e.id().equals(id));
        engines.add(new EngineHeartbeats.Engine(id, "engine-" + id, T0, clock.instant(), false));
    }

    @Test
    public void missingForNPlusOneMinutesAlertsOnceThenRecoversOnce() throws Exception {
        AlertEvaluator ev = evaluator(true, Set.of());
        beat("r1");
        clock.plusMinutes(5);
        Assert.assertEquals(ev.evaluate(), List.of(), "exactly N minutes is not missing yet");
        clock.plusMinutes(1); // N+1
        List<Alert> raised = ev.evaluate();
        Assert.assertEquals(raised.size(), 1);
        Assert.assertEquals(raised.get(0).ruleId(), AlertEvaluator.ENGINE_HEARTBEAT_MISSING);
        Assert.assertEquals(raised.get(0).kind(), Alert.Kind.RAISED);
        Assert.assertEquals(raised.get(0).severity(), Alert.Severity.CRITICAL);
        Assert.assertEquals(raised.get(0).subject(), "engine:r1");

        for (int i = 0; i < 10; i++) { // de-dup over repeated evaluations inside the mute window
            clock.plusMinutes(1);
            Assert.assertEquals(ev.evaluate(), List.of());
        }
        beat("r1");
        List<Alert> resolved = ev.evaluate();
        Assert.assertEquals(resolved.size(), 1);
        Assert.assertEquals(resolved.get(0).kind(), Alert.Kind.RESOLVED);
        Assert.assertEquals(ev.evaluate(), List.of(), "one recovery only");

        Assert.assertEquals(telegram.sent().size(), 2, "fan-out to every enabled channel");
        Assert.assertEquals(email.sent().size(), 2, "critical passes email's min-severity");
        Assert.assertEquals(telegram.lastSuccess(), clock.instant());
    }

    @Test
    public void repeatsOncePerMuteWindowWhileStillFiring() throws Exception {
        AlertEvaluator ev = evaluator(true, Set.of());
        beat("r1");
        clock.plusMinutes(6);
        Assert.assertEquals(ev.evaluate().size(), 1);
        clock.plusMinutes(59);
        Assert.assertEquals(ev.evaluate(), List.of());
        clock.plusMinutes(1);
        List<Alert> repeat = ev.evaluate();
        Assert.assertEquals(repeat.size(), 1);
        Assert.assertEquals(repeat.get(0).kind(), Alert.Kind.REPEAT);
        clock.plusMinutes(1);
        Assert.assertEquals(ev.evaluate(), List.of());
    }

    @Test
    public void disabledIsSilent() throws Exception {
        AlertEvaluator ev = evaluator(false, Set.of());
        beat("r1");
        clock.plusMinutes(120);
        Assert.assertEquals(ev.evaluate(), List.of());
        ev.tick();
        Assert.assertTrue(telegram.sent().isEmpty() && email.sent().isEmpty());
    }

    @Test
    public void startupGraceIdleRevokedAndMutedEngines() throws Exception {
        engines.add(new EngineHeartbeats.Engine("old", "", T0.minus(Duration.ofHours(3)), T0.minus(Duration.ofHours(2)), false));
        AlertEvaluator ev = evaluator(true, Set.of("drain"));
        clock.plusMinutes(2);
        Assert.assertEquals(ev.evaluate(), List.of(), "startup grace: one interval after portal start");
        clock.plusMinutes(4);
        Assert.assertEquals(ev.evaluate().size(), 1, "after grace a long-silent engine alerts");

        engines.clear();
        Assert.assertEquals(ev.evaluate(), List.of(), "no enrolled engine: idle, and no recovery spam");
        engines.add(new EngineHeartbeats.Engine("rev", "", T0, T0, true));
        engines.add(new EngineHeartbeats.Engine("drain", "", T0, T0, false));
        clock.plusMinutes(30);
        Assert.assertEquals(ev.evaluate(), List.of(), "revoked and operator-muted engines are suppressed");
    }

    @Test
    public void neverSeenEngineCountsFromEnrolment() throws Exception {
        AlertEvaluator ev = evaluator(true, Set.of());
        engines.add(new EngineHeartbeats.Engine("new", "fresh", T0, null, false));
        clock.plusMinutes(5);
        Assert.assertEquals(ev.evaluate(), List.of());
        clock.plusMinutes(1);
        Assert.assertEquals(ev.evaluate().size(), 1);
    }

    @Test
    public void failingChannelDoesNotBlockOthers() throws Exception {
        AlertChannel broken = new AlertChannel() {
            public String id() { return "broken"; }
            public boolean enabled() { return true; }
            public Alert.Severity minSeverity() { return Alert.Severity.WARNING; }
            public void send(Alert a) { throw new IllegalStateException("down"); }
        };
        AlertEvaluator ev = new AlertEvaluator(() -> List.copyOf(engines), List.of(broken, telegram), clock,
                new AlertEvaluator.Settings(true, Duration.ofMinutes(5), Duration.ofMinutes(60), Set.of()));
        beat("r1");
        clock.plusMinutes(6);
        Assert.assertEquals(ev.evaluate().size(), 1);
        Assert.assertEquals(telegram.sent().size(), 1);
    }
}
