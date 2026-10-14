package delivery.routing;

import delivery.snapshot.PageModel;
import delivery.snapshot.SnapshotCandidate;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class ModelRoutingTest {
    @AfterMethod public void clear() {
        System.clearProperty("keel.route.heal");
    }

    @Test public void everyIntentRoutesAndBudgetsArePinned() {
        ModelRouter r = ModelRouter.builtin();
        // golden: (context - reserve - image) * share * charsPerToken
        assertEquals(ModelRouter.observationChars(r.route(ModelRouter.Intent.GROUNDING)), (int) Math.floor((32768 - 4096 - 2500) * 0.5 * 3.5));
        assertEquals(ModelRouter.observationChars(r.route(ModelRouter.Intent.BIND)), (int) Math.floor((8192 - 2048) * 0.8 * 3.5));
        assertEquals(ModelRouter.observationChars(r.route(ModelRouter.Intent.HEAL)), (int) Math.floor((200000 - 8192) * 0.6 * 4.0));
        assertEquals(ModelRouter.observationChars(r.route(ModelRouter.Intent.NAMING)), (int) Math.floor((8192 - 1024) * 0.5 * 3.5));
        for (ModelRouter.Intent i : ModelRouter.Intent.values()) assertTrue(ModelRouter.observationChars(r.route(i)) >= ModelRouter.MIN_OBSERVATION_CHARS);
        assertEquals(r.route(ModelRouter.Intent.GROUNDING).model().model(), "ui-tars");
        assertEquals(r.route(ModelRouter.Intent.HEAL).model().costTag(), "cloud-paid");
    }

    @Test public void perIntentOverride() {
        System.setProperty("keel.route.heal", "qwen-text");
        assertEquals(ModelRouter.builtin().route(ModelRouter.Intent.HEAL).model().provider(), "ollama");
        System.setProperty("keel.route.heal", "nope");
        expectThrows(IllegalArgumentException.class, () -> ModelRouter.builtin().route(ModelRouter.Intent.HEAL));
    }

    @Test public void invalidTablesRejected() throws Exception {
        String good;
        try (InputStream in = getClass().getResourceAsStream("/keel/model-routing.json")) { good = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        String[][] bad = {
                {"\"version\": 1", "\"version\": 9"},
                {"\"naming\":       {", "\"namingX\":      {"},
                {"\"grounding\":    {\"model\": \"uitars-local\"", "\"grounding\":    {\"model\": \"gemma-local\""},
                {"\"heal\":         {\"model\": \"cursor-heal\"", "\"heal\":         {\"model\": \"ghost\""},
                {"\"costTag\": \"local-cpu\"", "\"costTag\": \"local-cpu\", \"extra\": 1"},
        };
        for (String[] b : bad) {
            assertTrue(good.contains(b[0]), b[0]);
            expectThrows(IllegalArgumentException.class, () -> ModelRouter.parse(good.replace(b[0], b[1])));
        }
    }

    @Test public void ledgerLogsCostTagsAndSummarises() throws Exception {
        ModelRouter r = ModelRouter.builtin();
        try (CallLedger.Scope s = CallLedger.scope()) {
            CallLedger.timed(r.route(ModelRouter.Intent.GROUNDING), 3500, () -> "x".repeat(350));
            CallLedger.timed(r.route(ModelRouter.Intent.HEAL), 40000, () -> "y".repeat(4000));
            expectThrows(IllegalStateException.class, () -> CallLedger.timed(r.route(ModelRouter.Intent.HEAL), 10, () -> { throw new IllegalStateException("down"); }));
            assertEquals(s.entries().size(), 3);
            CallLedger.Entry g = s.entries().get(0);
            assertEquals(g.costTag(), "local-gpu");
            assertEquals(g.estInTokens(), 1000);
            assertEquals(g.costUnits(), 1000 / 1000.0 * 0.2 + 100 / 1000.0 * 0.4, 1e-9);
            assertFalse(s.entries().get(2).ok());
            var sum = s.summary();
            assertEquals(sum.getInt("calls"), 3);
            assertEquals(sum.getJSONObject("byIntent").getJSONObject("heal").getInt("calls"), 2);
            assertEquals(sum.getJSONObject("byCostTag").getJSONObject("cloud-paid").getDouble("costUnits"), 10000 / 1000.0 * 3.0 + 1000 / 1000.0 * 15.0 + 3 / 1000.0 * 3.0, 1e-6);
        }
        // outside a scope: still logged, not collected
        CallLedger.record(r.route(ModelRouter.Intent.NAMING), 10, 10, 1, true);
    }

    static SnapshotCandidate cand(String name, boolean interactive, boolean keyword, boolean inViewport, boolean nav) {
        return new SnapshotCandidate(interactive ? "button" : "p", "", name, Map.of(), "#" + name.replaceAll("\\W", ""), List.of(), List.of(),
                interactive, keyword, inViewport, !inViewport, "", nav, false);
    }

    @Test public void budgetedRenderPrioritisesInteractiveAndKeywords() {
        List<SnapshotCandidate> cs = new ArrayList<>();
        for (int i = 0; i < 40; i++) cs.add(cand("Paragraph text number " + i, false, false, true, false));
        cs.add(cand("Checkout", true, true, false, false));          // e40: keyword + interactive, offscreen
        for (int i = 0; i < 10; i++) cs.add(cand("Btn" + i, true, false, true, false));
        cs.add(cand("Shipping", false, true, true, false));          // e51: keyword text
        cs.add(cand("Menu item", false, false, true, true));         // e52: nav
        PageModel m = new PageModel(true, 1, false, cs);
        String full = m.render(0);
        assertEquals(m.renderBudgeted(full.length()), full, "fits: identical to render()");

        String small = m.renderBudgeted(800);
        assertTrue(small.length() <= 800, small.length() + "");
        assertTrue(small.contains("[e40] "), "keyword+interactive kept even offscreen:\n" + small);
        assertTrue(small.contains("Btn0") && small.contains("Btn9"), small);
        assertTrue(small.contains("[e51] ") && small.contains("[e52] "), "keyword text and nav before plain text:\n" + small);
        assertFalse(small.contains("Paragraph text number 39"), "leftover space filled with plain text in document order only");
        assertTrue(small.matches("(?s).*\\.\\.\\. \\d+ more elements omitted \\(observation budget\\)\\n$"), small);
        // document order preserved among kept lines
        assertTrue(small.indexOf("[e40]") < small.indexOf("[e41]"));
        assertTrue(m.renderBudgeted(1300).length() <= 1300);
        String tiny = m.renderBudgeted(120);
        assertTrue(tiny.contains("[e40] "), "highest tier first even when only one line fits:\n" + tiny);
    }
}
