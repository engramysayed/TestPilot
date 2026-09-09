package delivery.hunt;

import delivery.authoring.LocalLlmClient;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public final class OllamaHuntPlanner implements HuntPlanner {
    private static final String SYSTEM = """
            You are Keel's Bug Hunter planner. Break the feature safely and invent edge-case scenarios.
            Return ONLY one JSON object with keys:
            decision ("continue"|"finish"), rationale, actions[], bugs[], scenarios[].
            actions allowlist: navigate{url}, click{locator|locatorStrategy+locatorValue}, type{locator,value},
            clear{locator}, wait{ms}, assert_visible{locator}, assert_text{text}.
            Emit at most actionCapPerCycle actions (see Caps). wait with no/blank ms defaults to 5000ms server-side.
            Use the page map as the primary DOM signal — only emit locators that appear in the page map
            (or in the slim DOM section when attached).
            Use the steps journal to remember what already ran — do not repeat failed clicks blindly.
            Respect scenarioCap remaining. Prefer finish when findings are enough.
            Do not narrate outside JSON.
            """ + "\n" + delivery.authoring.LocatorPolicy.huntPlannerRules();

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
        return HuntPlannerDecision.parse(raw);
    }

    static String buildUserPrompt(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Caps\n");
        sb.append("cycleIndex=").append(ctx.cycleIndex())
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

        sb.append("\n## Brief\n").append(ctx.briefMd() == null ? "" : ctx.briefMd()).append('\n');

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

        sb.append("\n## Locator preference\n");
        String hooks = ctx.preferredHooksLine();
        if (hooks == null || hooks.isBlank()) {
            sb.append("No project preferred-hook attributes configured — use default priority in system rules.\n");
        } else {
            sb.append("Project preferred-hook attributes (prefer controls using these): ")
                    .append(hooks).append('\n');
        }

        sb.append("\n## Screenshot\n");
        sb.append("One PNG of the current page at cycle start is attached when the model supports vision.\n");
        return sb.toString();
    }

    static String systemPrompt() {
        return SYSTEM;
    }
}
