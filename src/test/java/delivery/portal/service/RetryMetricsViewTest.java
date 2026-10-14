package delivery.portal.service;

import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcIdentity;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class RetryMetricsViewTest {

    private static TcDraft draft(String id, TcDraftStatus status) {
        return new TcDraft(id, "t", "s", "e", status, List.of(), List.of(), false,
                -1, "", "", "", 0, "");
    }

    private static void sidecar(Path root, String id, String json) throws Exception {
        Files.createDirectories(root.resolve("retry"));
        Files.writeString(root.resolve("retry").resolve(TcIdentity.storageKey(id) + ".json"), json);
    }

    @Test
    public void legacyRunHasNoFirstPassRate() throws Exception {
        Path root = Files.createTempDirectory("rmv-legacy");
        Map<String, Object> m = RetryMetricsView.summary(root,
                List.of(draft("A", TcDraftStatus.PASSED), draft("B", TcDraftStatus.TODO)));
        Assert.assertEquals(m.get("firstPassLabel"), "0.0%");
        Assert.assertEquals(m.get("finalPassLabel"), "50.0%");
        Assert.assertEquals(m.get("legacyPassed"), 1);
        Assert.assertEquals(RetryMetricsView.forTc(root, draft("A", TcDraftStatus.PASSED)).get("verdictKey"), "legacy");
        Map<String, Object> onlyLegacy = RetryMetricsView.summary(root, List.of(draft("A", TcDraftStatus.PASSED)));
        Assert.assertEquals(onlyLegacy.get("firstPassLabel"), "n/a");
        Assert.assertEquals(onlyLegacy.get("legacy"), true);
    }

    @Test
    public void sidecarVerdictsDriveBadgesAndTiles() throws Exception {
        Path root = Files.createTempDirectory("rmv");
        sidecar(root, "P", "{\"verdict\":\"PASSED\"}");
        sidecar(root, "F", "{\"verdict\":\"FLAKY\",\"testRetries\":1}");
        sidecar(root, "H", "{\"verdict\":\"HEALED\"}");
        sidecar(root, "X", "{\"verdict\":\"FAILED\"}");
        sidecar(root, "I", "{\"verdict\":\"INFRA_ERROR\",\"infraRetries\":2}");
        sidecar(root, "C", "{\"verdict\":\"CONFIG_ERROR\"}");
        sidecar(root, "R", "{\"verdict\":\"REUSED\",\"reusedFromVerdict\":\"FLAKY\",\"reusedFlaky\":true}");
        List<TcDraft> drafts = List.of(draft("P", TcDraftStatus.PASSED), draft("F", TcDraftStatus.PASSED),
                draft("H", TcDraftStatus.PASSED), draft("X", TcDraftStatus.TODO),
                draft("I", TcDraftStatus.TODO), draft("C", TcDraftStatus.TODO), draft("R", TcDraftStatus.REUSED));
        Map<String, Object> m = RetryMetricsView.summary(root, drafts);
        Assert.assertEquals(m.get("firstPassLabel"), "25.0%");
        Assert.assertEquals(m.get("finalPassLabel"), "75.0%");
        Assert.assertEquals(m.get("flaky"), 1);
        Assert.assertEquals(m.get("healed"), 1);
        Assert.assertEquals(m.get("infraError"), 1);
        Assert.assertEquals(m.get("configError"), 1);
        Assert.assertEquals(m.get("reusedFlaky"), 1);
        Map<String, Object> r = RetryMetricsView.forTc(root, draft("R", TcDraftStatus.REUSED));
        Assert.assertEquals(r.get("verdictKey"), "reused");
        Assert.assertEquals(r.get("reusedFlaky"), true);
        Assert.assertEquals(RetryMetricsView.forTc(root, draft("I", TcDraftStatus.TODO)).get("verdictLabel"), "Infra");
        Assert.assertEquals(RetryMetricsView.forTc(root, draft("C", TcDraftStatus.TODO)).get("verdictKey"), "config");
    }

    @Test
    public void legendCoversAllEightVerdicts() {
        Assert.assertEquals(RetryMetricsView.legend().size(), 8);
        Assert.assertEquals(RetryMetricsView.legend().get(4).get("badge"), "infra");
    }
}
