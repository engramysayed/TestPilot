package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Component
public class PipelinePoller {

    private final DeliveryPortalProperties props;
    private final PipelineService pipelineService;

    public PipelinePoller(DeliveryPortalProperties props, PipelineService pipelineService) {
        this.props = props;
        this.pipelineService = pipelineService;
    }

    @Scheduled(fixedDelayString = "${delivery.pipeline.poll-ms:2000}")
    public void pollRunningPipelines() {
        Path dir = Path.of(props.getStoreRoot(), "pipelines");
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(this::tickQuietly);
        } catch (Exception ignored) {
            // best-effort background poll
        }
    }

    private void tickQuietly(Path file) {
        try {
            String pipelineId = file.getFileName().toString().replace(".json", "");
            pipelineService.tick(pipelineId);
        } catch (Exception ignored) {
            // stale or mid-write file
        }
    }
}
