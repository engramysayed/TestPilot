package delivery.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * Loads KnowledgePacks for Hunt (and later Generate). Production plugs an HTTP
 * client against GET /api/projects/{projectId}/knowledge-packs[/{packId}].
 * Do not invent a second filesystem pack store in AE.
 */
public interface KnowledgePackReader {
    /** List metadata for a project (markdown may be omitted). */
    List<KnowledgePack> list(String projectId);

    /** Full pack including markdown body. */
    Optional<KnowledgePack> get(String projectId, String packId);

    /**
     * Resolve packs by id in request order. Missing ids are skipped (no hard fail).
     * Empty or null ids returns empty list.
     */
    default List<KnowledgePack> getByIds(String projectId, List<String> packIds) {
        if (packIds == null || packIds.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<KnowledgePack> out = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (String id : packIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String trimmed = id.trim();
            if (!seen.add(trimmed)) {
                continue;
            }
            get(projectId, trimmed).ifPresent(out::add);
        }
        return List.copyOf(out);
    }
}