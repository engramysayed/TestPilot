package delivery.settle;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * r3: sha256 pins are defined on the LF bytes. A Windows checkout with core.autocrlf=true (and no
 * .gitattributes eol=lf) turns them into CRLF; that is not a content change. Such a file passes with a
 * warning (or fails with a clear message under -Dkeel.pins.strict-eol=true); any other difference fails
 * and explains the v1 freeze rule.
 */
public final class LfPins {
    private LfPins() {}

    public static String sha(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] lf(byte[] b) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(b.length);
        for (int i = 0; i < b.length; i++) if (!(b[i] == '\r' && i + 1 < b.length && b[i + 1] == '\n')) out.write(b[i]);
        return out.toByteArray();
    }

    /** Returns null when the pin holds, else the failure message. */
    public static String check(String name, byte[] raw, String pin) {
        if (sha(raw).equals(pin)) return null;
        if (sha(lf(raw)).equals(pin)) {
            String msg = name + " has CRLF line endings (checkout converted it: core.autocrlf). Content matches the "
                    + "v1 pin after CRLF->LF; add '.gitattributes' eol=lf for the settle files (patches/r3/LF-FILES.txt) "
                    + "and re-checkout them.";
            if (Boolean.getBoolean("keel.pins.strict-eol")) return msg;
            System.err.println("WARN " + msg);
            return null;
        }
        return name + " does not match its frozen settle-policy v1 sha256 (" + pin + ", got " + sha(raw)
                + " / LF-normalized " + sha(lf(raw)) + "). v1 is FROZEN: a change needs \"version\": 2, new pins on both "
                + "sides and an announcement to Codegen (r2 SPEC section 1).";
    }

    public static void assertPinned(String name, byte[] raw, String pin) {
        String m = check(name, raw, pin);
        if (m != null) throw new AssertionError(m);
    }
}
