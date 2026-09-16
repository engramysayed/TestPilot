package delivery.store;

/**
 * Project deletion phases: tombstone first, wait for workers, then purge.
 * Partial purge failure stays retryable and never resurrects records.
 */
public final class DeletionLifecycle {
    public enum Phase { LIVE, TOMBSTONED, PURGE_FAILED, PURGED }

    public record State(Phase phase, String lastError) {
        public boolean mayAdmit() {
            return phase == Phase.LIVE;
        }

        public boolean mayPublish(String attemptId) {
            return phase == Phase.LIVE && attemptId != null && !attemptId.isBlank();
        }

        public boolean canPurge(boolean hasActiveLease) {
            if (hasActiveLease) {
                return false;
            }
            return phase == Phase.TOMBSTONED || phase == Phase.PURGE_FAILED;
        }

        public State tombstone() {
            return new State(Phase.TOMBSTONED, "");
        }

        public State acknowledgeIdle() {
            return phase == Phase.LIVE ? this : new State(Phase.TOMBSTONED, lastError);
        }

        public State purgeFailed(String error) {
            return new State(Phase.PURGE_FAILED, error == null ? "purge failed" : error);
        }

        public State purge() {
            return new State(Phase.PURGED, "");
        }
    }

    private DeletionLifecycle() {
    }

    public static State live() {
        return new State(Phase.LIVE, "");
    }
}
