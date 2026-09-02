package delivery.vision;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Downscales very large screenshots before UI-TARS (model trained on smaller GUI frames).
 * Bounding boxes from the model are mapped back to original screenshot pixels.
 */
final class UiTarsImagePrep {
    /** Longest side above this is scaled down (keeps aspect ratio). */
    static final int MAX_SIDE = 1280;

    record Prepared(byte[] png, int origW, int origH, int sendW, int sendH) {
        BoundingBox toOriginal(BoundingBox box) {
            if (box == null || origW <= 0 || origH <= 0 || sendW <= 0 || sendH <= 0) {
                return box;
            }
            if (origW == sendW && origH == sendH) {
                return box;
            }
            double sx = (double) origW / (double) sendW;
            double sy = (double) origH / (double) sendH;
            return new BoundingBox(
                    (int) Math.round(box.x() * sx),
                    (int) Math.round(box.y() * sy),
                    Math.max(1, (int) Math.round(box.width() * sx)),
                    Math.max(1, (int) Math.round(box.height() * sy)));
        }
    }

    private UiTarsImagePrep() {
    }

    static Prepared prepare(byte[] screenshotPng) {
        int[] dims = PngDimensions.read(screenshotPng);
        int origW = dims[0];
        int origH = dims[1];
        if (screenshotPng == null || screenshotPng.length == 0 || origW <= 0 || origH <= 0) {
            return new Prepared(screenshotPng == null ? new byte[0] : screenshotPng, origW, origH, origW, origH);
        }
        int maxSide = Math.max(origW, origH);
        if (maxSide <= MAX_SIDE) {
            return new Prepared(screenshotPng, origW, origH, origW, origH);
        }
        double scale = (double) MAX_SIDE / (double) maxSide;
        int sendW = Math.max(1, (int) Math.round(origW * scale));
        int sendH = Math.max(1, (int) Math.round(origH * scale));
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(screenshotPng));
            if (src == null) {
                return new Prepared(screenshotPng, origW, origH, origW, origH);
            }
            BufferedImage dst = new BufferedImage(sendW, sendH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, sendW, sendH, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(dst, "png", out)) {
                return new Prepared(screenshotPng, origW, origH, origW, origH);
            }
            return new Prepared(out.toByteArray(), origW, origH, sendW, sendH);
        } catch (IOException e) {
            return new Prepared(screenshotPng, origW, origH, origW, origH);
        }
    }
}
