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
    public void compactRollsPerEventLinesIntoCountLinesAndShrinksFile() throws Exception {
        Path tmp = Files.createTempDirectory("locout-compact");
        Path file = tmp.resolve("locator-outcomes.jsonl");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"WIN\",\"target\":\"By.id: a\"}\n");
        }
        for (int i = 0; i < 40; i++) {
            sb.append("{\"page\":\"P\",\"field\":\"f\",\"slot\":1,\"outcome\":\"MISS\",\"target\":\"By.cssSelector: b\"}\n");
        }
        Files.writeString(file, sb.toString());
        long sizeBefore = Files.size(file);
        LocatorOutcomeStore.Stats statsBefore = LocatorOutcomeStore.readFile(file).lookup("P", "f", "By.id: a");
        Assert.assertEquals(statsBefore.wins(), 100);

        LocatorOutcomeStore.compact(file);

        long sizeAfter = Files.size(file);
        Assert.assertTrue(sizeAfter < sizeBefore / 10,
                "compacted file must shrink: " + sizeBefore + " -> " + sizeAfter);
        LocatorOutcomeStore.Aggregate agg = LocatorOutcomeStore.readFile(file);
        LocatorOutcomeStore.Stats a = agg.lookup("P", "f", "By.id: a");
        LocatorOutcomeStore.Stats b = agg.lookup("P", "f", "By.cssSelector: b");
        Assert.assertEquals(a.wins(), 100);
        Assert.assertEquals(a.misses(), 0);
        Assert.assertEquals(b.wins(), 0);
        Assert.assertEquals(b.misses(), 40);
    }

    @Test
    public void compactIsIdempotentAndMergesWithLegacyLinesAppendedAfter() throws Exception {
        Path tmp = Files.createTempDirectory("locout-idem");
        Path file = tmp.resolve("locator-outcomes.jsonl");
        Files.writeString(file,
                "{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"WIN\",\"target\":\"By.id: a\"}\n"
                        + "{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"WIN\",\"target\":\"By.id: a\"}\n"
                        + "{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"MISS\",\"target\":\"By.id: a\"}\n");
        LocatorOutcomeStore.compact(file);
        LocatorOutcomeStore.compact(file); // second pass must stay stable
        // append a fresh per-event line after compaction
        Files.writeString(file,
                "{\"page\":\"P\",\"field\":\"f\",\"slot\":0,\"outcome\":\"WIN\",\"target\":\"By.id: a\"}\n",
                java.nio.file.StandardOpenOption.APPEND);
        LocatorOutcomeStore.Stats s = LocatorOutcomeStore.readFile(file).lookup("P", "f", "By.id: a");
        Assert.assertEquals(s.wins(), 3);
        Assert.assertEquals(s.misses(), 1);
    }

    @Test
    public void targetSigMatchesSeleniumByToString() {
        Assert.assertEquals(LocatorOutcomeStore.targetSig("id", "foo"), "By.id: foo");
        Assert.assertEquals(LocatorOutcomeStore.targetSig("cssSelector", "div[a=1]"), "By.cssSelector: div[a=1]");
        Assert.assertEquals(LocatorOutcomeStore.targetSig("xpath", "//x"), "By.xpath: //x");
        Assert.assertEquals(LocatorOutcomeStore.targetSig("name", "n"), "By.name: n");
    }
}
