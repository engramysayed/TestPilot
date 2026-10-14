package delivery.settle;

import delivery.settle.SettlePolicy.Action;
import delivery.settle.SettlePolicy.SideEffects;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Engine side of IR {@code sideEffects} (Codegen r1/05): classify a step from its action kind (A8 table
 * default) refined by the target's semantics. Conservative: anything not provably read-only stays UNKNOWN
 * (treated as MUTATING); a mutating word beats every read-only signal. Assertion steps are always NONE.
 */
public final class SideEffectClassifier {

    /** What we know about the bound element. All fields nullable. */
    public record Target(String tag, String role, String type, String name, String href,
                         boolean hasPopup, boolean inForm, String formMethod) {
        public static Target of(String tag, String role, String type, String name) {
            return new Target(tag, role, type, name, null, false, false, null);
        }
    }

    static final Pattern MUTATING = Pattern.compile("(?i)\\b(save|submit|create|add|new|update|edit|apply|delete|remove|"
            + "destroy|discard|archive|pay|purchase|buy|checkout|check out|order|place|book|send|post|publish|share|invite|"
            + "transfer|confirm|approve|reject|accept|decline|register|sign ?up|subscribe|unsubscribe|upload|import|"
            + "reset|logout|log ?out|sign ?out|cancel subscription|close account|deactivate|enable|disable|toggle|"
            + "speichern|l\u00f6schen|senden|bezahlen|abmelden)\\b");
    static final Pattern READ_ONLY = Pattern.compile("(?i)\\b(open|show|view|details?|more|expand|collapse|menu|tab|"
            + "sort|sorted|filter|search|find|next|previous|prev|page|back|home|close|cancel|help|preview|zoom|"
            + "refresh|go to|navigate|learn more|see all|select all rows)\\b");
    static final Pattern READ_ONLY_ROLES = Pattern.compile("(?i)tab|menuitem|link|treeitem|option|columnheader|"
            + "searchbox|combobox|listbox|switchtab|navigation");

    private SideEffectClassifier() {}

    public static SideEffects classify(SettlePolicy policy, Action action, Target t) {
        if (action == Action.ASSERT || action == Action.WAIT) return SideEffects.NONE;
        SideEffects base = policy.rule(action).sideEffects();
        if (base == SideEffects.MUTATING) return base;
        String name = t == null || t.name() == null ? "" : t.name().trim();
        if (!name.isEmpty() && MUTATING.matcher(name).find()) return SideEffects.MUTATING;
        if (t != null && t.href() != null && t.href().toLowerCase(Locale.ROOT).matches(".*(logout|signout|sign-out|delete).*"))
            return SideEffects.MUTATING;
        if (base == SideEffects.NONE) return base;
        if (t == null) return SideEffects.UNKNOWN;
        String tag = lc(t.tag()), type = lc(t.type()), role = lc(t.role());
        boolean pressLike = action == Action.CLICK || action == Action.TAP || action == Action.PRESS
                || action == Action.DOUBLE_CLICK;
        // implicit form submission: <button> defaults to type=submit inside a form; non-GET forms mutate
        if (pressLike && ("submit".equals(type) || ("button".equals(tag) && type.isEmpty() && t.inForm())))
            return t.formMethod() != null && t.formMethod().equalsIgnoreCase("get") ? SideEffects.NONE : SideEffects.MUTATING;
        if (pressLike && t.hasPopup()) return SideEffects.NONE;
        if (pressLike && "a".equals(tag) && t.href() != null && !t.href().isBlank() && !t.inForm()) return SideEffects.NONE;
        if (!role.isEmpty() && READ_ONLY_ROLES.matcher(role).matches() && !t.inForm()) return SideEffects.NONE;
        if ("search".equals(type) || "searchbox".equals(role)) return SideEffects.NONE;
        if (!name.isEmpty() && READ_ONLY.matcher(name).find() && !t.inForm()) return SideEffects.NONE;
        return SideEffects.UNKNOWN;
    }

    /** IR emission rule: write the field only when known; UNKNOWN is expressed by omission. */
    public static void emit(org.json.JSONObject step, SideEffects se) {
        if (step.has("assert") || "assert".equals(step.optString("action"))) { step.remove("sideEffects"); return; }
        if (se == null || se == SideEffects.UNKNOWN) step.remove("sideEffects");
        else step.put("sideEffects", se.wire);
    }

    /** IR read rule: absent/null = UNKNOWN; anything outside the enum is rejected. */
    public static SideEffects read(org.json.JSONObject step) {
        if ("assert".equals(step.optString("action"))) return SideEffects.NONE;
        return SideEffects.of(step.opt("sideEffects"));
    }

    private static String lc(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
