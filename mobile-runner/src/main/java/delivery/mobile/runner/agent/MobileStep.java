package delivery.mobile.runner.agent;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One interpreted test-case line for a native Android run (English or Arabic).
 *
 * <pre>
 *   Tap the "Login" button            → TAP     target=Login
 *   Enter "sara@x.com" in the Email field / testData → TYPE target=Email value=…
 *   Verify "Welcome" is displayed     → ASSERT_VISIBLE target=Welcome
 *   اضغط على زر "دخول"                 → TAP     target=دخول
 *   Go back / ارجع                     → BACK
 *   Swipe up / Scroll down / اسحب للأعلى → SWIPE direction
 *   Launch the app / افتح التطبيق        → LAUNCH
 * </pre>
 */
public record MobileStep(Kind kind, String target, String value, String direction, String raw) {

    public enum Kind { TAP, TYPE, CLEAR, ASSERT_VISIBLE, ASSERT_NOT_VISIBLE, BACK, SWIPE, LAUNCH, SKIP }

    private static final Pattern QUOTED = Pattern.compile("[\"'“”«»‘’]([^\"'“”«»‘’]{1,120})[\"'“”«»‘’]");

    public static MobileStep parse(String line, String testDataLine) {
        String raw = line == null ? "" : line.trim();
        if (raw.isEmpty()) {
            return new MobileStep(Kind.SKIP, "", "", "", raw);
        }
        String n = normalize(raw);
        List<String> quoted = quoted(raw);
        String data = testDataLine == null ? "" : testDataLine.trim();

        if (has(n, "go back", "press back", "navigate back", "ارجع", "رجوع", "زر الرجوع")) {
            return new MobileStep(Kind.BACK, "", "", "", raw);
        }
        if (has(n, "launch the app", "open the app", "start the app", "افتح التطبيق", "شغل التطبيق")) {
            return new MobileStep(Kind.LAUNCH, "", "", "", raw);
        }
        if (has(n, "swipe", "scroll", "اسحب", "مرر الشاشه")) {
            String dir = n.matches(".*\\bup\\b.*") || has(n, "للاعلي", "لاعلي", "فوق") ? "UP"
                    : n.matches(".*\\bleft\\b.*") || has(n, "لليسار", "يسار") ? "LEFT"
                    : n.matches(".*\\bright\\b.*") || has(n, "لليمين", "يمين") ? "RIGHT" : "DOWN";
            return new MobileStep(Kind.SWIPE, "", "", dir, raw);
        }
        boolean assertNot = has(n, "not visible", "not displayed", "not shown", "is hidden", "لا يظهر", "غير ظاهر", "اختفاء");
        if (assertNot || has(n, "verify", "confirm", "check that", "should see", "is displayed", "is visible", "is shown",
                "appears", "تحقق", "تاكد", "يظهر", "تظهر", "ظهور")) {
            String target = !quoted.isEmpty() ? quoted.get(0) : stripFillers(n);
            return new MobileStep(assertNot ? Kind.ASSERT_NOT_VISIBLE : Kind.ASSERT_VISIBLE, target, "", "", raw);
        }
        if (has(n, "clear the", "امسح")) {
            return new MobileStep(Kind.CLEAR, !quoted.isEmpty() ? quoted.get(0) : fieldName(n), "", "", raw);
        }
        if (has(n, "enter", "type", "fill", "input", "ادخل", "اكتب", "املا", "عبئ")) {
            // Enter "value" in the Email field   |   Enter in the Email field (value from TestData)
            String value = data;
            String target;
            if (quoted.size() >= 2) {
                value = quoted.get(0);
                target = quoted.get(1);
            } else if (quoted.size() == 1 && !data.isEmpty()) {
                target = quoted.get(0);
            } else if (quoted.size() == 1) {
                value = quoted.get(0);
                target = fieldName(n.replace(normalize(quoted.get(0)), " "));
            } else {
                target = fieldName(n);
            }
            return new MobileStep(Kind.TYPE, target, value, "", raw);
        }
        if (has(n, "tap", "click", "press", "select", "choose", "open", "اضغط", "انقر", "اختر", "حدد", "افتح")) {
            return new MobileStep(Kind.TAP, !quoted.isEmpty() ? quoted.get(0) : stripFillers(n), "", "", raw);
        }
        return new MobileStep(Kind.SKIP, "", "", "", raw);
    }

    static List<String> quoted(String raw) {
        Matcher m = QUOTED.matcher(raw);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }

    /** "in the Email field" / "into Password" / "في حقل البريد" → Email / Password / البريد */
    static String fieldName(String n) {
        Matcher m = Pattern.compile("(?:in|into|on)\\s+(?:the\\s+)?(.+?)(?:\\s+(?:field|box|input|textbox))?$").matcher(n);
        if (m.find()) {
            return m.group(1).trim();
        }
        Matcher ar = Pattern.compile("(?:في|داخل)\\s+(?:حقل|خانه)?\\s*(.+)$").matcher(n);
        if (ar.find()) {
            return ar.group(1).trim();
        }
        return stripFillers(n);
    }

    /** Remove verbs and filler words, keep the control's name. */
    static String stripFillers(String n) {
        String s = " " + n + " ";
        for (String w : new String[]{"tap", "click", "press", "select", "choose", "open", "verify", "confirm", "check that",
                "that", "the", "on", "button", "link", "tab", "icon", "is displayed", "is visible", "is shown", "appears",
                "should see", "اضغط", "انقر", "اختر", "حدد", "افتح", "علي", "على", "زر", "تحقق", "تاكد", "من", "يظهر", "تظهر",
                "ظهور", "ايقونه"}) {
            s = s.replace(" " + w + " ", " ");
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Whole-word for English needles ("enter" ≠ "center"); substring for Arabic (attached clitics). */
    static boolean has(String n, String... needles) {
        for (String x : needles) {
            String k = normalize(x);
            if (k.isEmpty()) {
                continue;
            }
            boolean ascii = k.chars().allMatch(c -> c < 128);
            if (ascii) {
                if (Pattern.compile("(^|[^\\p{L}\\p{N}])" + Pattern.quote(k) + "([^\\p{L}\\p{N}]|$)").matcher(n).find()) {
                    return true;
                }
            } else if (n.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /** Lowercase, NFKC, Arabic letter unification (أإآ→ا, ة→ه, ى→ي), tashkeel removed. */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
                .replace('ة', 'ه').replace('ى', 'ي');
        return n.replaceAll("\\s+", " ").trim();
    }
}
