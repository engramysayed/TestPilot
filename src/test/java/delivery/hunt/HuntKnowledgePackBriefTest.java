package delivery.hunt;

import delivery.excel.ManualTestCase;
import delivery.knowledge.KnowledgePack;
import delivery.knowledge.KnowledgePackGuidance;
import delivery.knowledge.KnowledgePackSource;
import delivery.knowledge.FixtureKnowledgePackReader;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class HuntKnowledgePackBriefTest {

    @Test
    public void brief_withPackGuidance_includesKnowledgeSection() {
        HuntRequest req = new HuntRequest();
        req.setProjectId("demo-shop");
        req.setJobId("hunt_test");
        req.setBaseUrl("https://example.test/login");
        req.setTcIds(List.of("TC_01"));
        req.setKnowledgePackIds(List.of("kp_01JEXAMPLE"));
        req.normalize();

        KnowledgePack pack = new KnowledgePack(
                "kp_01JEXAMPLE",
                "demo-shop",
                "Checkout happy path",
                KnowledgePackSource.LIBRARY,
                30,
                1,
                "# Checkout\n\nProbe cart totals.\n",
                "knowledge-packs/kp_01JEXAMPLE/pack.md",
                "2026-11-02T15:00:00Z",
                "2026-11-02T15:00:00Z"
        );
        String guidance = KnowledgePackGuidance.compose(List.of(pack));
        String brief = HuntBriefBuilder.build(
                req,
                List.of(new ManualTestCase("TC_01", "Login", "", "1. Open", "Home", "P1", "smoke")),
                guidance
        );
        Assert.assertTrue(brief.contains("Knowledge packs: `kp_01JEXAMPLE`"));
        Assert.assertTrue(brief.contains("## Knowledge pack guidance"));
        Assert.assertTrue(brief.contains("Probe cart totals."));
        Assert.assertTrue(brief.contains("## Mission"));
    }

    @Test
    public void brief_emptyPackIds_legacyPath_noKnowledgeSection() {
        HuntRequest req = new HuntRequest();
        req.setProjectId("demo-shop");
        req.setJobId("hunt_legacy");
        req.setBaseUrl("https://example.test/");
        req.setTcIds(List.of("TC_01"));
        req.normalize();
        Assert.assertTrue(req.getKnowledgePackIds().isEmpty());

        String brief = HuntBriefBuilder.build(
                req,
                List.of(new ManualTestCase("TC_01", "Login", "", "1. Open", "Home", "P1", "smoke")),
                ""
        );
        Assert.assertFalse(brief.contains("Knowledge pack guidance"));
        Assert.assertFalse(brief.contains("Knowledge packs:"));
        Assert.assertTrue(brief.contains("## Mission"));
    }

    @Test
    public void dryRun_withGuidance_writesBriefContainingPack() throws Exception {
        HuntRequest req = new HuntRequest();
        req.setProjectId("demo-shop");
        req.setJobId("hunt_dry_kp");
        req.setBaseUrl("https://example.test/");
        req.setTcIds(List.of("TC_01"));
        req.setKnowledgePackIds(List.of("kp_01JEXAMPLE"));
        req.setCycleCeiling(2);
        req.setScenarioCap(2);
        req.normalize();

        FixtureKnowledgePackReader reader = new FixtureKnowledgePackReader().put(new KnowledgePack(
                "kp_01JEXAMPLE", "demo-shop", "Checkout", KnowledgePackSource.LIBRARY, 30, 1,
                "Focus on payment edge cases.", "knowledge-packs/kp_01JEXAMPLE/pack.md", "", ""));
        String guidance = KnowledgePackGuidance.compose(
                reader.getByIds("demo-shop", req.getKnowledgePackIds()));

        Path root = Files.createTempDirectory("hunt-kp-dry-");
        try {
            HuntJobResult result = new DryRunHuntService().run(
                    req,
                    List.of(new ManualTestCase("TC_01", "Login", "", "1. Open", "Home", "P1", "smoke")),
                    root,
                    null,
                    new AtomicBoolean(false)::get,
                    guidance
            );
            Assert.assertNotNull(result.zipPath());
            String brief = Files.readString(root.resolve("brief.md"), StandardCharsets.UTF_8);
            Assert.assertTrue(brief.contains("Focus on payment edge cases."));
            Assert.assertTrue(brief.contains("## Knowledge pack guidance"));
        } finally {
            try (var walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                });
            }
        }
    }
}