package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds empty interactive form controls on the live page and plans type/select/click steps
 * with invented dummy values. Site-agnostic — driven by HTML structure only.
 */
public final class RequiredControlFiller {
    private RequiredControlFiller() {
    }

    /**
     * @param onlyRequiredAttr when true, only HTML {@code required} (or aria-required) controls;
     *                         when false, also empty fillable controls inside forms / with stable identity.
     */
    public static List<ProvenStep> planFills(String html, String tcId, boolean onlyRequiredAttr) {
        return planFills(html, tcId, onlyRequiredAttr, List.of());
    }

    /**
     * @param typeIntents optional TYPE_* intents with TestData — concrete values win over invent.
     */
    public static List<ProvenStep> planFills(
            String html, String tcId, boolean onlyRequiredAttr,
            List<StepIntentBinder.IntentLine> typeIntents) {
        return planFills(html, tcId, onlyRequiredAttr, typeIntents, List.of());
    }

    /**
     * @param spent already typed/selected controls this TC — never re-fill the same field
     *              (including preferred-hook vs id twins of one element).
     */
    public static List<ProvenStep> planFills(
            String html, String tcId, boolean onlyRequiredAttr,
            List<StepIntentBinder.IntentLine> typeIntents,
            List<ProvenStep> spent) {
        List<ProvenStep> out = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return out;
        }
        Document doc = Jsoup.parse(html);
        Set<String> seen = new HashSet<>();
        Set<String> handledRadioGroups = new HashSet<>();
        List<StepIntentBinder.IntentLine> dataIntents = typeIntents == null ? List.of() : typeIntents;
        List<ProvenStep> spentSteps = spent == null ? List.of() : spent;

