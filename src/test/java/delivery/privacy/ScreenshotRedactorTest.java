package delivery.privacy;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

public class ScreenshotRedactorTest {

    private static final int CANARY_RGB = 0x00FF00CC;

    @Test
    public void passwordBoxesArePaintedOverSoCanaryPixelsDisappear() throws Exception {
        BufferedImage src = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, 20);
        g.setColor(new Color(CANARY_RGB, true));
        g.fillRect(8, 4, 16, 10);
        g.dispose();
        byte[] png = toPng(src);

        byte[] redacted = ScreenshotRedactor.redact(png, List.of(new ScreenshotRedactor.Box(8, 4, 16, 10)));
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(redacted));
        Assert.assertNotNull(out);
        int remaining = 0;
        for (int y = 4; y < 14; y++) {
            for (int x = 8; x < 24; x++) {
                if ((out.getRGB(x, y) & 0x00FFFFFF) == (CANARY_RGB & 0x00FFFFFF)) {
                    remaining++;
                }
            }
        }
        Assert.assertEquals(remaining, 0, "password-field pixels must be redacted");
        Assert.assertNotEquals(out.getRGB(10, 6) & 0x00FFFFFF, CANARY_RGB & 0x00FFFFFF);
        Assert.assertEquals(out.getRGB(1, 1) & 0x00FFFFFF, 0x00FFFFFF);
    }

    @Test
    public void blankPngPassesThrough() {
        Assert.assertEquals(ScreenshotRedactor.redact(new byte[0], List.of()), new byte[0]);
        Assert.assertNull(ScreenshotRedactor.redact(null, List.of()));
    }

    private static byte[] toPng(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
