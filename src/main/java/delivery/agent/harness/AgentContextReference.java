package delivery.agent.harness;

/** Pointer into project storage with an explicit purpose for the context package. */
public record AgentContextReference(
        String source,
        String purpose,
        String relativePath
) {
    public AgentContextReference {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose is required");
        }
        relativePath = relativePath == null ? "" : relativePath.trim();
    }
}
