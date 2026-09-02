package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class UiTarsImagePrepTest {

    @Test
    public void leavesSmallImageUnchanged() throws Exception {
        byte[] png = solidPng(400, 300);
        UiTarsImagePrep.Prepared p = UiTarsImagePrep.prepare(png);
        Assert.assertEquals(p.sendW(), 400);
        Assert.assertEquals(p.sendH(), 300);
        Assert.assertEquals(p.origW(), 400);
        Assert.assertEquals(p.png().length, png.length);
    }

    @Test
    public void downscalesLargeAndMapsBoxBack() throws Exception {
        byte[] png = solidPng(1902, 984);
        UiTarsImagePrep.Prepared p = UiTarsImagePrep.prepare(png);
        Assert.assertTrue(Math.max(p.sendW(), p.sendH()) <= UiTarsImagePrep.MAX_SIDE);
        Assert.assertEquals(p.origW(), 1902);
        BoundingBox onSend = new BoundingBox(100, 50, 20, 20);
        BoundingBox orig = p.toOriginal(onSend);
        Assert.assertTrue(orig.x() > onSend.x());
        Assert.assertTrue(orig.width() >= onSend.width());
    }

    private static byte[] solidPng(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Assert.assertTrue(ImageIO.write(img, "png", out));
        return out.toByteArray();
    }
}
