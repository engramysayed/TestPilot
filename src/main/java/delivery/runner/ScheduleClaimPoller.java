package delivery.runner;

/**
 * Phase 3 AE: optional schedule claim tick for {@link EngineAgent}.
 * Failures are swallowed so job claim loop stays healthy when Backend CRUD is mid-flight.
 */
public final class ScheduleClaimPoller {
    private final ScheduleClaimClient client;
    private final int withinSeconds;
    private final boolean enabled;

    public ScheduleClaimPoller(ScheduleClaimClient client, int withinSeconds, boolean enabled) {
        this.client = client;
        this.withinSeconds = withinSeconds <= 0 ? 60 : withinSeconds;
        this.enabled = enabled;
    }

    public static ScheduleClaimPoller fromEnv(EngineConfig config) {
        boolean on = Boolean.parseBoolean(System.getenv().getOrDefault("KEEL_SCHEDULE_CLAIM", "true"));
        on = Boolean.parseBoolean(System.getProperty("keel.engine.schedule-claim", Boolean.toString(on)));
        int within = 60;
        try {
            within = Integer.parseInt(System.getProperty("keel.engine.schedule-within-seconds",
                    System.getenv().getOrDefault("KEEL_SCHEDULE_WITHIN_SECONDS", "60")));
        } catch (NumberFormatException ignored) {
            within = 60;
        }
        return new ScheduleClaimPoller(new ScheduleClaimClient(config.portalUrl(), config.token()), within, on);
    }

    /** @return starts claimed this tick, or 0 when disabled / endpoint absent */
    public int tick() {
        if (!enabled || client == null) {
            return 0;
        }
        try {
            return client.claimAndStartDue(withinSeconds);
        } catch (Exception e) {
            System.err.println("SCHEDULE_CLAIM: " + e.getMessage());
            return 0;
        }
    }
}
