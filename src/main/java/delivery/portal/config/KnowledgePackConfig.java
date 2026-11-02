package delivery.portal.config;

import delivery.knowledge.HttpKnowledgePackReader;
import delivery.knowledge.KnowledgePackReader;
import delivery.portal.service.KnowledgePackService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * KnowledgePackReader bean. Production uses {@link HttpKnowledgePackReader} in local
 * (same-process) mode against {@link KnowledgePackService} — the service that backs
 * GET /api/projects/{projectId}/knowledge-packs[/{packId}]. No second FS pack store.
 */
@Configuration
public class KnowledgePackConfig {
    @Bean
    public KnowledgePackReader knowledgePackReader(KnowledgePackService knowledgePackService) {
        return HttpKnowledgePackReader.local(
                knowledgePackService::list,
                knowledgePackService::get
        );
    }
}
