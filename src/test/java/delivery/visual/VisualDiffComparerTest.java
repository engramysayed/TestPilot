package delivery.visual;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.*;

public class VisualDiffComparerTest {
    private String prevEnabled;
    private String prevViewport;
    private String prevThreshold;
    private Path tmp;

    @BeforeMethod
    public void setUp() throws Exception {
        prevEnabled = System.getProperty(VisualDiffConfig.PROP_ENABLED);
        prevViewport = System.getProperty(VisualDiffConfig.PROP_VIEWPORT);
        prevThreshold = System.getProperty(VisualDiffConfig.PROP_FAIL_FRACTION);
        System.setProperty(VisualDiffConfig.PROP_ENABLED, "true");
        System.setProperty(VisualDiffConfig.PROP_VIEWPORT, "desktop");
        System.setProperty(VisualDiffConfig.PROP_FAIL_FRACTION, "0.02");
        tmp = Files.createTempDirectory("keel-visual-diff-");
    }

    @AfterMethod
    public void tearDown() {
        restore(VisualDiffConfig.PROP_ENABLED, prevEnabled);
        restore(VisualDiffConfig.PROP_VIEWPORT, prevViewport);
        restore(VisualDiffConfig.PROP_FAIL_FRACTION, prevThreshold);
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private static byte[] solidPng(Color color, int w, int h) throws Exception {
        BufferedImage i = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(i, "png", o);
        return o.toByteArray();
    }

    @Test
    public void noBaselineWritesActualAndNoBaselineStatus() throws Exception {
        byte[] actual = solidPng(Color.WHITE, 80, 60);
        VisualDiffCompareResult r = VisualDiffComparer.compareAndWrite(
                tmp, "run1", "TC~5F1", "desktop", actual);
        assertEquals(r.status(), VisualDiffStatus.NO_BASELINE);
        assertTrue(Files.isRegularFile(r.actualPath()));
        assertTrue(Files.isRegularFile(r.resultPath()));
        assertFalse(Files.isRegularFile(
                VisualBaselinePaths.baselinePng(tmp, "TC~5F1", "desktop")));
    }

    @Test
    public void identicalPassWithinThreshold() throws Exception {
        byte[] png = solidPng(Color.WHITE, 80, 60);
        VisualBaselineStore.writeBaseline(tmp, "prj", "TC~5F1", "desktop", png, "seed", 1);
        VisualDiffCompareResult r = VisualDiffComparer.compareAndWrite(
                tmp, "run2", "TC~5F1", "desktop", png);
        assertEquals(r.status(), VisualDiffStatus.PASS);
        assertEquals(r.changedBlocks(), 0);
        assertTrue(Files.isRegularFile(r.resultPath()));
        // baseline must not be rewritten by compare
        VisualBaselineMeta meta = VisualBaselineStore.readMeta(tmp, "TC~5F1", "desktop").orElseThrow();
        assertEquals(meta.revision(), 1);
        assertEquals(meta.sourceRunId(), "seed");
    }

    @Test
    public void sizeMismatchFails() throws Exception {
        VisualBaselineStore.writeBaseline(
                tmp, "prj", "TC~5F1", "desktop", solidPng(Color.WHITE, 80, 60), "seed", 1);
        VisualDiffCompareResult r = VisualDiffComparer.compareAndWrite(
                tmp, "run3", "TC~5F1", "desktop", solidPng(Color.WHITE, 100, 60));
        assertEquals(r.status(), VisualDiffStatus.FAIL);
        assertEquals(r.changedFraction(), 1.0);
    }

    @Test
    public void realChangeFailsAboveThreshold() throws Exception {
        VisualBaselineStore.writeBaseline(
                tmp, "prj", "TC~5F1", "desktop", solidPng(Color.WHITE, 80, 60), "seed", 1);
        VisualDiffCompareResult r = VisualDiffComparer.compareAndWrite(
                tmp, "run4", "TC~5F1", "desktop", solidPng(Color.RED, 80, 60));
        assertEquals(r.status(), VisualDiffStatus.FAIL);
        assertTrue(r.changedFraction() > 0.02);
    }

    @Test
    public void caseKeyUsesStorageKeyEncoding() {
        assertEquals(VisualBaselinePaths.caseKey("TC_1"), "TC~5F1");
        assertEquals(VisualBaselinePaths.sanitizeViewport("1280x800"), "1280x800");
        assertEquals(VisualBaselinePaths.sanitizeViewport(" desktop "), "desktop");
    }

    @Test
    public void featureFlagDefaultsOff() {
        System.clearProperty(VisualDiffConfig.PROP_ENABLED);
        assertFalse(VisualDiffConfig.enabled());
    }
}
