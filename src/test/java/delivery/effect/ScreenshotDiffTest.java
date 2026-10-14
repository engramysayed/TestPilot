package delivery.effect;

import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.testng.Assert.*;

public class ScreenshotDiffTest {
    private static byte[] png(Rectangle red, int noise) throws Exception {
        BufferedImage i = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, 100, 60);
        if (red != null) { g.setColor(Color.RED); g.fill(red); }
        g.dispose();
        if (noise > 0) i.setRGB(50, 30, new Color(255 - noise, 255 - noise, 255 - noise).getRGB());
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(i, "png", o);
        return o.toByteArray();
    }

    @Test public void identicalAndNoiseBelowThreshold() throws Exception {
        assertEquals(ScreenshotDiff.compare(png(null, 0), png(null, 0), List.of(), 8, 8).changedBlocks(), 0);
        assertEquals(ScreenshotDiff.compare(png(null, 0), png(null, 200), List.of(), 8, 8).changedBlocks(), 0,
                "one dark pixel moves an 8x8 block mean by ~3 luma");
    }

    @Test public void realChangeAndMask() throws Exception {
        Rectangle box = new Rectangle(10, 10, 20, 10);
        assertTrue(ScreenshotDiff.compare(png(null, 0), png(box, 0), List.of(), 8, 8).changedBlocks() > 0);
        assertEquals(ScreenshotDiff.compare(png(null, 0), png(box, 0), List.of(new Rectangle(8, 8, 24, 16)), 8, 8).changedBlocks(), 0);
    }
}
