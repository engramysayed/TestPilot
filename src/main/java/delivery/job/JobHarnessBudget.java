package delivery.job;

import java.time.Duration;
import java.time.Instant;

/** Lease-aligned harness wait budget (mirrors Convert publication validation slack). */
public final class JobHarnessBudget {
    private static final Duration DEFAULT = Duration.ofMinutes(15);
    private static final Duration MIN_REMAINING = Duration.ofSeconds(30);

    private JobHarnessBudget() {
    }

    public static Duration remainingLeaseBudget(Instant leaseUntil) {
        if (leaseUntil == null) {
            return DEFAULT;
        }
        Duration remaining = Duration.between(Instant.now(), leaseUntil);
        if (remaining.isNegative() || remaining.isZero()) {
            return MIN_REMAINING;
        }
        return remaining.minusSeconds(30);
    }
}