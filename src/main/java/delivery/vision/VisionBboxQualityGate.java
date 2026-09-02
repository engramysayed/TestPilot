package delivery.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rejects junk UI-TARS / VLM grounding candidates before heal uses them.
 */
public final class VisionBboxQualityGate {
    public static final int MIN_SIDE_PX = 8;
    public static final double MIN_CONFIDENCE = 0.7;
    public static final double MAX_HEIGHT_RATIO = 0.25;
    public static final double MAX_WIDTH_RATIO = 0.22;
    public static final double MAX_AREA_RATIO = 0.15;
    public static final int ESSAY_LEN = 120;

    private VisionBboxQualityGate() {
    }

    public static List<VisualCandidate> filter(List<VisualCandidate> in, int imageW, int imageH) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<VisualCandidate> out = new ArrayList<>();
        for (VisualCandidate c : in) {
            if (c != null && accept(c, imageW, imageH)) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    static boolean accept(VisualCandidate c, int imageW, int imageH) {
        if (c.confidence() < MIN_CONFIDENCE) {
            return false;
        }
        BoundingBox box = c.boundingBox();
        if (box == null || box.width() < MIN_SIDE_PX || box.height() < MIN_SIDE_PX) {
            return false;
        }
        if (imageW > 0 && imageH > 0) {
            double heightRatio = (double) box.height() / (double) imageH;
            double widthRatio = (double) box.width() / (double) imageW;
            double areaRatio = ((double) box.width() * (double) box.height())
                    / ((double) imageW * (double) imageH);
            if (heightRatio > MAX_HEIGHT_RATIO || widthRatio > MAX_WIDTH_RATIO
                    || areaRatio > MAX_AREA_RATIO) {
                return false;
            }
        }
        return !looksLikePageEssay(c.description());
    }

    static boolean looksLikePageEssay(String description) {
        if (description == null) {
            return false;
        }
        String d = description.trim();
        if (d.length() <= ESSAY_LEN) {
            return false;
        }
        String lower = d.toLowerCase(Locale.ROOT);
        long commas = d.chars().filter(ch -> ch == ',').count();
        return commas >= 2 && (lower.contains("containing") || lower.contains("page"));
    }

    /**
     * When the model returns a region-sized box with a short control label, shrink to a
     * center pad so elementFromPoint can still hit the widget.
     */
    public static VisualCandidate shrinkOversizedToCenterPad(
            VisualCandidate c, int imageW, int imageH) {
        if (c == null || c.boundingBox() == null) {
            return null;
        }
        if (accept(c, imageW, imageH)) {
            return c;
        }
        if (c.confidence() < MIN_CONFIDENCE) {
            return null;
        }
        String desc = c.description() == null ? "" : c.description().trim();
        if (desc.length() > 80 || looksLikePageEssay(desc)) {
            return null;
        }
        BoundingBox box = c.boundingBox();
        int cx = box.x() + Math.max(1, box.width()) / 2;
        int cy = box.y() + Math.max(1, box.height()) / 2;
        int pad = 16;
        int left = Math.max(0, cx - pad);
        int top = Math.max(0, cy - pad);
        int width = pad * 2;
        int height = pad * 2;
        if (imageW > 0) {
            width = Math.min(width, Math.max(1, imageW - left));
        }
        if (imageH > 0) {
            height = Math.min(height, Math.max(1, imageH - top));
        }
        VisualCandidate shrunk = new VisualCandidate(desc, new BoundingBox(left, top, width, height), c.confidence());
        return accept(shrunk, imageW, imageH) ? shrunk : null;
    }
}
