package delivery.hunt;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves hunt credential tokens at execute time. Password never appears in planner prompts.
 */
public final class HuntSecretResolver {
    private static final Pattern OTP_NEAR = Pattern.compile(
            "(?i)otp[^0-9]{0,24}(\\d{4,8})|(\\d{4,8})[^0-9]{0,12}otp");
    private static final Pattern ANY_OTP_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    private final String username;
    private final String password;
    private final String otp;

    public HuntSecretResolver(String username, String password, String otp) {
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.otp = otp == null ? "" : otp;
    }

    /** Hunt resolver — OTP only when explicitly configured (not mined from user story). */
    public static HuntSecretResolver of(String username, String password, String explicitOtp) {
        String otp = explicitOtp == null ? "" : explicitOtp.trim();
        return new HuntSecretResolver(username, password, otp);
    }

    /** Story mining retained for non-hunt callers / tests only. */
    public static HuntSecretResolver ofWithStoryOtp(String username, String password, String userStory) {
        return new HuntSecretResolver(username, password, otpHintFromStory(userStory));
    }

    public static String otpHintFromStory(String userStory) {
        if (userStory == null || userStory.isBlank()) {
            return "";
        }
        Matcher near = OTP_NEAR.matcher(userStory);
        if (near.find()) {
            String a = near.group(1);
            String b = near.group(2);
            return a != null ? a : (b == null ? "" : b);
        }
        Matcher six = ANY_OTP_DIGITS.matcher(userStory);
        if (six.find()) {
            return six.group(1);
        }
        return "";
    }

    public boolean hasUsername() {
        return !username.isBlank();
    }

    public String username() {
        return username;
    }

    public String otp() {
        return otp;
    }

    public boolean hasOtp() {
        return !otp.isBlank();
    }

    /** True when the raw value references a secret token (for logging without resolving). */
    public static boolean containsToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String s = raw.toUpperCase(Locale.ROOT);
        return s.contains("TARGET_USERNAME") || s.contains("TARGET_PASSWORD") || s.contains("TARGET_OTP");
    }

    public String resolve(String raw) {
        if (raw == null) {
            return "";
        }
        String out = raw;
        out = replaceAllAliases(out, "TARGET_USERNAME", username);
        out = replaceAllAliases(out, "TARGET_PASSWORD", password);
        out = replaceAllAliases(out, "TARGET_OTP", otp);
        return out;
    }

    private static String replaceAllAliases(String raw, String name, String value) {
        String v = value == null ? "" : value;
        String out = raw;
        out = out.replace("${" + name + "}", v);
        out = out.replace("$" + name, v);
        out = out.replace("{{" + name + "}}", v);
        // case-insensitive simple forms
        out = out.replaceAll("(?i)\\$\\{" + name + "\\}", Matcher.quoteReplacement(v));
        out = out.replaceAll("(?i)\\{\\{" + name + "\\}\\}", Matcher.quoteReplacement(v));
        return out;
    }
}
