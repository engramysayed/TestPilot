package delivery.portal.credits;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cost table for Keel Credits (Final lock). Properties:
 * {@code keel.credits.cost.generate|execute|automate|hunt}
 * Defaults: generate 10, execute 5, automate 25, hunt 15.
 */
@ConfigurationProperties(prefix = "keel.credits")
public class KeelCreditsProperties {
    private Cost cost = new Cost();

    public Cost getCost() { return cost; }
    public void setCost(Cost cost) { this.cost = cost == null ? new Cost() : cost; }

    public long costFor(String jobKind) {
        if (jobKind == null) {
            return cost.getGenerate();
        }
        return switch (jobKind.trim().toUpperCase()) {
            case "EXECUTE" -> cost.getExecute();
            case "AUTOMATE" -> cost.getAutomate();
            case "HUNT" -> cost.getHunt();
            default -> cost.getGenerate();
        };
    }

    public static class Cost {
        private long generate = 10;
        private long execute = 5;
        private long automate = 25;
        private long hunt = 15;

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
