package delivery.job.port;

import java.nio.file.Path;

/** Engine-side settings a worker needs (B05). Built from DeliveryPortalProperties in-process, from engine config later. */
public record EngineSettings(String llmBaseUrl, String llmModel, Path workDir, Path storeRoot, Path templateRoot) {
}
