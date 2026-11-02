package delivery.knowledge;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class KnowledgePackGuidanceTest {

    @Test
    public void compose_emptyWhenNoPacks() {
        Assert.assertEquals(KnowledgePackGuidance.compose(null), "");
        Assert.assertEquals(KnowledgePackGuidance.compose(List.of()), "");
    }

    @Test
    public void compose_sectionsByTitleAndIncludesMarkdown() {
        KnowledgePack pack = new KnowledgePack(
                "kp_01JEXAMPLE",
                "demo-shop",
                "Checkout happy path",
                KnowledgePackSource.LIBRARY,
                30,
                1,
                "# Checkout\n\n## TC-01 Login\n",
                "knowledge-packs/kp_01JEXAMPLE/pack.md",
                "2026-11-02T15:00:00Z",
                "2026-11-02T15:00:00Z"
        );
        String md = KnowledgePackGuidance.compose(List.of(pack));
        Assert.assertTrue(md.contains("## Knowledge pack guidance"));
        Assert.assertTrue(md.contains("Checkout happy path"));
        Assert.assertTrue(md.contains("`kp_01JEXAMPLE`"));
        Assert.assertTrue(md.contains("# Checkout"));
        Assert.assertTrue(md.contains("evidence"));
    }

    @Test
    public void fixtureReader_getByIds_skipsMissingKeepsOrder() {
        FixtureKnowledgePackReader reader = new FixtureKnowledgePackReader()
                .put(new KnowledgePack(
                        "kp_a", "demo", "A", KnowledgePackSource.LIBRARY, 20, 1,
                        "body-a", "knowledge-packs/kp_a/pack.md", "", ""))
                .put(new KnowledgePack(
                        "kp_b", "demo", "B", KnowledgePackSource.UPLOAD_MD, 40, 2,
                        "body-b", "knowledge-packs/kp_b/pack.md", "", ""));
        List<KnowledgePack> packs = reader.getByIds("demo", List.of("kp_b", "missing", "kp_a", "kp_b"));
        Assert.assertEquals(packs.size(), 2);
        Assert.assertEquals(packs.get(0).packId(), "kp_b");
        Assert.assertEquals(packs.get(1).packId(), "kp_a");
    }
}