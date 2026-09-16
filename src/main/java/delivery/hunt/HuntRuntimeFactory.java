package delivery.hunt;

import delivery.heal.CursorHealClient;
import delivery.job.ConversionJobRequest;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;

import java.nio.file.Path;

/** Builds the planner + login request for a HUNT job. */
public final class HuntRuntimeFactory {
    private HuntRuntimeFactory() {
    }

    public static HuntPlanner plannerFor(HuntRequest request, DeliveryPortalProperties props) {
        String mode = request.getPlanner() == null ? "ollama" : request.getPlanner();
        if ("cursor".equalsIgnoreCase(mode)) {
            CursorHealClient cursor = new CursorHealClient();
            if (!cursor.isEnabled()) {
                throw new IllegalStateException(
                        "Cursor planner selected but delivery.cursor-heal is disabled or CURSOR_API_KEY is missing");
            }
            return new CursorHuntPlanner(cursor);
        }
        String base = props.getLlmBaseUrl();
        String model = props.getLlmModel();
        if (base == null || base.isBlank() || model == null || model.isBlank()) {
            throw new IllegalStateException("Ollama planner requires delivery.llm base URL and model");
        }
        return new OllamaHuntPlanner(base, model);
    }

    public static ConversionJobRequest loginRequest(JobRecord job, DeliveryPortalProperties props) {
        return new ConversionJobRequest(
                job.getProjectId(),
                job.getExcelPath(),
                job.getBaseUrl(),
                job.getUsername(),
                job.getPassword(),
                Path.of(props.getWorkDir()),
                Path.of(props.getStoreRoot()),
                Path.of(props.getTemplateRoot()),
                "HUNT",
                props.getLlmBaseUrl(),
                props.getLlmModel(),
                false,
                false,
                delivery.authoring.AuthoringEngine.KEEL,
                delivery.authoring.PrecisionJobConfig.DEFAULTS,
                delivery.identity.TenantResolver.require(
                        Path.of(props.getStoreRoot()), job.getTenantId(), job.getOwnerUserId()),
                job.getJobId(),
                delivery.job.TenantScope.HOSTED
        );
    }
}
