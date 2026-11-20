package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LocatorOutcomeStoreTest {

    @Test
    public void absentFileReturnsEmptyAggregateAndKeepsOrder() throws Exception {
        Path tmp = Files.createTempDirectory("locout");
        LocatorOutcomeStore.Aggregate agg = LocatorOutcomeStore.read(tmp);
        Assert.assertTrue(agg.isEmpty());
        List<Integer> order = agg.rankedIndices("P", "f",
                List.of("By.id: a", "By.cssSelector: b", "By.xpath: c"));
        Assert.assertEquals(order, List.of(0, 1, 2));
    }

    @Test
    public void malformedLinesIgnoredFileStaysReadable() throws Exception {
        Path tmp = Files.createTempDirectory("locout");
        Path out = tmp.resolve("test-output").resolve("keel-results").resolve("locator-outcomes.jsonl");
        Files.createDirectories(out.getParent());
        Files.writeString(out, String.join("\n",
                "not-json",
                "{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"WIN\",\"target\":\"By.id: a\"}",
                "",
                "{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"MISS\",\"target\":\"By.id: a\"}",
                ""));
        LocatorOutcomeStore.Aggregate agg = LocatorOutcomeStore.read(tmp);
        LocatorOutcomeStore.Stats s = agg.lookup("P", "f", "By.id: a");
        Assert.assertNotNull(s);
        Assert.assertEquals(s.wins(), 1);
        Assert.assertEquals(s.misses(), 1);
    }

    @Test
    public void rankingPromotesFallbackThatOutperformsPrimary() throws Exception {
        Path tmp = Files.createTempDirectory("locout");
        Path out = tmp.resolve("test-output").resolve("keel-results").resolve("locator-outcomes.jsonl");
        Files.createDirectories(out.getParent());
        StringBuilder sb = new StringBuilder();
        // Slot 0 (By.id: a) misses 10 times, wins 1.
        for (int i = 0; i < 10; i++) {
            sb.append("{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"MISS\",\"target\":\"By.id: a\"}\n");
        }
        sb.append("{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"WIN\",\"target\":\"By.id: a\"}\n");
        // Slot 1 (By.cssSelector: b) wins 9 times.
        for (int i = 0; i < 9; i++) {
            sb.append("{\"page\":\"P\",\"field\":\"f\",\"slot\":1,\"outcome\":\"WIN\",\"target\":\"By.cssSelector: b\"}\n");
        }
        Files.writeString(out, sb.toString());
        LocatorOutcomeStore.Aggregate agg = LocatorOutcomeStore.read(tmp);
        List<Integer> order = agg.rankedIndices("P", "f",
                List.of("By.id: a", "By.cssSelector: b"));
        Assert.assertEquals(order.get(0).intValue(), 1, "fallback should win: " + order);
    }

    @Test
    public void unknownFallbackKeepsIncomingPositionAgainstKnownLoser() throws Exception {
        Path tmp = Files.createTempDirectory("locout");
        Path out = tmp.resolve("test-output").resolve("keel-results").resolve("locator-outcomes.jsonl");
        Files.createDirectories(out.getParent());
        // Known loser at slot 0; slot 1 unseen.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"MISS\",\"target\":\"By.id: a\"}\n");
        }
        Files.writeString(out, sb.toString());
        LocatorOutcomeStore.Aggregate agg = LocatorOutcomeStore.read(tmp);
        List<Integer> order = agg.rankedIndices("P", "f",
                List.of("By.id: a", "By.cssSelector: b"));
        Assert.assertEquals(order.get(0).intValue(), 1);
        Assert.assertEquals(order.get(1).intValue(), 0);
    }

    @Test
    public void targetSigMatchesSeleniumByToString() {
        Assert.assertEquals(LocatorOutcomeStore.targetSig("id", "foo"), "By.id: foo");
        Assert.assertEquals(LocatorOutcomeStore.targetSig("cssSelector", "div[a=1]"), "By.cssSelector: div[a=1]");
        Assert.assertEquals(LocatorOutcomeStore.targetSig("xpath", "//x"), "By.xpath: //x");
        Assert.assertEquals(LocatorOutcomeStore.targetSig("name", "n"), "By.name: n");
    }
}
