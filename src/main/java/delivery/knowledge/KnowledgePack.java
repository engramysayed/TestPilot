package delivery.knowledge;

/**
 * Interim KnowledgePack DTO (Maestro/Backend contract 2026-11-02).
 * Markdown is source of truth; store remains project disk via Backend HTTP later.
 */
public record KnowledgePack(
        String packId,
        String projectId,
        String title,
        KnowledgePackSource source,
        int batchSize,
        int revision,
        String markdown,
        String markdownPath,
        String createdAt,
        String updatedAt
) {
    public KnowledgePack {
        packId = packId == null ? "" : packId.trim();
        projectId = projectId == null ? "" : projectId.trim();
        title = title == null ? "" : title.trim();
        source = source == null ? KnowledgePackSource.LIBRARY : source;
        markdown = markdown == null ? "" : markdown;
        markdownPath = markdownPath == null ? "" : markdownPath.trim();
        createdAt = createdAt == null ? "" : createdAt.trim();
        updatedAt = updatedAt == null ? "" : updatedAt.trim();
        if (batchSize < 1) {
            batchSize = 30;
        }
        if (revision < 1) {
            revision = 1;
        }
    }
}