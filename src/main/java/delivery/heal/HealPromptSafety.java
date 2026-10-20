package delivery.heal;

import delivery.authoring.StepIntentBinder;
import delivery.settle.SettlePolicy;
import delivery.settle.SideEffectClassifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * P0: structured safety text for Cursor heal/solve/invent prompts (secret, sideEffects, banned
 * locators, abort-on-crash). Pure helpers — unit-tested with golden snippets.
 */
public final class HealPromptSafety {
    private HealPromptSafety() {}

    static final Pattern CRASH = Pattern.compile(
            "(?i)(fatal exception|application error|aw\\s*snap|err_name_not_resolved|"
                    + "chrome-error://|page crashed|renderer crashed|net::ERR_CONNECTION_REFUSED|"
                    + "\\b500\\b internal|\\b502\\b|\\b503\\b|stack trace:)",
            Pattern.MULTILINE);

    /** Intent looks like a password / OTP / secret field. */
    public static boolean isSecretIntent(StepIntentBinder.IntentLine intent) {
        if (intent == null) return false;
        if (intent.kind() == StepIntentBinder.IntentKind.TYPE_PASS) return true;
        String t = intent.text() == null ? "" : intent.text().toLowerCase(Locale.ROOT);
        return t.contains("password") || t.contains("otp") || t.contains("one-time")
                || t.contains("passcode") || t.contains("secret");
    }

    /** Conservative table default for the intent's action kind (no DOM target). */
    public static SettlePolicy.SideEffects sideEffectsOf(StepIntentBinder.IntentLine intent) {
        if (intent == null) return SettlePolicy.SideEffects.UNKNOWN;
        SettlePolicy.Action a = switch (intent.kind()) {
            case ASSERT_VISIBLE -> SettlePolicy.Action.ASSERT;
            case TYPE_USER, TYPE_PASS, TYPE_FIELD -> SettlePolicy.Action.TYPE;
            case CLICK_LOGIN, CLICK -> SettlePolicy.Action.CLICK;
            case HOVER -> SettlePolicy.Action.HOVER;
            case DOUBLE_CLICK -> SettlePolicy.Action.DOUBLE_CLICK;
            case RIGHT_CLICK -> SettlePolicy.Action.RIGHT_CLICK;
            case DRAG -> SettlePolicy.Action.DRAG;
        };
        return SideEffectClassifier.classify(SettlePolicy.builtin(), a, null);
    }

    public static boolean looksLikeCrash(String failureReason, String slimHtml) {
        String blob = (failureReason == null ? "" : failureReason) + "\n" + (slimHtml == null ? "" : slimHtml);
        return CRASH.matcher(blob).find();
    }

    public static List<String> formatBanned(List<FailedLocator> failed) {
        List<String> out = new ArrayList<>();
        if (failed == null) return out;
        for (FailedLocator f : failed) {
            if (f == null || f.value() == null || f.value().isBlank()) continue;
            String line = (f.strategy() == null ? "" : f.strategy()) + ": " + f.value();
            if (f.errorSummary() != null && !f.errorSummary().isBlank()) {
                line += " → " + trim(f.errorSummary(), 80);
            }
            out.add(line);
        }
        return out;
    }

    /**
     * Markdown block appended into heal.mjs prompts (and usable as a Java-side failureReason prefix).
     * Values for secrets are never included — only the flag.
     */
    public static String safetyBlock(boolean secret, SettlePolicy.SideEffects se, List<String> banned,
                                     boolean crash) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Safety (must obey)\n");
        if (crash) {
            sb.append("- CRASH/ERROR PAGE DETECTED. Return ONLY ")
                    .append("{\"mode\":\"abort\",\"thought\":\"crash or fatal error page — do not invent locators\"}")
                    .append(" — do not pick, invent, or recover.\n");
        }
        sb.append("- secret=").append(secret ? "true" : "false");
        if (secret) {
            sb.append(": NEVER echo, print, or invent real secret/password/OTP values; ")
                    .append("use empty value or ${TARGET_PASSWORD}/${TARGET_OTP} tokens only.\n");
        } else {
            sb.append(".\n");
        }
        String wire = se == null ? SettlePolicy.SideEffects.UNKNOWN.wire : se.wire;
        sb.append("- sideEffects=").append(wire);
        if (se == SettlePolicy.SideEffects.NONE) {
            sb.append(": read-only — recovery retries on the same control are allowed.\n");
        } else {
            sb.append(": do NOT invent mutating retries (no re-submit/delete/pay/logout). ")
                    .append("Recovery may only clear/dismiss or navigate to the Excel open-path. ")
                    .append("Mark invented steps with \"sideEffects\":\"").append(wire).append("\".\n");
        }
        if (banned != null && !banned.isEmpty()) {
            sb.append("- Banned locators (already failed — do not return these):\n");
            for (String b : banned) sb.append("  - ").append(b).append('\n');
        } else {
            sb.append("- Banned locators: (none)\n");
        }
        sb.append("- Stop: if you cannot decide safely, return empty candidateId / empty steps ")
                .append("(or mode=abort on crash) rather than guessing.\n");
        return sb.toString();
    }

    public static String trim(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
