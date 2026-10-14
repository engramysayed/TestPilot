package project.utils.settle;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Visual-change check, ported from the Automation Engine's A7 {@code ScreenshotDiff}: mean luma
 * (0.299R + 0.587G + 0.114B) per {@link #BLOCK}x{@link #BLOCK} px block; a block counts as changed when
 * its mean moves more than {@link #LUMA_THRESHOLD}/255; {@link #MIN_CHANGED_BLOCKS} changed block is an
 * effect. Blocks intersecting a mask rectangle are skipped. A size change counts as an effect.
 */
public final class ScreenshotDiff {
    public static final int BLOCK = 8;
    public static final double LUMA_THRESHOLD = 8.0;
    public static final int MIN_CHANGED_BLOCKS = 1;

    public record Result(int changedBlocks, int totalBlocks) {
        public boolean changed() {
            return changedBlocks >= MIN_CHANGED_BLOCKS;
        }
    }

    private ScreenshotDiff() {
    }

    public static Result compare(byte[] beforePng, byte[] afterPng, List<Rectangle> masksPx) {
        return compare(read(beforePng), read(afterPng), masksPx);
    }

    public static Result compare(BufferedImage a, BufferedImage b, List<Rectangle> masksPx) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return new Result(1, 1); // viewport/size change is an effect
        }
        int w = a.getWidth(), h = a.getHeight();
        int bw = (w + BLOCK - 1) / BLOCK, bh = (h + BLOCK - 1) / BLOCK;
        int changed = 0, total = 0;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                Rectangle r = new Rectangle(bx * BLOCK, by * BLOCK, Math.min(BLOCK, w - bx * BLOCK), Math.min(BLOCK, h - by * BLOCK));
                if (masked(r, masksPx)) {
                    continue;
                }
                total++;
                if (Math.abs(meanLuma(a, r) - meanLuma(b, r)) > LUMA_THRESHOLD) {
                    changed++;
                }
            }
        }
        return new Result(changed, total);
    }

    /** Grows a rectangle by one block on every side (mask slack for antialiasing, shadows, growth). */
    public static Rectangle withSlack(Rectangle r) {
        return new Rectangle(r.x - BLOCK, r.y - BLOCK, r.width + 2 * BLOCK, r.height + 2 * BLOCK);
    }

    private static boolean masked(Rectangle r, List<Rectangle> masks) {
        if (masks == null) {
            return false;
        }
        for (Rectangle m : masks) {
            if (m.intersects(r)) {
                return true;
            }
        }
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

    static BufferedImage read(byte[] png) {
        try {
            BufferedImage i = ImageIO.read(new ByteArrayInputStream(png));
            if (i == null) {
                throw new IllegalArgumentException("not an image");
            }
            return i;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
