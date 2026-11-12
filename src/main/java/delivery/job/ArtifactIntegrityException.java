package delivery.job;

/** Publish aborted because packaged artifact bytes failed integrity checks (not advisory validation). */
public final class ArtifactIntegrityException extends RuntimeException {
    public ArtifactIntegrityException(String detail) {
        super("ARTIFACT_INTEGRITY: " + (detail == null ? "check failed" : detail));
    }
}
