package delivery.hunt;

import delivery.authoring.LocalLlmClient;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public final class OllamaHuntPlanner implements HuntPlanner {
    private static final String SYSTEM = """
            You are Keel's Bug Hunter planner. Break the feature safely and invent edge-case scenarios.
            Return ONLY one JSON object with keys:
            decision ("continue"|"finish_iteration"|"finish"), rationale, actions[], bugs[], scenarios[].
            decision rules:
            - continue = keep cycling within this iteration
            - finish_iteration = stop cycles for this iteration after executing actions; results feed next iteration
            - finish = stop entire hunt after executing actions
            actions allowlist: navigate{url}, back{}, forward{}, refresh{}, restart_browser{login?},
            execute_js{script},
            switch_frame{locator|index|name}, switch_default{},
            click{locator}, click_xy{x,y|value:"x,y"}, click_shadow{locator,value:innerCss},
            type{locator,value}, clear{locator}, drag_drop{locator,value:targetLocator},
            wait{ms}, assert_visible{locator}, assert_text{text}.
            IFRAME: if controls are inside iframe (see page map iframe entries), switch_frame BEFORE interact.
            SHADOW: use click_shadow with host locator + value=inner CSS inside shadowRoot.
            DRAG: drag_drop source locator + value=target locator (both from page map).
            COORDS: click_xy only when map locators fail — value "123,456" for viewport x,y.
            Never use fill/input — use type. Never invent locators.
            Copy locators exactly from page map Controls. Prefer structured form:
            {"type":"type","locatorStrategy":"css","locatorValue":"<exact value from page map>","value":"x"}.
            When ## Locator preference lists attributes, those locators are RANK 1 over twins.
            Aliases: navigate_back→back, reload→refresh, js/code→execute_js script field,
            fresh_session|reset_browser|restart→restart_browser.
            Current URL is always in the page map (## URL) — read it; use navigate/back/forward/refresh to move.
            Prefer UI logout when a Sign out control is on the map; otherwise use restart_browser
            for a clean session (opens project base URL only — no server auto-login).
            restart_browser{login:true} means YOU must log in again on the next cycles via type/click.
            Max 2 restart_browser per hunt — do not thrash.
            LOGIN: the server never auto-logs in. When credential tokens are present, YOU drive the
            full flow (username → password → Sign In → OTP if shown) using mapped locators and tokens.
            Start with empty/invalid credential negatives before happy-path when ## Untested lists them.
            Prefer UI locators for normal probes; use execute_js only for short page probes
            (read state, dispatch events, scroll) — max ~4000 chars, no network exfil.
            Emit at most actionCapPerCycle actions (see Caps). wait with no/blank ms defaults to 5000ms server-side.
            After Sign In / submit click, emit wait{ms:1000} before assert_text (UI may paint late).
            After a click that opens a dropdown, menu, modal, or drawer, emit wait{ms:3000} first.
            Do not assert on toast/alert text — toasts auto-dismiss and the alert text is already
            captured for you; assert on page content or use assert_visible on a mapped control.
            Never repeat an action that already failed twice (see coverage "## Do not retry") —
            switch control, navigate, refresh, or restart_browser instead.
            Emit at most 2–3 NEW scenarios per cycle — do not fill scenarioCap in cycle 1.
            Do not re-file the same bug title/theme; prefer observed UI copy over outdated TC wording
            (file one copy-drift bug, then assert the actual message).
            When Hunt credentials tokens are present, happy-path login MUST use
            ${TARGET_USERNAME} / ${TARGET_PASSWORD} — never invent other real passwords.
            Use ${TARGET_OTP} ONLY when the credentials section lists an OTP token (otherwise skip OTP).
            Negative tests use clearly fake values (invalid_*, empty).
            ITERATIONS: each iteration — invent 2–3 scenarios in scenarios[], execute them via actions,
            then finish_iteration when this iteration's plan is done. Next iteration reads prior results.
            Use the page map as the primary DOM signal — only emit locators that appear in the page map
            (or in the slim DOM section when attached).
            HISTORY (mandatory): read ## Steps journal and ## Coverage every cycle.
            - Do NOT repeat steps that already succeeded.
            - Do NOT retry the same failing locator (see ## Do not retry) unless strategy changed
              (different control, refresh, restart_browser, longer wait).
            - Continue from the latest sub-goal, not from step 1.
            SAFETY: if current URL is blank or page map is empty, emit refresh{} or navigate{baseUrl}
            first — do not invent selectors without DOM evidence.
            FORM DATA: non-credential fields use realistic dummy values (never empty, never random garbage).
            Credential fields MUST use ${TARGET_USERNAME} / ${TARGET_PASSWORD} / ${TARGET_OTP} only.
            BUGS: file a bug only when you have evidence this cycle — a network failure in the prompt,
            a failed assert on a mapped control, or clearly wrong UI copy you can cite in actual/repro.
            Do not file speculative bugs or duplicate oracle themes (Expired Token, assert_text miss).
            Prefer ## Untested coverage gaps over repeating probed paths.
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
