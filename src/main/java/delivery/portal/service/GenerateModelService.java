package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GenerateModelService {
    private final DeliveryPortalProperties props;

    public GenerateModelService(DeliveryPortalProperties props) {
        this.props = props;
    }

    public String resolve(String requested) {
        String trimmed = requested == null ? "" : requested.trim();
        if (trimmed.isEmpty()) {
            return props.getGenerateModel();
        }
        for (String allowed : allowedModels()) {
            if (allowed.equals(trimmed)) {
                return trimmed;
            }
        }
        throw new IllegalArgumentException("Unsupported generate model: " + trimmed);
    }

    public List<String> allowedModels() {
        List<String> configured = props.getGenerateModels();
        if (configured != null && !configured.isEmpty()) {
            return List.copyOf(configured);
        }
        return List.of(props.getGenerateModel());
    }

    public Map<String, Object> configPayload() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("defaultModel", props.getGenerateModel());
        body.put("models", allowedModels());
        return body;
    }
}
