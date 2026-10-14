package delivery.mobile.runner.settle;

/** One SETTLE_AFTER row: hold-still vs after-change, with millisecond cap. */
public record SettleRule(SettleMode mode, long capMs) {
    public enum SettleMode {
        /** Two identical consecutive normalized hierarchies. */
        HELD_STILL,
        /** Wait for first hierarchy change, then held still. */
        AFTER_CHANGE_THEN_HELD,
        /** No change-wait (secret TYPE). */
        NONE
    }

    public static SettleRule heldStill(long capMs) {
        return new SettleRule(SettleMode.HELD_STILL, capMs);
    }

    public static SettleRule afterChange(long capMs) {
        return new SettleRule(SettleMode.AFTER_CHANGE_THEN_HELD, capMs);
    }

    public static SettleRule none() {
        return new SettleRule(SettleMode.NONE, 0L);
    }
}
