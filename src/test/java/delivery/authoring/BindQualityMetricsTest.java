package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class BindQualityMetricsTest {

    @Test
    public void writeJson_roundTripsCounts() throws Exception {
        BindQualityMetrics m = new BindQualityMetrics();
        m.bindClear = 3;
        m.bindTie = 2;
        m.bindEmpty = 1;
        m.catalogHit = 4;
        m.catalogRebuild = 5;
        m.ollamaAsked = 6;
        m.visionAsked = 7;
        m.healEntered = 8;
        m.falseBind = 99;
        Path tmp = Files.createTempFile("bind-quality", ".json");
        try {
            m.writeJson(tmp);
            String json = Files.readString(tmp);
            Assert.assertTrue(json.contains("\"clear\":3"), json);
            Assert.assertTrue(json.contains("\"tie\":2"), json);
            Assert.assertTrue(json.contains("\"empty\":1"), json);
            Assert.assertTrue(json.contains("\"ollama\":6"), json);
            Assert.assertTrue(json.contains("\"vision\":7"), json);
            Assert.assertTrue(json.contains("\"heal\":8"), json);
            Assert.assertFalse(json.contains("falseBind"), json);
            Assert.assertTrue(m.summaryLine().startsWith("BIND_QUALITY clear=3"), m.summaryLine());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void add_incrementsByKind() {
        BindQualityMetrics m = new BindQualityMetrics();
        m.add(BindDecision.Kind.CLEAR);
        m.add(BindDecision.Kind.TIE);
        m.add(BindDecision.Kind.EMPTY);
        Assert.assertEquals(m.bindClear, 1);
        Assert.assertEquals(m.bindTie, 1);
        Assert.assertEquals(m.bindEmpty, 1);
    }
}