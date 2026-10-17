package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Source contract: bind-quality counters are job-scoped (one metrics instance),
 * not reset per TC inside proveOne. pageCatalog still clears per TC; flush stays in finally.
 */
public class BindQualityJobScopeTest {

    private static final Path PROVE_PHASE =
            Path.of("src/main/java/delivery/job/ProvePhase.java");

    @Test
    public void proveOne_keepsPageCatalogClear_andFlush_butDoesNotZeroBindQuality() throws Exception {
        Assert.assertTrue(Files.isRegularFile(PROVE_PHASE), "ProvePhase.java missing at " + PROVE_PHASE.toAbsolutePath());
        String src = Files.readString(PROVE_PHASE, StandardCharsets.UTF_8);
        int start = src.indexOf("private TcDraft proveOne(");
        Assert.assertTrue(start >= 0, "proveOne method not found");
        int end = src.indexOf("private TcDraft proveBatchFallback(", start);
        Assert.assertTrue(end > start, "proveBatchFallback after proveOne not found");
        String proveOne = src.substring(start, end);

        Assert.assertTrue(proveOne.contains("pageCatalog.clear();"),
                "proveOne must still clear pageCatalog per TC");
        Assert.assertTrue(proveOne.contains("flushBindQuality("),
                "proveOne must still flush bind-quality in finally");

        Assert.assertFalse(
                proveOne.contains("bindQuality.bindClear = bindQuality.bindTie = bindQuality.bindEmpty = 0"),
                "proveOne must not zero bindClear/bindTie/bindEmpty");
        Assert.assertFalse(
                proveOne.contains("bindQuality.catalogHit = bindQuality.catalogRebuild = 0"),
                "proveOne must not zero catalogHit/catalogRebuild");
        Assert.assertFalse(
                proveOne.contains("bindQuality.ollamaAsked = bindQuality.visionAsked = bindQuality.healEntered = 0"),
                "proveOne must not zero ollamaAsked/visionAsked/healEntered");
    }
}