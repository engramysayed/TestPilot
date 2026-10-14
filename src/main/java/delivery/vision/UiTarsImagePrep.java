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

    /** Pad to a multiple of this (right/bottom, white), like Midscene's padBlockSize; 0 = off (default). */
    static final String PAD_BLOCK_KEY = "delivery.vision.uitars.pad-block";

    /**
     * sendW/sendH = content pixels sent; modelW/modelH = prepared image incl. padding (== send when unpadded).
     */
    record Prepared(byte[] png, int origW, int origH, int sendW, int sendH, int modelW, int modelH) {
        Prepared(byte[] png, int origW, int origH, int sendW, int sendH) {
            this(png, origW, origH, sendW, sendH, sendW, sendH);
        }

        UiTarsCoords.Grid grid(UiTarsCoords.Space space) {
            return new UiTarsCoords.Grid(space, modelW, modelH, sendW, sendH);
        }

        /** Content-pixel box to original-screenshot box; the centre is mapped exactly (then rounded once). */
        BoundingBox toOriginal(BoundingBox box) {
            if (box == null || origW <= 0 || origH <= 0 || sendW <= 0 || sendH <= 0) {
                return box;
            }
            if (origW == sendW && origH == sendH) {
                return box;
            }
            double sx = (double) origW / (double) sendW;
            double sy = (double) origH / (double) sendH;
            int w = Math.max(1, (int) Math.round(box.width() * sx));
            int h = Math.max(1, (int) Math.round(box.height() * sy));
            double cx = (box.x() + box.width() / 2.0) * sx;
            double cy = (box.y() + box.height() / 2.0) * sy;
            return new BoundingBox((int) Math.round(cx - w / 2.0), (int) Math.round(cy - h / 2.0), w, h);
        }
    }

    private UiTarsImagePrep() {
    }

    static Prepared prepare(byte[] screenshotPng) {
        return prepare(screenshotPng, padBlock());
    }

    static int padBlock() {
        String v = System.getProperty(PAD_BLOCK_KEY);
        if (v == null || v.isBlank()) v = utils.PropertyReader.getProperty(PAD_BLOCK_KEY);
        try {
            return v == null || v.isBlank() ? 0 : Math.max(0, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static Prepared prepare(byte[] screenshotPng, int padBlock) {
        Prepared p = resize(screenshotPng);
        if (padBlock <= 1 || p.sendW() <= 0 || p.sendH() <= 0) return p;
        int mw = (int) Math.ceil((double) p.sendW() / padBlock) * padBlock;
        int mh = (int) Math.ceil((double) p.sendH() / padBlock) * padBlock;
        if (mw == p.sendW() && mh == p.sendH()) return p;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(p.png()));
            if (src == null) return p;
            BufferedImage dst = new BufferedImage(mw, mh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            try {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, mw, mh);
                g.drawImage(src, 0, 0, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(dst, "png", out)) return p;
            return new Prepared(out.toByteArray(), p.origW(), p.origH(), p.sendW(), p.sendH(), mw, mh);
        } catch (IOException e) {
            return p;
        }
    }

    private static Prepared resize(byte[] screenshotPng) {
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
