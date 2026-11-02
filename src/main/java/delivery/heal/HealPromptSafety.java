package delivery.heal;

import delivery.prompts.PromptCatalog;

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
        String crashBlock = "";
        if (crash) {
            crashBlock = "- CRASH/ERROR PAGE DETECTED. Return ONLY "
                    + "{\"mode\":\"abort\",\"thought\":\"crash or fatal error page — do not invent locators\"}"
                    + " — do not pick, invent, or recover.\n";
        }
        String secretClause = secret
                ? ": NEVER echo, print, or invent real secret/password/OTP values; "
                  + "use empty value or ${TARGET_PASSWORD}/${TARGET_OTP} tokens only.\n"
                : ".\n";
        String wire = se == null ? SettlePolicy.SideEffects.UNKNOWN.wire : se.wire;
        String sideEffectsClause;
        if (se == SettlePolicy.SideEffects.NONE) {
            sideEffectsClause = ": read-only — recovery retries on the same control are allowed.\n";
        } else {
            sideEffectsClause = ": do NOT invent mutating retries (no re-submit/delete/pay/logout). "
                    + "Recovery may only clear/dismiss or navigate to the Excel open-path. "
                    + "Mark invented steps with \"sideEffects\":\"" + wire + "\".\n";
        }
        String bannedBlock;
        if (banned != null && !banned.isEmpty()) {
            StringBuilder bb = new StringBuilder();
            bb.append("- Banned locators (already failed — do not return these):\n");
            for (String b : banned) {
                bb.append("  - ").append(b).append('\n');
            }
            bannedBlock = bb.toString();
        } else {
            bannedBlock = "- Banned locators: (none)\n";
        }
        return PromptCatalog.format("heal/safety", java.util.Map.of(
                "crashBlock", crashBlock,
                "secretFlag", secret ? "true" : "false",
                "secretClause", secretClause,
                "sideEffectsWire", wire,
                "sideEffectsClause", sideEffectsClause,
                "bannedBlock", bannedBlock
        ));
    }

    public static String trim(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
