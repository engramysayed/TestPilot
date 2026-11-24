package delivery.mobile.adb;

import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static org.testng.Assert.assertTrue;

public class H264Fmp4Test {

    @Test
    public void aFrameBecomesAFragmentedMp4() {
        byte[] annex = start(new byte[] {0x67, 0x42, 0x00, 0x1f});
        annex = join(annex, start(new byte[] {0x68, (byte) 0xce}));
        annex = join(annex, start(new byte[] {0x65, (byte) 0x88}));
        annex = join(annex, start(new byte[] {0x41, 0x00}));
        byte[] mp4 = new H264Fmp4(720, 1280).push(annex);
        String head = new String(mp4, 4, 4, StandardCharsets.US_ASCII);
        assertTrue(mp4.length > 8, "empty mp4");
        assertTrue("ftyp".equals(head), head);
        assertTrue(indexOf(mp4, "moof".getBytes(StandardCharsets.US_ASCII)) > 0);
    }

    private static byte[] start(byte[] nal) {
        byte[] out = new byte[4 + nal.length];
        out[2] = 0;
        out[3] = 1;
        System.arraycopy(nal, 0, out, 4, nal.length);
        return out;
    }

    private static byte[] join(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        for (int i = 0; i + needle.length <= hay.length; i++) {
            boolean ok = true;
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) return i;
        }
        return -1;
    }
}
