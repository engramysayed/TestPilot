package delivery.effect;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Perceptual-ish screenshot diff: mean luminance per block (default 8x8 px), masked rectangles ignored.
 * A block "changed" when its mean luma moves more than {@code lumaThreshold} (0..255). Robust to
 * antialiasing/compression noise, cheap (no deps). Like Maestro's differencePercent with a threshold.
 */
public final class ScreenshotDiff {
    public record Result(int changedBlocks, int totalBlocks, double changedFraction) {
    }

    private ScreenshotDiff() {
    }

    public static Result compare(byte[] beforePng, byte[] afterPng, List<Rectangle> masksPx, int block, double lumaThreshold) {
        BufferedImage a = read(beforePng), b = read(afterPng);
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return new Result(1, 1, 1.0); // viewport/size change is an effect
        }
        int w = a.getWidth(), h = a.getHeight();
        int bw = (w + block - 1) / block, bh = (h + block - 1) / block;
        int changed = 0, total = 0;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                Rectangle r = new Rectangle(bx * block, by * block, Math.min(block, w - bx * block), Math.min(block, h - by * block));
                if (masked(r, masksPx)) continue;
                total++;
                if (Math.abs(meanLuma(a, r) - meanLuma(b, r)) > lumaThreshold) changed++;
            }
        }
        return new Result(changed, total, total == 0 ? 0 : (double) changed / total);
    }

    private static boolean masked(Rectangle r, List<Rectangle> masks) {
        if (masks == null) return false;
        for (Rectangle m : masks) if (m.intersects(r)) return true;
        return false;
    }

    private static double meanLuma(BufferedImage img, Rectangle r) {
        double sum = 0;
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                int p = img.getRGB(x, y);
                sum += 0.299 * ((p >> 16) & 255) + 0.587 * ((p >> 8) & 255) + 0.114 * (p & 255);
            }
        }
        return sum / (r.width * (double) r.height);
    }

    private static BufferedImage read(byte[] png) {
        try {
            BufferedImage i = ImageIO.read(new ByteArrayInputStream(png));
            if (i == null) throw new IllegalArgumentException("not an image");
            return i;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
