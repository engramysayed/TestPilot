package delivery.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * Empty reader retained for tests / emergency fallback.
 * Production bean is {@link HttpKnowledgePackReader} (see {@code KnowledgePackConfig}).
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
