package delivery.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.portal.AgentHarnessProperties;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.roles.RolesService;
import delivery.portal.security.CurrentUserService;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Install-wide admin control for the Cursor model used by the agent harness sidecars.
 * Persists under {@code delivery-store/agent-harness.json} and pushes to runtime on save.
 */
@RestController
@RequestMapping("/api/admin/agent-harness")
public class AdminAgentHarnessController {
    static final String FILE_NAME = "agent-harness.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    static final List<String> KNOWN_MODELS = List.of(
            "auto", "claude-opus-5", "claude-sonnet-5", "claude-haiku-5", "gpt-5", "gpt-5-mini");

    private final AgentHarnessProperties properties;
    private final CurrentUserService currentUser;
    private final RolesService roles;
    private final Path settingsFile;

    public AdminAgentHarnessController(
            AgentHarnessProperties properties,
            DeliveryPortalProperties portalProps,
            CurrentUserService currentUser,
            RolesService roles) {
        this.properties = properties;
        this.currentUser = currentUser;
        this.roles = roles;
        String root = portalProps.getStoreRoot() == null || portalProps.getStoreRoot().isBlank()
                ? "./delivery-store"
                : portalProps.getStoreRoot().trim();
        this.settingsFile = Path.of(root).resolve(FILE_NAME).toAbsolutePath().normalize();
    }

    @PostConstruct
    void loadPersisted() {
        try {
            if (Files.isRegularFile(settingsFile)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = JSON.readValue(settingsFile.toFile(), Map.class);
                Object model = raw.get("cursorModel");
                if (model instanceof String s && !s.isBlank()) {
                    properties.setCursorModel(s);
                }
            }
        } catch (Exception ignored) {
            // Keep defaults from application.properties on parse failure.
        }
    }

    @GetMapping({"", "/"})
    public Map<String, Object> get() {
        requireOwner();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cursorModel", properties.getCursorModel());
        out.put("knownModels", KNOWN_MODELS);
        return out;
    }

    @PutMapping({"", "/"})
    public ResponseEntity<?> put(@RequestBody(required = false) PutBody body) {
        requireOwner();
        String requested = body == null || body.cursorModel == null ? null : body.cursorModel.trim();
        if (requested == null || requested.isBlank()) {
            requested = "auto";
        }
        if (!KNOWN_MODELS.contains(requested)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "BAD_MODEL");
            err.put("allowed", KNOWN_MODELS);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
        properties.setCursorModel(requested);
        try {
            Files.createDirectories(settingsFile.getParent());
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("cursorModel", requested);
            Files.writeString(settingsFile, JSON.writeValueAsString(raw), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "PERSIST_FAILED");
            err.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
        return ResponseEntity.ok(get());
    }

    public static final class PutBody {
        public String cursorModel;
    }

    private void requireOwner() {
        roles.requireHeadAdmin(currentUser.requireUser());
    }
}
