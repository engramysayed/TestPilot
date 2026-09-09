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
        return HuntPlannerDecision.parse(raw);
    }
}
