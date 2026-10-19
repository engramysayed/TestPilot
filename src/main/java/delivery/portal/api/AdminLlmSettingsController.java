package delivery.portal.api;

import delivery.portal.roles.RolesService;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.llm.LlmSettingsService;
import delivery.portal.service.llm.LlmSettingsService.OllamaUnreachableException;
import delivery.portal.service.llm.LlmSettingsService.UpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Install-wide OWNER LLM / heal API keys. Same gate as AdminCredits ({@code requireHeadAdmin}). */
@RestController
@RequestMapping("/api/admin/llm-settings")
public class AdminLlmSettingsController {
    private final LlmSettingsService settings;
    private final CurrentUserService currentUser;
    private final RolesService roles;

    public AdminLlmSettingsController(
            LlmSettingsService settings, CurrentUserService currentUser, RolesService roles) {
        this.settings = settings;
        this.currentUser = currentUser;
        this.roles = roles;
    }

    @GetMapping({"", "/"})
    public Map<String, Object> get() {
        requireOwner();
        return settings.maskedView();
    }

    @PutMapping({"", "/"})
    public Map<String, Object> put(@RequestBody(required = false) PutBody body) {
        requireOwner();
        UpdateRequest req = body == null
                ? new UpdateRequest(null, null, null, null, null, null, false, false, false)
                : new UpdateRequest(
                        body.cursorApiKey,
                        body.agentrouterApiKey,
                        body.ollamaApiKey,
                        body.ollamaBaseUrl,
                        body.ollamaChatModel,
                        body.ollamaVisionModel,
                        Boolean.TRUE.equals(body.clearCursorApiKey),
                        Boolean.TRUE.equals(body.clearAgentrouterApiKey),
                        Boolean.TRUE.equals(body.clearOllamaApiKey),
                        body.reviewCasesPerBatch,
                        body.browser,
                        aliasesFrom(body.authoringVerbAliases));
        return settings.update(req);
    }

    @GetMapping("/ollama/models")
    public ResponseEntity<?> ollamaModels(
            @RequestParam(value = "baseUrl", required = false) String baseUrl) {
        requireOwner();
        try {
            List<String> models = settings.listOllamaModels(baseUrl);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("models", models);
            return ResponseEntity.ok(out);
        } catch (OllamaUnreachableException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "OLLAMA_UNREACHABLE");
            err.put("message", e.getMessage() == null ? "Ollama unreachable" : e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(err);
        }
    }

    private void requireOwner() {
        roles.requireHeadAdmin(currentUser.requireUser());
    }

    /** JSON body for PUT — omit or blank secret fields leave the stored secret unchanged. */
    public static class PutBody {
        public String cursorApiKey;
        public String agentrouterApiKey;
        public String ollamaApiKey;
        public String ollamaBaseUrl;
        public String ollamaChatModel;
        public String ollamaVisionModel;
        public Boolean clearCursorApiKey;
        public Boolean clearAgentrouterApiKey;
        public Boolean clearOllamaApiKey;
        public Integer reviewCasesPerBatch;
        public String browser;
        public List<Map<String, String>> authoringVerbAliases;
    }

    private static List<delivery.authoring.AuthoringVerbAliases.Alias> aliasesFrom(
            List<Map<String, String>> raw) {
        if (raw == null) {
            return null;
        }
        return delivery.authoring.AuthoringVerbAliases.fromRaw(raw);
    }
}
