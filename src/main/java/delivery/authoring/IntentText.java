package delivery.authoring;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unicode-aware text handling for step ↔ element matching (English, Arabic, accented Latin).
 * <p>
 * Before this class every matcher split on {@code [^a-z0-9]}: an Arabic step without a quoted literal
 * produced zero tokens, every candidate was dropped (no intent signal) and the step fell to LLM / heal /
 * TODO. Substring checks also produced false hits ("add" ⊂ "Address").
 * <ul>
 *   <li>{@link #normalize}: NFKC, lowercase, Arabic tashkeel/tatweel removed, alef/teh-marbuta/yeh unified,
 *       Arabic-Indic digits → ASCII.</li>
 *   <li>{@link #tokens}: splits on non-letter/non-digit (any script), drops stop words (EN + AR), strips the
 *       Arabic article ال and its clitic forms.</li>
 *   <li>{@link #containsToken}: whole-word match that still sees camelCase / kebab / snake compounds.</li>
 *   <li>{@link #annotateArabicVerbs}: appends the English verb keywords that intent classification uses.</li>
 * </ul>
 */
public final class IntentText {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern TASHKEEL = Pattern.compile("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]");
    private static final Pattern ARABIC = Pattern.compile("[\\u0600-\\u06FF]");

    public static final Set<String> STOP = Set.of(
            // English
            "the", "and", "for", "with", "from", "that", "this", "into", "only",
            "click", "open", "confirm", "verify", "shown", "visible", "page",
            "user", "already", "logged", "enter", "type", "fill", "button",
            "item", "was", "are", "has", "have", "select", "choose", "pick",
            "clicks", "sidebar", "toolbar", "menu",
            // Arabic (normalized forms)
            "اضغط", "انقر", "اختر", "ادخل", "اكتب", "املا", "تحقق", "تاكد", "يظهر", "ظهور", "عرض", "يتم",
            "الصفحه", "صفحه", "زر", "الزر", "على", "علي", "في", "من", "الى", "الي", "ان", "او", "ثم", "مع",
            "قم", "بالضغط", "الضغط", "النقر", "المستخدم", "حقل", "الحقل", "خانه", "الخانه", "يجب", "هو", "هي",
            "رساله", "الرساله", "قائمه", "القائمه");

    private IntentText() {
    }

    public static boolean hasArabic(String s) {
        return s != null && ARABIC.matcher(s).find();
    }

    public static String normalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (hasArabic(n)) {
            n = TASHKEEL.matcher(n).replaceAll("");
            StringBuilder sb = new StringBuilder(n.length());
            for (int i = 0; i < n.length(); i++) {
                char c = n.charAt(i);
                switch (c) {
                    case 'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا');
                    case 'ة' -> sb.append('ه');
                    case 'ى' -> sb.append('ي');
                    case 'ؤ' -> sb.append('و');
                    case 'ئ' -> sb.append('ي');
                    default -> {
                        if (c >= '٠' && c <= '٩') {
                            sb.append((char) ('0' + (c - '٠')));
                        } else if (c >= '۰' && c <= '۹') {
                            sb.append((char) ('0' + (c - '۰')));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            n = sb.toString();
        }
        return n.replaceAll("\\s+", " ").trim();
    }

    /** Arabic article / clitics: الحفظ → حفظ, والحفظ → حفظ, بالحفظ → حفظ, للحفظ → حفظ. */
    static String stripArabicArticle(String t) {
        if (t == null || t.length() < 4 || !hasArabic(t)) {
            return t;
        }
        for (String p : new String[]{"وال", "بال", "كال", "فال", "لل", "ال"}) {
            if (t.startsWith(p) && t.length() - p.length() >= 2) {
                return t.substring(p.length());
            }
        }
        return t;
    }

    /** Content tokens: Latin ≥ 3 chars, Arabic ≥ 2 chars (words are shorter), stop words removed. */
    public static List<String> tokens(String text) {
        return tokens(text, 3);
    }

    public static List<String> tokens(String text, int minLatin) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String raw : NON_WORD.split(normalize(text))) {
            if (raw.isEmpty()) {
                continue;
            }
            boolean arabic = hasArabic(raw);
            if (raw.length() < (arabic ? 2 : minLatin)) {
                continue;
            }
            if (STOP.contains(raw)) {
                continue;
            }
            String t = arabic ? stripArabicArticle(raw) : raw;
            if (STOP.contains(t)) {
                continue;
            }
            if (!out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * Whole-word containment in any script. Also matches inside camelCase / kebab / snake compounds of
     * locator values ({@code placeOrder}, {@code place-order}) but never as a prefix of a longer word
     * ({@code add} does not match {@code address}). Arabic words also match with an attached article.
     */
    public static boolean containsToken(String hay, String token) {
        if (hay == null || token == null || token.isBlank()) {
            return false;
        }
        String t = normalize(token);
        if (t.isEmpty()) {
            return false;
        }
        String spaced = hay
                .replaceAll("([\\p{Ll}\\p{N}])(\\p{Lu})", "$1 $2")
                .replace('_', ' ').replace('-', ' ');
        for (String w : NON_WORD.split(normalize(spaced))) {
            if (w.equals(t)) {
                return true;
            }
            if (hasArabic(w) && stripArabicArticle(w).equals(t)) {
                return true;
            }
        }
        return false;
    }

    /** Phrase (one or more words) present on word boundaries. */
    public static boolean containsPhrase(String hay, String phrase) {
        if (hay == null || phrase == null || phrase.isBlank()) {
            return false;
        }
        String h = " " + String.join(" ", NON_WORD.split(normalize(hay))) + " ";
        String p = " " + String.join(" ", NON_WORD.split(normalize(phrase))).trim() + " ";
        return p.trim().length() > 0 && h.contains(p);
    }

    // ------------------------------------------------------------------ Arabic verbs → classifier keywords

    private static final Map<String, String> AR_VERBS = new LinkedHashMap<>();

    static {
        // order matters: more specific phrases first
        AR_VERBS.put("نقر مزدوج", " double click ");
        AR_VERBS.put("نقره مزدوجه", " double click ");
        AR_VERBS.put("نقر يمين", " right click ");
        AR_VERBS.put("زر الفاره الايمن", " right click ");
        AR_VERBS.put("تسجيل الدخول", " login ");
        AR_VERBS.put("اسم المستخدم", " username ");
        AR_VERBS.put("كلمه المرور", " password ");
        AR_VERBS.put("كلمه السر", " password ");
        AR_VERBS.put("الرقم السري", " password ");
        AR_VERBS.put("البريد الالكتروني", " email ");
        AR_VERBS.put("مرر", " hover over ");
        AR_VERBS.put("حرك الماوس", " hover over ");
        AR_VERBS.put("اسحب", " drag ");
        AR_VERBS.put("امسح", " clear the field ");
        AR_VERBS.put("اترك", " leave empty ");
        AR_VERBS.put("ادخل", " enter ");
        AR_VERBS.put("اكتب", " type ");
        AR_VERBS.put("املا", " fill ");
        AR_VERBS.put("عبئ", " fill ");
        AR_VERBS.put("اختر", " select ");
        AR_VERBS.put("حدد", " select ");
        AR_VERBS.put("تحقق", " verify ");
        AR_VERBS.put("تاكد", " verify ");
        AR_VERBS.put("يظهر", " is shown ");
        AR_VERBS.put("تظهر", " is shown ");
        AR_VERBS.put("ظاهر", " visible ");
        AR_VERBS.put("مرئي", " visible ");
        AR_VERBS.put("ارسال", " submit ");
        AR_VERBS.put("اضغط", " click ");
        AR_VERBS.put("انقر", " click ");
        AR_VERBS.put("بالضغط", " click ");
        AR_VERBS.put("افتح", " open ");
        AR_VERBS.put("اذهب الي", " go to ");
        AR_VERBS.put("انتقل الي", " go to ");
        AR_VERBS.put("زر", " button ");
    }

    /**
     * For Arabic steps, append the English keywords {@code StepIntentBinder.classify} keys on
     * ("اضغط على زر حفظ" → "… click button"). English text is returned unchanged.
     */
    public static String annotateArabicVerbs(String lower) {
        if (lower == null || !hasArabic(lower)) {
            return lower;
        }
        String n = normalize(lower);
        StringBuilder extra = new StringBuilder();
        for (Map.Entry<String, String> e : AR_VERBS.entrySet()) {
            if (n.contains(e.getKey())) {
                extra.append(e.getValue());
            }
        }
        if (extra.isEmpty()) {
            return lower;
        }
        // Verbs first so startsWith("select ") / ("hover") rules still fire.
        return extra.toString().trim() + " " + lower;
    }
}
