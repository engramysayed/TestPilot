package delivery.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * Production default until Backend HTTP KnowledgePack APIs are live-applied.
 * Returns empty so Hunt keeps legacy behavior when packs are requested but unavailable.
 */
public final class NoOpKnowledgePackReader implements KnowledgePackReader {
    @Override
    public List<KnowledgePack> list(String projectId) {
        return List.of();
    }

    @Override
    public Optional<KnowledgePack> get(String projectId, String packId) {
        return Optional.empty();
    }
}