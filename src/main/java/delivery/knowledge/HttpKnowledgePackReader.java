package delivery.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * Thin client stub for Backend GET /api/projects/{projectId}/knowledge-packs.
 * Until HTTP is wired, behaves like {@link NoOpKnowledgePackReader} (empty, no throw)
 * so Hunt never hard-fails when pack ids are present.
 *
 * <p>Backend plug-in: inject RestClient/WebClient base URL + auth and implement
 * {@link #list(String)} / {@link #get(String, String)} against the contract paths.
 */
public final class HttpKnowledgePackReader implements KnowledgePackReader {
    private final String apiBaseUrl;

    public HttpKnowledgePackReader(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl == null ? "" : apiBaseUrl.trim();
    }

    public String apiBaseUrl() {
        return apiBaseUrl;
    }

    @Override
    public List<KnowledgePack> list(String projectId) {
        // TODO(Backend): GET {apiBase}/api/projects/{projectId}/knowledge-packs
        return List.of();
    }

    @Override
    public Optional<KnowledgePack> get(String projectId, String packId) {
        // TODO(Backend): GET {apiBase}/api/projects/{projectId}/knowledge-packs/{packId}
        return Optional.empty();
    }
}