        Elements controls = doc.select("input, select, textarea");
        for (Element el : controls) {
            if (shouldSkip(el)) {
                continue;
            }
            if (elementMatchesSpent(el, spentSteps)) {
                continue;
            }
            boolean required = el.hasAttr("required")
                    || "true".equalsIgnoreCase(el.attr("aria-required"));
            if (onlyRequiredAttr && !required) {
                continue;
            }
            if (!onlyRequiredAttr && !required && !isFillCandidate(el)) {
                continue;
            }
            if (!isEmpty(el)) {
                continue;
            }

            if (isLeaveEmptyTarget(el, labelOf(el), dataIntents)) {
                continue;
            }

            Locator loc = locatorOf(el);
            if (loc == null || !seen.add(loc.strategy + ":" + loc.value)) {
                continue;
            }

            String type = el.attr("type").toLowerCase(Locale.ROOT);
            String tag = el.tagName().toLowerCase(Locale.ROOT);

            if ("radio".equals(type)) {
                String group = el.hasAttr("name") ? el.attr("name") : loc.value;
                if (!handledRadioGroups.add(group)) {
                    continue;
                }
                Element pick = pickRadio(doc, group, el);
                Locator radioLoc = locatorOf(pick);
                if (radioLoc == null) {
                    continue;
                }
                out.add(new ProvenStep(tcId, "Page", "elementAction", "click",
                        radioLoc.strategy, radioLoc.value, "", "", "", true,
                        "auto-fill:radio"));
                continue;
            }

            if ("checkbox".equals(type)) {
                if (required || el.hasAttr("required")) {
                    out.add(new ProvenStep(tcId, "Page", "elementAction", "click",
                            loc.strategy, loc.value, "", "", "", true,
                            "auto-fill:checkbox"));
                }
                continue;
            }

            if ("select".equals(tag)) {
                String option = firstSelectableOption(el);
                if (option == null || option.isBlank()) {
                    option = valueForControl(el, tag, type, dataIntents);
                }
                if (option == null || option.isBlank()) {
                    continue;
                }
                out.add(new ProvenStep(tcId, "Page", "elementAction", "select",
                        loc.strategy, loc.value, option, "", "", true,
                        "auto-fill:select"));
                continue;
            }

            String value = valueForControl(el, tag, type, dataIntents);
            out.add(new ProvenStep(tcId, "Page", "elementAction", "type",
                    loc.strategy, loc.value, value, "", "", true,
                    "auto-fill:type"));
        }
        return out;
    }

    /** Fill empty fields only before submit/continue/sign-in — not before Verify/Submit OTP. */
    public static List<ProvenStep> planFillsBeforeClick(String html, String tcId, String clickIntentText) {
        return planFillsBeforeClick(html, tcId, clickIntentText, List.of(), List.of());
    }

    public static List<ProvenStep> planFillsBeforeClick(
            String html, String tcId, String clickIntentText,
            List<StepIntentBinder.IntentLine> typeIntents) {
        return planFillsBeforeClick(html, tcId, clickIntentText, typeIntents, List.of());
    }

    public static List<ProvenStep> planFillsBeforeClick(
            String html, String tcId, String clickIntentText,
            List<StepIntentBinder.IntentLine> typeIntents,
            List<ProvenStep> spent) {
        if (!looksLikeSubmit(clickIntentText)) {
            return List.of();
        }
        return planFills(html, tcId, false, typeIntents, spent);
    }

    static boolean looksLikeSubmit(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String s = text.toLowerCase(Locale.ROOT);
        // OTP verify/submit is not a form autofill gate — Excel often says "submit OTP".
        if (s.contains("otp") || s.contains("one-time") || s.contains("one time")
                || s.contains("mfa") || s.contains("2fa") || s.contains("totp")
                || s.contains("verification code")) {
            return false;
        }
        return s.contains("continue") || s.contains("submit") || s.contains("finish")
                || s.contains("next") || s.contains("save") || s.contains("place order")
                || s.contains("complete") || s.contains("confirm") || s.contains("checkout")
                || s.contains("pay") || s.contains("send") || s.contains("apply")
                || s.contains("proceed") || s.contains("register")
                || s.contains("create account") || s.contains("create new")
                || s.contains("sign up") || s.contains("signup")
                || s.contains("log in") || s.contains("login") || s.contains("sign in")
                || s.contains("signin");
    }

    /**
     * True when this DOM node is the same control a prior type/select already used —
     * matches preferred-hook locators against id/name/data-* on the element.
     */
    static boolean elementMatchesSpent(Element el, List<ProvenStep> spent) {
        if (el == null || spent == null || spent.isEmpty()) {
            return false;
        }
        Set<String> identities = elementIdentities(el);
        if (identities.isEmpty()) {
            return false;
        }
        for (ProvenStep step : spent) {
            if (step == null || step.action() == null) {
                continue;
            }
            String action = step.action().toLowerCase(Locale.ROOT);
            if (!("type".equals(action) || "select".equals(action) || "clear".equals(action))) {
                continue;
            }
            for (String token : locatorIdentities(step.locatorStrategy(), step.locatorValue())) {
                if (identities.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> elementIdentities(Element el) {
        Set<String> out = new HashSet<>();
        if (usableIdentifier(el.id())) {
            out.add("id:" + el.id().toLowerCase(Locale.ROOT));
        }
        if (usableIdentifier(el.attr("name"))) {
            out.add("name:" + el.attr("name").toLowerCase(Locale.ROOT));
        }
        for (org.jsoup.nodes.Attribute attr : el.attributes()) {
            String key = attr.getKey() == null ? "" : attr.getKey().toLowerCase(Locale.ROOT);
            if (!(key.startsWith("data-") && key.contains("test")) && !"data-qa".equals(key)) {
                continue;
            }
            String v = attr.getValue();
            if (usableIdentifier(v)) {
                out.add("attr:" + key + ":" + v.toLowerCase(Locale.ROOT));
                out.add("hook:" + v.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    static Set<String> locatorIdentities(String strategy, String value) {
        Set<String> out = new HashSet<>();
        if (value == null || value.isBlank()) {
            return out;
        }
        String s = strategy == null ? "" : strategy.toLowerCase(Locale.ROOT);
        String v = value.trim();
        if ("id".equals(s)) {
            out.add("id:" + v.toLowerCase(Locale.ROOT));
        } else if ("name".equals(s)) {
            out.add("name:" + v.toLowerCase(Locale.ROOT));
        } else if ("data-test".equals(s) || "data-testid".equals(s) || "data-qa".equals(s)) {
            out.add("attr:" + s + ":" + v.toLowerCase(Locale.ROOT));
            out.add("hook:" + v.toLowerCase(Locale.ROOT));
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\\[(data-[a-z0-9-]+)\\s*=\\s*['\"]([^'\"]+)['\"]\\]",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(v);
        while (m.find()) {
            String attr = m.group(1).toLowerCase(Locale.ROOT);
            String attrVal = m.group(2).toLowerCase(Locale.ROOT);
            out.add("attr:" + attr + ":" + attrVal);
            out.add("hook:" + attrVal);
        }
        java.util.regex.Matcher idM = java.util.regex.Pattern.compile(
                "(?:@id|\\bid)\\s*=\\s*['\"]([^'\"]+)['\"]",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(v);
        if (idM.find()) {
            out.add("id:" + idM.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static String valueForControl(
            Element el, String tag, String type, List<StepIntentBinder.IntentLine> typeIntents) {
        String label = labelOf(el);
        String column = matchTestData(el, label, typeIntents);
        if (column != null && !column.isBlank()) {
            return DummyValueInventor.fromStepOrInvent(
                    "Enter in the " + (label == null || label.isBlank() ? "field" : label) + " field",
                    column,
                    tag, type, el.attr("name"), label, el.attr("placeholder"));
        }
        return DummyValueInventor.invent(
                tag, type, el.attr("name"), label, el.attr("placeholder"),
                el.attr("maxlength"), "");
    }

    /**
     * Pick the best TYPE_* IntentLine TestData for this control by token overlap with
     * name / id / placeholder / label.
     */
    static String matchTestData(Element el, String label, List<StepIntentBinder.IntentLine> typeIntents) {
        if (typeIntents == null || typeIntents.isEmpty()) {
            return "";
        }
        String hay = (el.id() + " " + el.attr("name") + " " + el.attr("placeholder")
                + " " + el.attr("data-test") + " " + el.attr("data-testid")
                + " " + (label == null ? "" : label)).toLowerCase(Locale.ROOT);
        StepIntentBinder.IntentLine best = null;
        int bestScore = 0;
        for (StepIntentBinder.IntentLine intent : typeIntents) {
            if (intent == null || intent.testData() == null || intent.testData().isBlank()
                    || DummyValueInventor.looksLikeUnspecifiedValue(intent.testData())) {
                continue;
            }
            if (intent.kind() != StepIntentBinder.IntentKind.TYPE_FIELD
                    && intent.kind() != StepIntentBinder.IntentKind.TYPE_USER
                    && intent.kind() != StepIntentBinder.IntentKind.TYPE_PASS) {
                continue;
            }
            int score = tokenOverlap(intent.text(), hay);
            if (score > bestScore) {
                bestScore = score;
                best = intent;
            }
        }
        return best == null || bestScore <= 0 ? "" : best.testData().trim();
    }

    private static int tokenOverlap(String intentText, String hay) {
        if (intentText == null || intentText.isBlank() || hay == null) {
            return 0;
        }
        int score = 0;
        for (String token : intentText.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() < 3) {
                continue;
            }
            if ("enter".equals(token) || "type".equals(token) || "fill".equals(token)
                    || "input".equals(token) || "field".equals(token) || "the".equals(token)) {
                continue;
            }
            if (hay.contains(token)) {
                score++;
            }
        }
        return score;
    }

    /**
     * Skip auto-fill when a TYPE_* intent deliberately leaves this control empty
     * (leave/keep empty / blank TestData on leave-empty wording).
     */
    static boolean isLeaveEmptyTarget(
            Element el, String label, List<StepIntentBinder.IntentLine> typeIntents) {
        if (el == null || typeIntents == null || typeIntents.isEmpty()) {
            return false;
        }
        String hay = (el.id() + " " + el.attr("name") + " " + el.attr("placeholder")
                + " " + el.attr("data-test") + " " + el.attr("data-testid")
                + " " + (label == null ? "" : label)).toLowerCase(Locale.ROOT);
        for (StepIntentBinder.IntentLine intent : typeIntents) {
            if (intent == null) {
                continue;
            }
            if (intent.kind() != StepIntentBinder.IntentKind.TYPE_FIELD
                    && intent.kind() != StepIntentBinder.IntentKind.TYPE_USER
                    && intent.kind() != StepIntentBinder.IntentKind.TYPE_PASS) {
                continue;
            }
            if (!StepIntentBinder.isLeaveOrKeepEmptyStep(intent.text())) {
                continue;
            }
            if (tokenOverlap(intent.text(), hay) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldSkip(Element el) {
        String type = el.attr("type").toLowerCase(Locale.ROOT);
        if ("hidden".equals(type) || "submit".equals(type) || "button".equals(type)
                || "image".equals(type) || "reset".equals(type) || "file".equals(type)) {
            return true;
        }
        if (el.hasAttr("disabled") || el.hasAttr("readonly")) {
            return true;
        }
        // Login credentials are handled by JobLoginService / prelude — skip typical auth fields here
        String hint = (el.attr("name") + " " + el.id() + " " + el.attr("placeholder")
                + " " + el.attr("data-test") + " " + el.attr("data-testid")
                + " " + el.attr("data-axis-test-id") + " " + el.attr("autocomplete")).toLowerCase(Locale.ROOT);
        if (DummyValueInventor.looksLikeOtpHint(hint)) {
            return true;
        }
        if (hint.contains("password") || hint.contains("username") || hint.contains("user-name")
                || "username".equals(hint.trim()) || hint.contains("current-password")
                || hint.contains("new-password")) {
            return true;
        }
        return false;
    }

    private static boolean isFillCandidate(Element el) {
        if (el.hasAttr("required") || "true".equalsIgnoreCase(el.attr("aria-required"))) {
            return true;
        }
        // Inside a form, or has stable identity typical of checkout/profile fields
        if (el.closest("form") != null) {
            return true;
        }
        return hasStableIdentity(el);
    }

    private static boolean hasStableIdentity(Element el) {
        return usableIdentifier(el.id()) || usableIdentifier(el.attr("name"))
                || usableIdentifier(el.attr("data-test")) || usableIdentifier(el.attr("data-testid"))
                || usableIdentifier(el.attr("data-qa"))
                || usable(el.attr("placeholder")) || usable(el.attr("aria-label"));
    }

    private static boolean isEmpty(Element el) {
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        String type = el.attr("type").toLowerCase(Locale.ROOT);
        if ("radio".equals(type) || "checkbox".equals(type)) {
            return !el.hasAttr("checked");
        }
        if ("select".equals(tag)) {
            Element selected = el.selectFirst("option[selected]");
            if (selected != null) {
                String v = selected.attr("value");
                String t = selected.text();
                return (v == null || v.isBlank()) && (t == null || t.isBlank()
                        || t.toLowerCase(Locale.ROOT).contains("select"));
            }
            Element first = el.selectFirst("option");
            if (first == null) {
                return true;
            }
            // Many UIs ship a blank placeholder as first selected implicitly
            String v = first.attr("value");
            return v == null || v.isBlank() || first.text().toLowerCase(Locale.ROOT).contains("select");
        }
        String val = el.val();
        return val == null || val.isBlank();
    }

    private static Element pickRadio(Document doc, String group, Element fallback) {
        if (group != null && !group.isBlank()) {
            Elements groupEls = doc.select("input[type=radio][name=" + cssEscape(group) + "]");
            for (Element r : groupEls) {
                if (r.hasAttr("required") || "true".equalsIgnoreCase(r.attr("aria-required"))) {
                    return r;
                }
            }
            if (!groupEls.isEmpty()) {
                return groupEls.first();
            }
        }
        return fallback;
    }

    private static String firstSelectableOption(Element select) {
        for (Element opt : select.select("option")) {
            if (opt.hasAttr("disabled")) {
                continue;
            }
            String v = opt.attr("value");
            String t = opt.text() == null ? "" : opt.text().trim();
            if ((v == null || v.isBlank()) && (t.isBlank() || t.toLowerCase(Locale.ROOT).contains("select"))) {
                continue;
            }
            return !t.isBlank() ? t : v;
        }
        return null;
    }

    private static Locator locatorOf(Element el) {
        // Prefer test hooks so autofill locators match binder preferred-hook strategy.
        List<String> preferred = PreferredHooksStore.current();
        if (preferred != null) {
            for (String hook : preferred) {
                if (hook == null || hook.isBlank()) {
                    continue;
                }
                String v = el.attr(hook);
                if (usableIdentifier(v)) {
                    return new Locator("css", "[" + hook + "='" + v.replace("'", "") + "']");
                }
            }
        }
        for (String attr : List.of("data-test", "data-testid", "data-qa")) {
            String v = el.attr(attr);
            if (usableIdentifier(v)) {
                String strategy = "data-test".equals(attr) ? "data-test"
                        : "data-testid".equals(attr) ? "data-testid" : "data-qa";
                return new Locator(strategy, v);
            }
        }
        for (org.jsoup.nodes.Attribute attr : el.attributes()) {
            String key = attr.getKey() == null ? "" : attr.getKey().toLowerCase(Locale.ROOT);
            if (key.startsWith("data-") && key.contains("test") && usableIdentifier(attr.getValue())) {
                return new Locator("css", "[" + attr.getKey() + "='"
                        + attr.getValue().replace("'", "") + "']");
            }
        }
        if (usableIdentifier(el.id())) {
            return new Locator("id", el.id());
        }
        if (usableIdentifier(el.attr("name"))) {
            return new Locator("name", el.attr("name"));
        }
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        for (String attr : List.of("aria-label", "placeholder", "title")) {
            String v = el.attr(attr);
            if (usable(v)) {
                return new Locator("css", tag + "[" + attr + "='" + v.replace("'", "") + "']");
            }
        }
        String type = el.attr("type");
        if (usable(type) && !"text".equalsIgnoreCase(type) && !"password".equalsIgnoreCase(type)) {
            return new Locator("css", tag + "[type='" + type.replace("'", "") + "']");
        }
        return null;
    }

    private static String labelOf(Element el) {
        String aria = el.attr("aria-label");
        if (usable(aria)) {
            return aria;
        }
        String ph = el.attr("placeholder");
        if (usable(ph)) {
            return ph;
        }
        String id = el.id();
        if (usable(id)) {
            Element label = el.ownerDocument() == null ? null
                    : el.ownerDocument().selectFirst("label[for=" + cssEscape(id) + "]");
            if (label != null && usable(label.text())) {
                return label.text().trim();
            }
        }
        return el.attr("name");
    }

    private static boolean usable(String v) {
        return v != null && !v.isBlank() && v.length() < 120;
    }

    private static boolean usableIdentifier(String v) {
        return usable(v) && !GeneratedIdDetector.looksGenerated(v);
    }

    private static String cssEscape(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Locator(String strategy, String value) {
    }
}
