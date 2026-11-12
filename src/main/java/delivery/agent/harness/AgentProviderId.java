package delivery.agent.harness;

/** Agent runtime provider identifier (harness selects via {@link AgentTaskRouter}). */
public enum AgentProviderId {
    /** Phase 1 tests and local dry-runs — no external SDK. */
    FAKE,
    /** Reserved for Phase 2 Cursor SDK adapter — not wired in Phase 1. */
    CURSOR
}
