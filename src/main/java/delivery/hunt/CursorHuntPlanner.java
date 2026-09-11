package delivery.hunt;

import delivery.heal.CursorHealClient;

import java.nio.file.Files;

public final class CursorHuntPlanner implements HuntPlanner {
    private final CursorHealClient cursor;

    public CursorHuntPlanner() {
        this(new CursorHealClient());
    }

    public CursorHuntPlanner(CursorHealClient cursor) {
        this.cursor = cursor;
    }

    @Override
    public HuntPlannerDecision plan(Context ctx) throws Exception {
        if (cursor == null || !cursor.isEnabled()) {
            throw new IllegalStateException(
                    "Cursor planner unavailable — enable delivery.cursor-heal and CURSOR_API_KEY, or choose Ollama");
        }
        String prompt = OllamaHuntPlanner.buildUserPrompt(ctx);
        String slimForCursor = ctx.includeSlimDom() ? ctx.slimDom() : "";
        String raw = cursor.huntPlan(prompt, slimForCursor, ctx.screenshotPath());
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Cursor planner returned empty response");
        }
        try {
            return HuntPlannerDecision.parse(raw);
        } catch (Exception first) {
            String repair = prompt + "\n\n## Repair\nPrevious reply was invalid JSON ("
                    + first.getMessage()
                    + "). Return ONLY one valid JSON object with keys decision, rationale, actions, bugs, scenarios.";
            String repaired = cursor.huntPlan(repair, slimForCursor, ctx.screenshotPath());
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
        if (cursor == null || !cursor.isEnabled()) {
            return "";
        }
        String prompt = (triageUserPrompt == null ? "" : triageUserPrompt)
                + "\n\nReturn ONLY the triage JSON object.";
        String raw = cursor.huntPlan(prompt, "", null);
        return raw == null ? "" : raw;
    }
}
