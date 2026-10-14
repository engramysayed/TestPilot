package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Template ScreenshotDiff = Automation Engine A7 spec: 8x8 blocks, mean luma change &gt; 8/255, one block. */
public class ScreenshotDiffTemplateTest {
    private URLClassLoader loader;
    private Method compare;
    private Method slack;

    @BeforeClass
    public void compile() throws Exception {
        loader = CustomerTemplateCompiler.compile(Files.createTempDirectory("sdiff"),
                Path.of("customer-framework-template/src/main/java/project/utils/settle/ScreenshotDiff.java"));
        Class<?> c = loader.loadClass("project.utils.settle.ScreenshotDiff");
        compare = c.getMethod("compare", BufferedImage.class, BufferedImage.class, List.class);
        slack = c.getMethod("withSlack", Rectangle.class);
        Assert.assertEquals(c.getField("BLOCK").get(null), 8);
        Assert.assertEquals(c.getField("LUMA_THRESHOLD").get(null), 8.0);
        Assert.assertEquals(c.getField("MIN_CHANGED_BLOCKS").get(null), 1);
    }

    @AfterClass(alwaysRun = true)
    public void close() throws Exception {
        loader.close();
    }

    static BufferedImage gray(int v) {
        BufferedImage i = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        g.setColor(new Color(v, v, v));
        g.fillRect(0, 0, 64, 64);
        g.dispose();
        return i;
    }

    private int changed(BufferedImage a, BufferedImage b, List<Rectangle> masks) throws Exception {
        Object r = compare.invoke(null, a, b, masks);
        return (int) r.getClass().getMethod("changedBlocks").invoke(r);
    }

    @Test
    public void thresholdIsStrictlyMoreThanEight() throws Exception {
        BufferedImage base = gray(100);
        BufferedImage eight = gray(100);
        eight.setRGB(0, 0, 0); // irrelevant single pixel would be 100/64 luma: below threshold
        Assert.assertEquals(changed(base, eight, List.of()), 0, "one dark pixel moves its block mean by ~1.6");
        BufferedImage plus8 = gray(100);
        fill(plus8, 8, 8, 8, 8, 108);
        Assert.assertEquals(changed(base, plus8, List.of()), 0, "exactly 8/255 does not count");
        BufferedImage plus9 = gray(100);
        fill(plus9, 8, 8, 8, 8, 109);
        Assert.assertEquals(changed(base, plus9, List.of()), 1, "9/255 in one block is an effect");
    }

    @Test
    public void maskWithOneBlockSlackHidesNeighbourBlocks() throws Exception {
        BufferedImage base = gray(100);
        BufferedImage moved = gray(100);
        fill(moved, 16, 16, 8, 8, 0);  // inside the element
        fill(moved, 8, 16, 8, 8, 0);   // the block just left of it (e.g. a shadow / grown box)
        Rectangle el = new Rectangle(16, 16, 8, 8);
        Assert.assertEquals(changed(base, moved, List.of(el)), 1, "without slack the neighbour block counts");
        Assert.assertEquals(changed(base, moved, List.of((Rectangle) slack.invoke(null, el))), 0);
    }

    @Test
    public void sizeChangeIsAnEffect() throws Exception {
        Assert.assertEquals(changed(gray(1), new BufferedImage(32, 64, BufferedImage.TYPE_INT_RGB), List.of()), 1);
    }

    static void fill(BufferedImage i, int x, int y, int w, int h, int v) {
        Graphics2D g = i.createGraphics();
        g.setColor(new Color(v, v, v));
        g.fillRect(x, y, w, h);
        g.dispose();
    }
}
