package delivery.mobile.runner.action;

import java.text.Normalizer;

/**
 * TYPE text handling (arch §6): Unicode-capable path only for non-ASCII; {@code adb input text} forbidden
 * for non-ASCII; read-back with NFC except secure/password fields.
 */
public final class TypeInputPolicy {
    public enum Path { UNICODE_ELEMENT, ASCII_ELEMENT }

    private TypeInputPolicy() {}

    public static boolean isNonAscii(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) return true;
        }
        return false;
    }

    public static Path pathFor(String value) {
        return isNonAscii(value) ? Path.UNICODE_ELEMENT : Path.ASCII_ELEMENT;
    }

    /** {@code adb input text} is never legal for non-ASCII (and discouraged generally). */
    public static boolean adbInputTextAllowed(String value) {
        return false; // never use adb input text from mobile-runner TYPE path
    }

    public static boolean adbInputTextAllowedForNonAscii(String value) {
        return !isNonAscii(value) && false; // still false — explicit API for tests
    }

    public static String nfc(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    public static boolean readBackMatches(String expected, String observed, boolean secureField) {
        if (secureField) return true; // skip read-back
        return nfc(expected).equals(nfc(observed));
    }
}
