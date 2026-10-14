package delivery.mobile.runner.settle;

/**
 * Byte-wise visual diff for equal-sized RGB(A) buffers. Masked regions (status bar) can be
 * zeroed by the caller before compare. Returns changed-pixel ratio in [0,1], or null if incomparable.
 */
public final class ScreenshotDiffer {
    private ScreenshotDiffer() {}

    public static Double changedRatio(byte[] before, byte[] after, int bytesPerPixel) {
        if (before == null || after == null || before.length == 0 || after.length == 0) return null;
        if (before.length != after.length) return null;
        if (bytesPerPixel < 1) bytesPerPixel = 1;
        int pixels = before.length / bytesPerPixel;
        if (pixels == 0) return null;
        int changed = 0;
        for (int i = 0; i < before.length; i += bytesPerPixel) {
            boolean diff = false;
            for (int k = 0; k < bytesPerPixel && i + k < before.length; k++) {
                if (before[i + k] != after[i + k]) {
                    diff = true;
                    break;
                }
            }
            if (diff) changed++;
        }
        return changed / (double) pixels;
    }
}
