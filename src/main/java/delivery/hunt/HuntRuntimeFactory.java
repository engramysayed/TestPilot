package delivery.hunt;

import delivery.heal.CursorHealClient;
import delivery.job.ConversionJobRequest;
import delivery.job.port.EngineSettings;
import delivery.job.port.JobContextPort;

/** Builds the planner + login request for a HUNT job. */
public final class HuntRuntimeFactory {
    private HuntRuntimeFactory() {
    }

    public static HuntPlanner plannerFor(HuntRequest request, EngineSettings props) {
        String mode = request.getPlanner() == null ? "ollama" : request.getPlanner();
        if ("cursor".equalsIgnoreCase(mode)) {
            CursorHealClient cursor = new CursorHealClient();
            if (!cursor.isEnabled()) {
                throw new IllegalStateException(
                        "Cursor planner selected but delivery.cursor-heal is disabled or CURSOR_API_KEY is missing");
            }
            return new CursorHuntPlanner(cursor);
        }
        String base = props.llmBaseUrl();
        String model = props.llmModel();
        if (base == null || base.isBlank() || model == null || model.isBlank()) {
            throw new IllegalStateException("Ollama planner requires delivery.llm base URL and model");
        }
        return new OllamaHuntPlanner(base, model);
    }

    public static ConversionJobRequest loginRequest(JobContextPort job) {
        EngineSettings props = job.settings();
        return new ConversionJobRequest(
                job.projectId(),
                job.excelPath(),
                job.baseUrl(),
                job.username(),
                job.password(),
                props.workDir(),
                props.storeRoot(),
                props.templateRoot(),
                "HUNT",
                props.llmBaseUrl(),
                props.llmModel(),
                false,
                false,
                delivery.authoring.AuthoringEngine.KEEL,
                delivery.authoring.PrecisionJobConfig.DEFAULTS,
                delivery.identity.TenantResolver.require(
                        props.storeRoot(), job.tenantId(), job.ownerUserId()),
                job.jobId(),
                delivery.job.TenantScope.HOSTED
        );
    }
}
