package delivery.hunt;

import delivery.authoring.LocalLlmClient;
import delivery.prompts.PromptCatalog;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public final class OllamaHuntPlanner implements HuntPlanner {
    private static final String SYSTEM = PromptCatalog.get("hunt/ollama-system").trim() + "\n"
            + delivery.authoring.LocatorPolicy.huntPlannerRules();

    private final LocalLlmClient client;

    public OllamaHuntPlanner(String baseUrl, String model) {
        this.client = new LocalLlmClient(baseUrl, model);
    }

    public OllamaHuntPlanner(LocalLlmClient client) {
        this.client = client;
    }

    @Override
    public HuntPlannerDecision plan(Context ctx) throws Exception {
        String user = buildUserPrompt(ctx);
        byte[] png = null;
        if (ctx.screenshotPath() != null && Files.isRegularFile(ctx.screenshotPath())) {
            png = Files.readAllBytes(ctx.screenshotPath());
        }
        String raw = png == null || png.length == 0
                ? client.completeJson(SYSTEM, user)
                : client.completeJson(SYSTEM, user, png);
        try {
            return HuntPlannerDecision.parse(raw);
        } catch (Exception first) {
            // One repair pass — small models often emit NULs / truncated JSON.
            String repairUser = user + "\n\n## Repair\nYour previous reply was invalid JSON ("
                    + first.getMessage()
                    + "). Return ONLY one valid JSON object with keys decision, rationale, actions, bugs, scenarios.";
            String repaired = png == null || png.length == 0
                    ? client.completeJson(SYSTEM, repairUser)
                    : client.completeJson(SYSTEM, repairUser, png);
            try {
                return HuntPlannerDecision.parse(repaired);
            } catch (Exception second) {
                return HuntPlannerDecision.parse("""
                        {"decision":"continue","rationale":"Planner JSON invalid after repair — skipping actions this cycle.",
                        "actions":[],"bugs":[],"scenarios":[]}
                        """.trim());
            }
        }
    }

    @Override
    public String triageBugs(String triageUserPrompt) throws Exception {
        String system = """
                You triage Bug Hunter findings. Return ONLY JSON with keys keep, drop, merge.
                Do not invent bugs. When unsure, KEEP. Drop noise, duplicates, flakes, false login-URL oracles.
                """;
        return client.completeJson(system, triageUserPrompt == null ? "" : triageUserPrompt);
    }

    static String buildUserPrompt(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Caps\n");
        sb.append("iterationIndex=").append(ctx.iterationIndex())
                .append(" iterationCeiling=").append(ctx.iterationCeiling())
                .append(" cycleIndex=").append(ctx.cycleIndex())
                .append(" cycleCeiling=").append(ctx.cycleCeiling())
                .append(" actionCapPerCycle=").append(ctx.actionCapPerCycle())
                .append(" scenarioCap=").append(ctx.scenarioCap())
                .append(" scenariosEmitted=").append(ctx.scenariosEmitted())
                .append(" remainingScenarios=")
                .append(Math.max(0, ctx.scenarioCap() - ctx.scenariosEmitted()))
                .append('\n');

        sb.append("\n## Strategy\n");
        String strategy = ctx.strategyHint();
        if (strategy == null || strategy.isBlank()) {
            sb.append("(none)\n");
        } else {
            sb.append(strategy).append('\n');
        }

        sb.append("\n## Iteration\n");
        sb.append("iteration ").append(ctx.iterationIndex()).append('/').append(ctx.iterationCeiling()).append('\n');
        if (ctx.priorIterationResultsMd() != null && !ctx.priorIterationResultsMd().isBlank()) {
            sb.append("\n### Prior iteration results\n").append(ctx.priorIterationResultsMd()).append('\n');
        }
        if (ctx.iterationPlanMd() != null && !ctx.iterationPlanMd().isBlank()) {
            sb.append("\n### Current iteration test plan\n").append(ctx.iterationPlanMd()).append('\n');
        }

        sb.append("\n## Brief\n").append(ctx.briefMd() == null ? "" : ctx.briefMd()).append('\n');

        if (ctx.hasCredentials()) {
            sb.append("\n## Hunt credentials (tokens only — password not shown)\n");
            sb.append("- Username token: ${TARGET_USERNAME}");
            if (ctx.credentialUsername() != null && !ctx.credentialUsername().isBlank()) {
                sb.append(" (profile user present)");
            }
            sb.append('\n');
            sb.append("- Password token: ${TARGET_PASSWORD} (resolved at execute; value not shown)\n");
            if (ctx.hasOtp()) {
                sb.append("- OTP token: ${TARGET_OTP} (only when OTP screen appears)\n");
            }
            sb.append("Rules: server opened base URL only — YOU log in via UI actions using these tokens.\n");
            sb.append("Happy-path MUST use tokens; negatives use invalid_*/empty before happy-path when untested.\n");
        } else {
            sb.append("\n## Hunt credentials\n");
            sb.append("No project credential profile on this job — cannot complete real login; probe validation/negatives.\n");
        }

        if (ctx.loginFeature()) {
            sb.append("\n## Feature mode\nlogin-feature=true (staying on /login is in-scope; do not treat login URL as unexpected redirect)\n");
        }

        sb.append("\n## Steps journal (prior actions + results; no DOM/screenshots)\n");
        String journal = ctx.stepsJournalMd();
        if (journal == null || journal.isBlank()) {
            sb.append("(none yet — first cycle)\n");
        } else {
            sb.append(journal).append('\n');
        }

        sb.append("\n## Coverage\n");
        String coverage = ctx.coverageMd();
        if (coverage == null || coverage.isBlank()) {
            sb.append("(none)\n");
        } else {
            sb.append(coverage).append('\n');
        }

        sb.append("\n## Network failures (this cycle only)\n");
        List<Map<String, Object>> net = ctx.networkFailures();
        if (net == null || net.isEmpty()) {
            sb.append("(none)\n");
        } else {
            sb.append(new org.json.JSONArray(net).toString()).append('\n');
        }

        sb.append("\n## Page map\n");
        String pageMap = ctx.pageMapMd();
        if (pageMap == null || pageMap.isBlank()) {
            sb.append("(none)\n");
        } else {
            sb.append(pageMap).append('\n');
        }

        if (ctx.includeSlimDom()) {
            sb.append("\n## Slim DOM (current page only)\n");
            String dom = ctx.slimDom() == null ? "" : ctx.slimDom();
            sb.append(dom).append('\n');
        }

        String hooks = ctx.preferredHooksLine();
        if (hooks != null && !hooks.isBlank()) {
            sb.append("\n## Locator preference (RANK 1)\n");
            sb.append("This project configures preferred hook attribute(s): ").append(hooks).append('\n');
            sb.append("How to apply:\n");
            sb.append("1. Scan page-map Controls for locators that use these attribute(s).\n");
            sb.append("2. If a matching control exists for your intent, ALWAYS choose that locator ")
                    .append("over id / name / generic CSS twins of the same element.\n");
            sb.append("3. Copy the locator exactly from the page map — never invent attribute values.\n");
            sb.append("4. If no mapped control has these attributes, fall back to normal locator rules.\n");
        }

        sb.append("\n## Screenshot\n");
        sb.append("One PNG of the current page at cycle start is attached when the model supports vision.\n");
        return sb.toString();
    }

    static String systemPrompt() {
        return SYSTEM;
    }
}
