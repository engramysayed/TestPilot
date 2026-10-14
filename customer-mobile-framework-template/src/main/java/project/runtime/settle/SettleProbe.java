package project.runtime.settle;

/**
 * Package-visible settle hook. Production polls page-source / screenshot;
 * unit tests short-circuit without blocking sleep / live device.
 */
public interface SettleProbe {
    /** Normalized hierarchy fingerprint (or raw page source). */
    String hierarchyFingerprint();

    /** Optional screenshot changed ratio vs pre-action baseline; null if unavailable. */
    Double screenshotChangedRatio();

    /** Advance virtual/real time by at most {@code maxMs}; return elapsed. Never blocking sleep in tests. */
    long awaitPoll(long maxMs);

    /** Short-circuit probe: settle completes immediately as NO_CHANGE unless hierarchy differs. */
    static SettleProbe immediate(String fingerprint) {
        return new SettleProbe() {
            @Override
            public String hierarchyFingerprint() {
                return fingerprint == null ? "" : fingerprint;
            }

            @Override
            public Double screenshotChangedRatio() {
                return null;
            }

            @Override
            public long awaitPoll(long maxMs) {
                return 0L;
            }
        };
    }
}
