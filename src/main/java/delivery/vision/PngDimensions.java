package delivery.vision;

/**
 * Minimal PNG IHDR reader for screenshot width/height (no ImageIO dependency required).
 */
final class PngDimensions {
    private PngDimensions() {
    }

    /** @return int[]{width, height} or {0,0} if not a PNG / unreadable */
    static int[] read(byte[] png) {
        if (png == null || png.length < 24) {
            return new int[]{0, 0};
        }
        // 8-byte signature + IHDR length(4) + type(4) + width(4) + height(4)
        if (png[0] != (byte) 0x89 || png[1] != 0x50 || png[2] != 0x4E || png[3] != 0x47) {
            return new int[]{0, 0};
        }
        int width = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16)
                | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int height = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16)
                | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        if (width <= 0 || height <= 0) {
            return new int[]{0, 0};
        }
        return new int[]{width, height};
    }
}
