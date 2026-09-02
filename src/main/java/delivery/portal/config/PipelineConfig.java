package delivery.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.JobStarter;
import delivery.portal.service.JobStatusLookup;
import delivery.portal.service.PipelineGenerator;
import delivery.portal.service.PipelineService;
import delivery.portal.service.TcGenerateService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class PipelineConfig {

    @Bean
    public PipelineGenerator pipelineGenerator(TcGenerateService generate) {
        // All-in-one always runs the second coverage pass (reviewPass=true) so generated
        // Excel is more complete before Automate / Execute start.
        return (projectId, ownerUserId, stories) ->
                generate.generate(projectId, ownerUserId, stories, true, null);
    }

    @Bean
    public PipelineService pipelineService(
            DeliveryPortalProperties props,
            GeneratedWorkbookService workbooks,
            JobStarter jobStarter,
            JobStatusLookup jobStatus,
            ObjectMapper objectMapper,
            PipelineGenerator pipelineGenerator
    ) {
        return new PipelineService(
                props,
                workbooks,
                jobStarter,
                jobStatus,
                objectMapper,
                pipelineGenerator,
                PipelineService.defaultGenerateExecutor()
        );
    }
}
