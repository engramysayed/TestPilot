package delivery.portal.api;

import delivery.portal.service.GenerateModelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/generate")
public class GenerateConfigController {

    private final GenerateModelService models;

    public GenerateConfigController(GenerateModelService models) {
        this.models = models;
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels() {
        return ResponseEntity.ok(models.configPayload());
    }
}
