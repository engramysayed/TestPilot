package delivery.portal.service;

import delivery.effect.ScreenshotDiff;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Round-trip: save baseline → compare changed (fail) → approve → compare pass.
 */
public class VisualDiffRoundTripTest {

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

    private static byte[] withRedBox(Color bg, int w, int h) throws Exception {
        BufferedImage i = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.RED);
        g.fillRect(10, 10, 40, 30);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(i, "png", o);
        return o.toByteArray();
    }

    @Test
    public void baselineCompareFailApproveComparePass() throws Exception {
        Path root = Files.createTempDirectory("visual-diff-rt");
        VisualDiffDiskStore store = new VisualDiffDiskStore();
        String caseKey = "TC_LOGIN";
        String viewport = "1280";
        String runId = "job_run_001";

        byte[] baseline = solidPng(Color.WHITE, 120, 80);
        Map<String, Object> meta = store.saveBaseline(root, caseKey, viewport, baseline, null, 8, 8.0, 0.02);
        Assert.assertEquals(meta.get("caseKey"), caseKey);
        Assert.assertTrue(Files.isRegularFile(store.baselineDir(root, caseKey, viewport).resolve("baseline.png")));
        Assert.assertTrue(Files.isRegularFile(store.baselineDir(root, caseKey, viewport).resolve("meta.json")));

        byte[] changed = withRedBox(Color.WHITE, 120, 80);
        Map<String, Object> fail = store.compare(root, runId, caseKey, viewport, changed, true);
        Assert.assertEquals(fail.get("passed"), Boolean.FALSE);
        Assert.assertTrue(((Number) fail.get("changedBlocks")).intValue() > 0);
        Path diffDir = store.diffDir(root, runId, caseKey, viewport);
        Assert.assertTrue(Files.isRegularFile(diffDir.resolve("actual.png")));
        Assert.assertTrue(Files.isRegularFile(diffDir.resolve("diff.png")));
        Assert.assertTrue(Files.isRegularFile(diffDir.resolve("result.json")));

        Map<String, Object> approved = store.approveFromRun(root, runId, caseKey, viewport);
        Assert.assertEquals(approved.get("sourceRunId"), runId);

        String run2 = "job_run_002";
        Map<String, Object> pass = store.compare(root, run2, caseKey, viewport, changed, true);
        Assert.assertEquals(pass.get("passed"), Boolean.TRUE, "after approve, same actual should pass");
        Assert.assertFalse(Files.isRegularFile(store.diffDir(root, run2, caseKey, viewport).resolve("diff.png")),
                "passing compare should not keep a diff.png");

        Assert.assertEquals(store.listPending(root, runId).size(), 1);
        Assert.assertEquals(store.listPending(root, run2).size(), 0);
        Assert.assertEquals(store.listBaselines(root).size(), 1);
    }

    @Test
    public void renderDiffPng_producesBytes() throws Exception {
        byte[] a = solidPng(Color.WHITE, 64, 48);
        byte[] b = withRedBox(Color.WHITE, 64, 48);
        byte[] diff = ScreenshotDiff.renderDiffPng(a, b, java.util.List.of(), 8, 8);
        Assert.assertTrue(diff.length > 8);
        Assert.assertEquals(diff[0] & 0xFF, 0x89);
        Assert.assertTrue(ScreenshotDiff.compare(a, b, java.util.List.of(), 8, 8).changedFraction() > 0);
    }
}
