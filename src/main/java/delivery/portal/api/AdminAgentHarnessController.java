package delivery.portal.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.heal.ApiKeySource;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Install-wide admin control for the Cursor model used by the agent harness sidecars.
 * Persists under {@code delivery-store/agent-harness.json} and pushes to runtime on save.
 */
@RestController
@RequestMapping("/api/admin/agent-harness")
public class AdminAgentHarnessController {
    static final String FILE_NAME = "agent-harness.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Static fallback when CURSOR_API_KEY is missing or the SDK call fails. */
    static final List<String> KNOWN_MODELS_FALLBACK = List.of(
            "auto", "claude-opus-5", "claude-sonnet-5", "claude-haiku-5", "gpt-5", "gpt-5-mini");
    private static final long MODEL_CACHE_TTL_MS = 10 * 60 * 1000L; // 10 minutes
    private final AtomicReference<CachedModels> modelCache = new AtomicReference<>(null);

    private record CachedModels(long expiresAt, List<Map<String, Object>> rows, List<String> ids) {
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
        List<Map<String, Object>> models = fetchModels().rows();
        List<String> allowed = fetchModels().ids();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cursorModel", properties.getCursorModel());
        out.put("models", models);
        out.put("knownModels", allowed);
        return out;
    }

    @GetMapping("/models/refresh")
    public Map<String, Object> refreshModels() {
        requireOwner();
        modelCache.set(null);
        return get();
    }

    private CachedModels fetchModels() {
        CachedModels cached = modelCache.get();
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) {
            return cached;
        }
        CachedModels fresh = fetchFromSdk(now);
        modelCache.set(fresh);
        return fresh;
    }

    private CachedModels fetchFromSdk(long now) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        rows.add(Map.of("id", "auto", "displayName", "Auto — cheapest capable (recommended)", "description", ""));
        ids.add("auto");
        String apiKey = ApiKeySource.resolve(ApiKeySource.CURSOR_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            // No key — static fallback so the picker still works offline.
            for (String id : KNOWN_MODELS_FALLBACK) {
                if (!"auto".equals(id)) {
                    rows.add(Map.of("id", id, "displayName", id, "description", ""));
                    ids.add(id);
                }
            }
            return new CachedModels(now + MODEL_CACHE_TTL_MS, rows, ids);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "tools/cursor-agent/list-models.mjs");
            pb.environment().put("CURSOR_API_KEY", apiKey);
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    stdout.append(line);
                }
            }
            boolean finished = proc.waitFor(30, TimeUnit.SECONDS);
            if (!finished || proc.exitValue() != 0) {
                proc.destroyForcibly();
                return fallbackCache(now, rows, ids);
            }
            List<Map<String, Object>> parsed = JSON.readValue(stdout.toString(), new TypeReference<>() {});
            for (Map<String, Object> m : parsed) {
                Object id = m.get("id");
                if (id == null) continue;
                String sid = String.valueOf(id);
                if ("auto".equals(sid)) continue; // already added
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", sid);
                row.put("displayName", String.valueOf(m.getOrDefault("displayName", sid)));
                row.put("description", String.valueOf(m.getOrDefault("description", "")));
                rows.add(row);
                ids.add(sid);
            }
            return new CachedModels(now + MODEL_CACHE_TTL_MS, rows, ids);
        } catch (Exception e) {
            return fallbackCache(now, rows, ids);
        }
    }

    private CachedModels fallbackCache(long now, List<Map<String, Object>> rows, List<String> ids) {
        for (String id : KNOWN_MODELS_FALLBACK) {
            if (!ids.contains(id)) {
                rows.add(Map.of("id", id, "displayName", id, "description", ""));
                ids.add(id);
            }
        }
        // Short TTL on fallback so a later refresh picks up a freshly configured key.
        return new CachedModels(now + 60_000L, rows, ids);
    }

    @PutMapping({"", "/"})
    public ResponseEntity<?> put(@RequestBody(required = false) PutBody body) {
        requireOwner();
        String requested = body == null || body.cursorModel == null ? null : body.cursorModel.trim();
        if (requested == null || requested.isBlank()) {
            requested = "auto";
        }
        List<String> allowed = fetchModels().ids();
        if (!allowed.contains(requested)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "BAD_MODEL");
            err.put("allowed", allowed);
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
