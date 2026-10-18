package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.service.llm.LlmSettingsService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GenerateModelService {
    private final DeliveryPortalProperties props;
    private final LlmSettingsService llmSettings;

    public GenerateModelService(DeliveryPortalProperties props, LlmSettingsService llmSettings) {
        this.props = props;
        this.llmSettings = llmSettings;
    }

    public String resolve(String requested) {
        LlmSettingsService.GenerateModelList list = llmSettings.listForGenerate(false);
        String trimmed = requested == null ? "" : requested.trim();
        if (trimmed.isEmpty()) {
            return list.defaultModel();
        }
        for (String allowed : list.models()) {
            if (allowed.equals(trimmed)) {
                return trimmed;
            }
        }
        throw new IllegalArgumentException("Unsupported generate model: " + trimmed);
    }

    public List<String> allowedModels() {
        return llmSettings.listForGenerate(false).models();
    }

    public Map<String, Object> configPayload(boolean refresh) {
        LlmSettingsService.GenerateModelList list = llmSettings.listForGenerate(refresh);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("defaultModel", list.defaultModel());
        body.put("models", list.models());
        body.put("source", list.source());
        body.put("unreachable", list.unreachable());
        return body;
    }

    /** Test helper — unreachable Ollama base and throwaway store root. */
    static GenerateModelService forTests() {
        DeliveryPortalProperties p = new DeliveryPortalProperties();
        p.setStoreRoot(System.getProperty("java.io.tmpdir") + "/keel-gms-" + System.nanoTime());
        p.setLlmBaseUrl("http://127.0.0.1:9");
        p.setGenerateModel("gemma4:e2b");
        return new GenerateModelService(p, new LlmSettingsService(p));
    }
}
