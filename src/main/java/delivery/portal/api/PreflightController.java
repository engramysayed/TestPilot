package delivery.portal.api;

import delivery.heal.ApiKeySource;
import delivery.packager.AllureReportBuilder;
import delivery.agent.harness.AgentHarnessProperties;
import delivery.portal.service.llm.LlmSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-flight health checks for the four run surfaces (Generate / Automate / Bug Hunt / Execute).
 * All checks fail-open — a check that throws just reports {@code ok=false} with the message, never
 * 500s the endpoint.
 */
@RestController
@RequestMapping("/api/preflight")
public class PreflightController {

    private final LlmSettingsService llmSettings;
    private final AgentHarnessProperties agentProps;

    public PreflightController(LlmSettingsService llmSettings, AgentHarnessProperties agentProps) {
        this.llmSettings = llmSettings;
        this.agentProps = agentProps;
    }

    @GetMapping
    public Map<String, Object> check() {
        List<Check> checks = new ArrayList<>();

        // Ollama reachable + chat model available (one call covers both)
        List<String> models = null;
        try {
            models = llmSettings.listOllamaModels(null);
            checks.add(new Check("Ollama reachable", true, "found " + models.size() + " model(s)"));
        } catch (Exception e) {
            checks.add(new Check("Ollama reachable", false, firstLine(e.getMessage())));
        }

        Map<String, Object> view = llmSettings.maskedView();
        String chat = str(view.get("ollamaChatModel"));
        if (chat.isBlank()) {
            checks.add(new Check("Ollama chat model configured", false,
                    "no chat model saved — open LLM Settings"));
        } else if (models == null) {
            checks.add(new Check("Ollama chat model available", false,
                    "selected '" + chat + "' but Ollama unreachable"));
        } else if (models.stream().anyMatch(m -> m.equalsIgnoreCase(chat))) {
            checks.add(new Check("Ollama chat model available", true, chat));
        } else {
            checks.add(new Check("Ollama chat model available", false,
                    "'" + chat + "' not in Ollama's model list — pull it or change selection"));
        }

        String vision = str(view.get("ollamaVisionModel"));
        if (!vision.isBlank() && models != null) {
            if (models.stream().anyMatch(m -> m.equalsIgnoreCase(vision))) {
                checks.add(new Check("Ollama vision model available", true, vision));
            } else {
                checks.add(new Check("Ollama vision model available", false,
                        "'" + vision + "' not in Ollama's model list"));
            }
        }

        // Cursor API key present (system property wins per ApiKeySource since recent fix)
        boolean cursorKey = ApiKeySource.isPresent(ApiKeySource.CURSOR_API_KEY);
        if (cursorKey) {
            checks.add(new Check("Cursor API key configured", true, "key loaded"));
        } else {
            checks.add(new Check("Cursor API key configured", false,
                    "CURSOR_API_KEY missing — set in portal LLM Settings"));
        }
        // Cursor agent enabled in harness properties
        checks.add(new Check("Cursor agent enabled",
                agentProps.isCursorEnabled(),
                agentProps.isCursorEnabled()
                        ? "delivery.agent-harness.cursor-enabled=true"
                        : "disabled — framework-review will be skipped"));

        // Allure autoreport + mvn on PATH (needed when autoreport is on)
        boolean allureOn = AllureReportBuilder.isEnabled();
        checks.add(new Check("Allure autoreport enabled", allureOn,
                allureOn ? "enabled (default)" : "disabled via keel.framework.allure.autoreport=false"));
        if (allureOn) {
            String mvn = AllureReportBuilder.findMvn();
            checks.add(new Check("Maven on PATH", mvn != null,
                    mvn != null ? mvn : "mvn not found on PATH — autoreport will skip"));
        }

        boolean allOk = checks.stream().allMatch(Check::ok);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allOk", allOk);
        out.put("checks", checks);
        return out;
    }

    public record Check(String name, boolean ok, String detail) {
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String firstLine(String s) {
        if (s == null) return "unknown error";
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl) : s;
    }
}
