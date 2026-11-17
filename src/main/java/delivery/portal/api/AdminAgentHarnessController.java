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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Install-wide admin control for the Cursor model used by the agent harness sidecars.
 * Persists under {@code delivery-store/agent-harness.json} and pushes to runtime on save.
 */
@RestController
@RequestMapping("/api/admin/agent-harness")
public class AdminAgentHarnessController {
    static final String FILE_NAME = "agent-harness.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * Curated list of Cursor cloud models. @cursor/sdk 1.0.27 does not expose a listCloudModels
     * endpoint, so this is static. If Cursor adds a new model, enter its id in the custom-id
     * field on Admin and the PUT endpoint accepts it as long as the pattern matches.
     */
    static final List<String> KNOWN_MODELS = List.of(
            "auto",
            "claude-opus-5", "claude-sonnet-5", "claude-haiku-5",
            "claude-opus-4.5", "claude-sonnet-4.5",
            "gpt-5", "gpt-5-mini", "gpt-5-codex",
            "gpt-4.1",
            "o3", "o4-mini",
            "gemini-2.5-pro", "gemini-2.5-flash",
            "grok-code-fast-1", "grok-5");
    /** User-supplied model ids must look like a valid Cursor identifier (no injection). */
    static final Pattern MODEL_ID = Pattern.compile("^[a-z][a-z0-9.\\-]{0,60}$");

    private record CachedModels(List<Map<String, Object>> rows, List<String> ids) {
    }

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
        CachedModels snap = models();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cursorModel", properties.getCursorModel());
        out.put("models", snap.rows());
        out.put("knownModels", snap.ids());
        return out;
    }

    /** Legacy no-op so the Refresh button keeps working on existing clients — list is now static. */
    @GetMapping("/models/refresh")
    public Map<String, Object> refreshModels() {
        return get();
    }

    private CachedModels models() {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (String id : KNOWN_MODELS) {
            String name = "auto".equals(id) ? "Auto — cheapest capable (recommended)" : id;
            rows.add(Map.of("id", id, "displayName", name, "description", ""));
            ids.add(id);
        }
        // Include whatever the admin currently has set, even if we don't know it yet (custom id).
        String current = properties.getCursorModel();
        if (current != null && !current.isBlank() && !ids.contains(current)) {
            rows.add(Map.of("id", current, "displayName", current + " (custom)", "description", ""));
            ids.add(current);
        }
        return new CachedModels(rows, ids);
    }

    @PutMapping({"", "/"})
    public ResponseEntity<?> put(@RequestBody(required = false) PutBody body) {
        requireOwner();
        String requested = body == null || body.cursorModel == null ? null : body.cursorModel.trim();
        if (requested == null || requested.isBlank()) {
            requested = "auto";
        }
        // Accept any Cursor-looking id (including models we haven't curated like grok-code-fast-1).
        // Rejecting unknown ids used to block grok etc.; now we trust the owner and let Cursor
        // reject at run time with a clear error if the id is wrong.
        if (!MODEL_ID.matcher(requested).matches()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "BAD_MODEL");
            err.put("message", "Model id must match " + MODEL_ID.pattern());
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
