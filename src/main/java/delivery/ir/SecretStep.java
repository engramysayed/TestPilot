package delivery.ir;

import delivery.settle.SettlePolicy.Action;
import org.json.JSONObject;
import org.openqa.selenium.WebElement;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * IR step flag {@code "secret": true} (r2, spec in patches/r2/SPEC.md).
 * Set when the target is a password / masked / OTP field, or the value comes from a secret or credential.
 * Effects: settle kind typeSecret; A9 evidence masks the value; logs redact it.
 */
public final class SecretStep {
    public static final String MASK = "\u2022\u2022\u2022";

    /** Target facts, all nullable. textSecurity = computed -webkit-text-security (disc/circle/square = masked). */
    public record Target(String tag, String type, String autocomplete, String name, String id, String ariaLabel,
                         String placeholder, String inputMode, String textSecurity, Integer maxLength) {
        public static Target ofType(String type) { return new Target("input", type, null, null, null, null, null, null, null, null); }
    }

    static final Pattern OTP_WORDS = Pattern.compile("(?i)(\\botp\\b|one[-_ ]?time|passcode|pass[-_ ]?code|\\bpin\\b|"
            + "\\bcvv\\b|\\bcvc\\b|\\bcsc\\b|security[-_ ]?code|verification[-_ ]?code|auth(entication)?[-_ ]?code|"
            + "\\b2fa\\b|\\bmfa\\b|\\btotp\\b|\\btan\\b|sms[-_ ]?code|password|passwort|kennwort|mot de passe)");
    static final Pattern SECRET_PLACEHOLDER = Pattern.compile("(?i)\\$\\{[^}]*(password|passwd|pwd|secret|token|api_?key|"
            + "otp|pin|credential|private)[^}]*}");

    private SecretStep() {}

    public static boolean isSecretTarget(Target t) {
        if (t == null) return false;
        String type = lc(t.type()), ac = lc(t.autocomplete()), sec = lc(t.textSecurity());
        if ("password".equals(type)) return true;
        if (ac.contains("current-password") || ac.contains("new-password") || ac.contains("one-time-code")
                || ac.matches(".*\\bcc-(csc|number)\\b.*")) return true;
        if (!sec.isEmpty() && !"none".equals(sec)) return true;
        String words = String.join(" ", nz(t.name()), nz(t.id()), nz(t.ariaLabel()), nz(t.placeholder()))
                .replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("[_.\\-]+", " ");
        return OTP_WORDS.matcher(words).find();
    }

    /** Value is a secret reference (${TARGET_PASSWORD}, ${OTP}, ...) or equals a known credential. */
    public static boolean isSecretValue(String value, Collection<String> knownSecrets) {
        if (value == null || value.isEmpty()) return false;
        if (SECRET_PLACEHOLDER.matcher(value).find()) return true;
        if (knownSecrets != null) for (String k : knownSecrets) if (k != null && !k.isEmpty() && k.equals(value)) return true;
        return false;
    }

    public static boolean isSecret(Target t, String value, Collection<String> knownSecrets) {
        return isSecretTarget(t) || isSecretValue(value, knownSecrets);
    }

    /** Reads the facts from a live element (never its value). */
    public static Target fromElement(WebElement e) {
        String ml = attr(e, "maxlength");
        Integer max = null;
        try { if (!ml.isEmpty()) max = Integer.parseInt(ml.trim()); } catch (NumberFormatException ignored) { }
        String sec;
        try { sec = e.getCssValue("-webkit-text-security"); } catch (RuntimeException x) { sec = ""; }
        return new Target(e.getTagName(), attr(e, "type"), attr(e, "autocomplete"), attr(e, "name"), attr(e, "id"),
                attr(e, "aria-label"), attr(e, "placeholder"), attr(e, "inputmode"), sec, max);
    }

    /** Settle kind for a step: a secret type/clear settles as typeSecret (no visible change to wait for). */
    public static Action settleKind(Action a, boolean secret) {
        return secret && (a == Action.TYPE || a == Action.CLEAR) ? Action.TYPE_SECRET : a;
    }

    /** IR read rule: absent/null = false; anything but a JSON boolean is rejected. */
    public static boolean read(JSONObject step) {
        Object v = step.opt("secret");
        if (v == null || v == JSONObject.NULL) return false;
        if (v instanceof Boolean b) return b;
        throw new IllegalArgumentException("IR step 'secret' must be a boolean, got " + v);
    }

    /** Value as it may appear in a log line. */
    public static String logValue(String value, boolean secret) {
        return secret ? MASK : value;
    }

    private static String attr(WebElement e, String n) {
        try { String v = e.getDomAttribute(n); return v == null ? "" : v; } catch (RuntimeException x) { return ""; }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String lc(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
