package delivery.portal.config;

import delivery.knowledge.KnowledgePackReader;
import delivery.knowledge.NoOpKnowledgePackReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * KnowledgePackReader bean. Swap {@link NoOpKnowledgePackReader} for an HTTP
 * implementation when Backend GET knowledge-packs APIs are live.
 */
@Configuration
public class KnowledgePackConfig {
    @Bean
    public KnowledgePackReader knowledgePackReader() {
        return new NoOpKnowledgePackReader();
    }
}