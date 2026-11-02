package delivery.portal.api;

import delivery.portal.roles.RolesService;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.AdminPromptsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owner admin prompts inventory / edit. Same gate as {@link AdminLlmSettingsController}. */
@RestController
@RequestMapping("/api/admin/prompts")
public class AdminPromptsController {
    private final AdminPromptsService prompts;
    private final CurrentUserService currentUser;
    private final RolesService roles;

    public AdminPromptsController(
            AdminPromptsService prompts, CurrentUserService currentUser, RolesService roles) {
        this.prompts = prompts;
        this.currentUser = currentUser;
        this.roles = roles;
    }

    @GetMapping({"", "/"})
    public Map<String, Object> list() throws Exception {
        requireOwner();
        return prompts.listPrompts();
    }

    @GetMapping("/**")
    public ResponseEntity<?> getOrDiff(HttpServletRequest request) throws Exception {
        requireOwner();
        String rest = restPath(request);
        if (rest == null || rest.isBlank()) {
            return ResponseEntity.ok(prompts.listPrompts());
        }
        boolean diff = false;
        String id = rest;
        if (rest.endsWith("/diff")) {
            diff = true;
            id = rest.substring(0, rest.length() - "/diff".length());
            while (id.endsWith("/")) {
                id = id.substring(0, id.length() - 1);
            }
        }
        try {
            if (diff) {
                return ResponseEntity.ok(prompts.diffPrompt(id));
            }
            return ResponseEntity.ok(prompts.getPrompt(id));
        } catch (AdminPromptsService.NotFoundException e) {
            return error(HttpStatus.NOT_FOUND, "PROMPT_NOT_FOUND", e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "BAD_PROMPT_ID", e.getMessage());
        }
    }

    @PutMapping("/**")
    public ResponseEntity<?> put(HttpServletRequest request, @RequestBody(required = false) PutBody body)
            throws Exception {
        requireOwner();
        String id = restPath(request);
        if (id == null || id.isBlank() || id.endsWith("/diff")) {
            return error(HttpStatus.BAD_REQUEST, "BAD_PROMPT_ID", "prompt id is required");
        }
        try {
            String text = body == null ? null : body.text;
            return ResponseEntity.ok(prompts.savePrompt(id, text));
        } catch (AdminPromptsService.UnsetDirException e) {
            return error(HttpStatus.BAD_REQUEST, "PROMPTS_DIR_UNSET", e.getMessage());
        } catch (AdminPromptsService.BadRequestException | IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
        } catch (AdminPromptsService.NotFoundException e) {
            return error(HttpStatus.NOT_FOUND, "PROMPT_NOT_FOUND", e.getMessage());
        }
    }

    private void requireOwner() {
        roles.requireHeadAdmin(currentUser.requireUser());
    }

    private static String restPath(HttpServletRequest request) {
        String full = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (full == null) {
            return "";
        }
        String prefix = "/api/admin/prompts";
        String rest = full;
        if (full.startsWith(prefix)) {
            rest = full.substring(prefix.length());
        }
        while (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        return rest;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message == null ? "" : message);
        return ResponseEntity.status(status).body(body);
    }

    public static class PutBody {
        public String text;
    }
}
