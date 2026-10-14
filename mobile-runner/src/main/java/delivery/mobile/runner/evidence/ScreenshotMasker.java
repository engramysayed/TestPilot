package delivery.mobile.runner.evidence;

import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

/**
 * Masks sensitive bounds on a raw RGBA buffer (row-major, width*height*4).
 * When geometry unknown, returns a fully zeroed copy (withhold rather than leak).
 */
public final class ScreenshotMasker {
    private ScreenshotMasker() {}

    public static byte[] mask(byte[] rgba, int width, int height, MobileSnapshot snap) {
        if (rgba == null || rgba.length == 0 || width <= 0 || height <= 0) {
            return rgba == null ? new byte[0] : rgba.clone();
        }
        byte[] out = rgba.clone();
        if (snap == null) {
            for (int i = 0; i < out.length; i++) out[i] = 0;
            return out;
        }
        for (MobileSnapshotNode n : snap.nodes()) {
            if (!n.password() && !containsCanary(n)) continue;
            MobileBounds b = n.bounds();
            if (b == null) continue;
            int left = Math.max(0, b.left());
            int top = Math.max(0, b.top());
            int right = Math.min(width, b.right());
            int bottom = Math.min(height, b.bottom());
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) {
                    int i = (y * width + x) * 4;
                    if (i + 3 < out.length) {
                        out[i] = 0;
                        out[i + 1] = 0;
                        out[i + 2] = 0;
                        out[i + 3] = (byte) 255;
                    }
                }
            }
        }
        return out;
    }

    private static boolean containsCanary(MobileSnapshotNode n) {
        return n.rawText().contains(XmlRedactor.SECRET_CANARY)
                || n.contentDesc().contains(XmlRedactor.SECRET_CANARY)
                || n.hint().contains(XmlRedactor.SECRET_CANARY);
    }
}
