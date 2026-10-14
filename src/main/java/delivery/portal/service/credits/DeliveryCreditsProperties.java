package delivery.portal.service.credits;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keel Credits config. Separate from project BudgetLedger / precision units.
 *
 * <pre>
 * delivery.credits.enabled=false
 * delivery.credits.cost.generate=10
 * delivery.credits.cost.execute=5
 * delivery.credits.cost.automate=25
 * delivery.credits.cost.hunt=15
 * </pre>
 */
@ConfigurationProperties(prefix = "delivery.credits")
public class DeliveryCreditsProperties {
    /** When false, grants work but job start is not gated. */
    private boolean enabled = false;
    private final Cost cost = new Cost();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Cost getCost() { return cost; }

    public static class Cost {
        private long generate = 10L;
        private long execute = 5L;
        private long automate = 25L;
        private long hunt = 15L;

        public long getGenerate() { return generate; }
        public void setGenerate(long generate) { this.generate = Math.max(0, generate); }
        public long getExecute() { return execute; }
        public void setExecute(long execute) { this.execute = Math.max(0, execute); }
        public long getAutomate() { return automate; }
        public void setAutomate(long automate) { this.automate = Math.max(0, automate); }
        public long getHunt() { return hunt; }
        public void setHunt(long hunt) { this.hunt = Math.max(0, hunt); }
    }
}
