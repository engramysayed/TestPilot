package delivery.portal.api;

import delivery.vision.VisionGroundingConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** A6: DOM-first / vision policy surface for UI + agents. */
@RestController
@RequestMapping("/api/vision")
public class VisionPolicyController {

    @GetMapping("/policy")
    public ResponseEntity<Map<String, Object>> policy() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groundingEnabled", VisionGroundingConfig.enabled());
        body.put("assertionsEnabled", VisionGroundingConfig.assertionsEnabled());
        body.put("healHintsEnabled", VisionGroundingConfig.healHintsEnabled());
        body.put("domPostClickEnabled", VisionGroundingConfig.domPostClickEnabled());
        body.put("domPostClickStrict", VisionGroundingConfig.domPostClickStrict());
        body.put("groundProvider", VisionGroundingConfig.groundingProviderId());
        body.put("groundModel", VisionGroundingConfig.groundingModel());
        body.put("assertProvider", VisionGroundingConfig.assertProviderId());
        body.put("assertModel", VisionGroundingConfig.assertModel());
        body.put("policySummary", domFirstSummary());
        return ResponseEntity.ok(body);
    }

    private static String domFirstSummary() {
        if (!VisionGroundingConfig.assertionsEnabled()) {
            return "Vision assertions off — DOM-only.";
        }
        if (VisionGroundingConfig.domPostClickEnabled()) {
            return VisionGroundingConfig.domPostClickStrict()
                    ? "DOM-first (strict): vision only when DOM post-click is insufficient."
                    : "DOM-first: vision fallback after DOM attempts.";
        }
        return "Vision assertions enabled; DOM-first post-click disabled.";
    }
}
