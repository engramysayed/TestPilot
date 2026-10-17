package delivery.authoring;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Excel/mobile confirm and expected lines before CandidateScorer runs.
 * Outcomes are a checklist: page-shown and "is clicked" never TIE the shortlist.
 */
public final class OutcomeKind {

    public enum Kind {
        QUOTED_TEXT,
        FIELD_ACCEPTED,
        FIELD_VISIBLE,
        CONTROL_VISIBLE,
        PAGE_SHOWN,
        SCREEN_GONE,
        NOT_VISIBLE,
        ACTION_ECHO,
        SELECTED,
        CHECKED,
        UNCHECKED,
        EXPANDED,
        TOAST,
        UPLOAD,
        ENABLED,
        DISABLED,
        VALUE,
        KEYBOARD,
        UNKNOWN
    }

    /**
     * @param kind           outcome kind
     * @param assertionType  existing execute type (visible, notVisible, textContains, urlContains,
     *                       selected, checked, unchecked)
     * @param expected       optional assertion expected (quote, URL slug, selected value, …)
     * @param namedControl   optional control/field/page name from the line
     */
    public record Outcome(Kind kind, String assertionType, String expected, String namedControl) {
        public Outcome {
            assertionType = assertionType == null ? "" : assertionType;
            expected = expected == null ? "" : expected;
            namedControl = namedControl == null ? "" : namedControl;
        }
    }

    private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']{2,80})[\"']");
    private static final Pattern FIELD_ACCEPTED = Pattern.compile(
            "(?i)\\b(.+?)\\s+is\\s+(?:accepted|filled)\\b");
    private static final Pattern FIELD_VISIBLE = Pattern.compile(
            "(?i)\\b(?:confirm|verify)?\\s*(?:the\\s+)?(.+?)\\s+field\\s+is\\s+(?:shown|visible|displayed)\\b");
    private static final Pattern CONTROL_VISIBLE = Pattern.compile(
            "(?i)\\b(?:confirm|verify)?\\s*(?:the\\s+|a\\s+)?(.+?)\\s+(?:button|link|item|section)\\s+is\\s+(?:shown|visible|displayed)\\b");
    private static final Pattern PAGE_SHOWN = Pattern.compile(
            "(?i)\\b(?:the\\s+)?([A-Za-z][\\w\\s-]{0,40}?)\\s+(?:page|screen|view)\\s+is\\s+shown\\b"
                    + "|\\b([A-Za-z][\\w-]{2,40})\\s+is\\s+shown\\b");
    private static final Pattern ACTION_ECHO = Pattern.compile(
            "(?i)\\b(.+?)\\s+is\\s+clicked\\b");
    private static final Pattern EXPANDED = Pattern.compile(
            "(?i)\\b(?:the\\s+)?(.+?)\\s+(?:section|menu|submenu|sidebar|nav)\\s+expands?\\b"
                    + "|\\b(?:the\\s+)?(.+?)\\s+expands?(?:\\s+and\\b|\\s*$|\\s*,)");
    private static final Pattern TOAST = Pattern.compile(
            "(?i)\\b(?:error|success|toast|notification|alert|banner)\\b");
    private static final Pattern UPLOAD = Pattern.compile(
            "(?i)\\b(?:file\\s*(?:upload|input)?|drop\\s*zone|browse|upload\\s*(?:area|zone|control)?)\\b");
    private static final Pattern ENABLED = Pattern.compile(
            "(?i)\\b(.+?)\\s+is\\s+enabled\\b");
    private static final Pattern DISABLED = Pattern.compile(
            "(?i)\\b(.+?)\\s+is\\s+disabled\\b");
    private static final Pattern VALUE_SHOWN = Pattern.compile(
            "(?i)\\b(?:the\\s+)?(.+?)\\s+(?:field|input|textbox)?\\s*(?:shows?|displays?|has\\s+value)\\s+[\"']?([^\"']+?)[\"']?\\s*$");
    private static final Pattern KEYBOARD = Pattern.compile(
            "(?i)\\b(?:the\\s+)?(?:soft\\s+)?keyboard\\s+is\\s+(?:shown|visible|displayed)\\b");
    private static final Pattern SCREEN_GONE = Pattern.compile(
            "(?i)\\b(no\\s+longer\\s+shown|not\\s+shown|is\\s+gone|disappear|is\\s+not\\s+visible|no\\s+longer\\s+visible)\\b");

    private OutcomeKind() {
    }

    public static Outcome classify(String text) {
        if (text == null || text.isBlank()) {
            return new Outcome(Kind.UNKNOWN, "visible", "", "");
        }
        String raw = text.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        // Quotes win over page-shown / landmark wording.
        Matcher quoted = QUOTED.matcher(raw);
        if (quoted.find()) {
            String phrase = quoted.group(1).trim();
            if (TOAST.matcher(lower).find()) {
                return new Outcome(Kind.TOAST, "textContains", phrase, "");
            }
            return new Outcome(Kind.QUOTED_TEXT, "textContains", phrase, "");
        }

        // Unquoted "the text/message/label X is visible" (existing extractAssertTextPhrase).
        String phrase = StepIntentBinder.extractAssertTextPhrase(raw);
        if (phrase != null && !phrase.isBlank()) {
            return new Outcome(Kind.QUOTED_TEXT, "textContains", phrase, "");
        }

        String state = StepIntentBinder.extractStateAssertion(raw);
        if ("unchecked".equals(state)) {
            return new Outcome(Kind.UNCHECKED, "unchecked", "", controlNameBeforeState(raw));
        }
        if ("checked".equals(state)) {
            return new Outcome(Kind.CHECKED, "checked", "", controlNameBeforeState(raw));
        }
        if ("selected".equals(state)) {
            String expected = StepIntentBinder.extractSelectedValuePhrase(raw);
            return new Outcome(Kind.SELECTED, "selected", expected == null ? "" : expected,
                    expected == null || expected.isBlank() ? controlNameBeforeState(raw) : expected);
        }
        if ("notVisible".equals(state) || SCREEN_GONE.matcher(lower).find()) {
            String name = StepIntentBinder.extractDisappearedControlName(raw);
            Kind gone = lower.contains("gone") || lower.contains("disappear") || lower.contains("no longer")
                    ? Kind.SCREEN_GONE : Kind.NOT_VISIBLE;
            return new Outcome(gone, "notVisible", "", name == null ? "" : name);
        }

        if (ACTION_ECHO.matcher(lower).find()) {
            Matcher m = ACTION_ECHO.matcher(raw);
            String name = m.find() ? stripLeadingConfirm(m.group(1)) : "";
            return new Outcome(Kind.ACTION_ECHO, "visible", "", name);
        }

        Matcher accepted = FIELD_ACCEPTED.matcher(raw);
        if (accepted.find()) {
            return new Outcome(Kind.FIELD_ACCEPTED, "visible", "", stripLeadingConfirm(accepted.group(1)));
        }

        Matcher fieldVis = FIELD_VISIBLE.matcher(raw);
        if (fieldVis.find()) {
            return new Outcome(Kind.FIELD_VISIBLE, "visible", "", stripLeadingConfirm(fieldVis.group(1)));
        }

        if (KEYBOARD.matcher(lower).find()) {
            return new Outcome(Kind.KEYBOARD, "visible", "", "");
        }

        Matcher expanded = EXPANDED.matcher(raw);
        if (expanded.find()) {
            String name = expanded.group(1) != null ? expanded.group(1) : expanded.group(2);
            return new Outcome(Kind.EXPANDED, "visible", "", stripLeadingConfirm(name));
        }

        Matcher controlVis = CONTROL_VISIBLE.matcher(raw);
        if (controlVis.find()) {
            return new Outcome(Kind.CONTROL_VISIBLE, "visible", "", stripLeadingConfirm(controlVis.group(1)));
        }

        if (TOAST.matcher(lower).find()
                && (lower.contains("shown") || lower.contains("visible") || lower.contains("displayed")
                || lower.contains("appears") || lower.contains("message"))) {
            String token = distinctiveToken(raw);
            return new Outcome(Kind.TOAST, "textContains", token, token);
        }

        if (UPLOAD.matcher(lower).find()
                && (lower.contains("shown") || lower.contains("visible") || lower.contains("displayed"))) {
            String name = distinctiveToken(raw);
            return new Outcome(Kind.UPLOAD, "visible", "", name);
        }

        Matcher enabled = ENABLED.matcher(raw);
        if (enabled.find()) {
            // TODO: no enabled execute type yet — map to visible on that control for v1
            return new Outcome(Kind.ENABLED, "visible", "", stripLeadingConfirm(enabled.group(1)));
        }
        Matcher disabled = DISABLED.matcher(raw);
        if (disabled.find()) {
            // TODO: no disabled execute type yet — map to visible on that control for v1
            return new Outcome(Kind.DISABLED, "visible", "", stripLeadingConfirm(disabled.group(1)));
        }

        Matcher valueShown = VALUE_SHOWN.matcher(raw);
        if (valueShown.find()) {
            String field = stripLeadingConfirm(valueShown.group(1));
            String val = valueShown.group(2).trim().replaceAll("[.,;:]+$", "");
            return new Outcome(Kind.VALUE, "textContains", val, field);
        }

        Matcher page = PAGE_SHOWN.matcher(raw);
        if (page.find()) {
            String name = page.group(1) != null ? page.group(1) : page.group(2);
            name = stripLeadingConfirm(name);
            String slug = pageSlug(name);
            return new Outcome(Kind.PAGE_SHOWN, "urlContains", slug, name);
        }

        // Landmark "X is shown" without "page" when X looks like a screen name.
        if (lower.matches(".*\\b(login|dashboard|home|welcome|settings|profile|otp|checkout)\\b.*")
                && (lower.contains("is shown") || lower.contains("are shown"))) {
            String slug = pageSlug(distinctiveToken(raw));
            if (!slug.isBlank()) {
                return new Outcome(Kind.PAGE_SHOWN, "urlContains", slug, slug);
            }
        }

        String token = distinctiveToken(raw);
        if (!token.isBlank() && token.length() >= 2) {
            return new Outcome(Kind.UNKNOWN, "textContains", token, token);
        }
        return new Outcome(Kind.UNKNOWN, "visible", "", "");
    }

    static String pageSlug(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String n = name.toLowerCase(Locale.ROOT).trim()
                .replaceAll("\\b(the|a|an|page|screen|view)\\b", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (n.isBlank()) {
            return "";
        }
        // first token is the URL-ish slug (login → login)
        return n.split("\\s+")[0];
    }

    static String stripLeadingConfirm(String s) {
        if (s == null) {
            return "";
        }
        return s.trim()
                .replaceAll("(?i)^(confirm|verify|assert)\\s+(?:that\\s+)?(?:the\\s+|a\\s+)?", "")
                .replaceAll("(?i)^(the|a|an)\\s+", "")
                .replaceAll("[.,;:]+$", "")
                .trim();
    }

    static String controlNameBeforeState(String text) {
        if (text == null) {
            return "";
        }
        Matcher m = Pattern.compile(
                "(?i)\\b(?:confirm|verify)?\\s*(?:the\\s+|a\\s+)?(.+?)\\s+is\\s+(?:the\\s+)?(?:unchecked|checked|selected)\\b")
                .matcher(text);
        if (m.find()) {
            return stripLeadingConfirm(m.group(1)
                    .replaceAll("(?i)\\b(button|link|checkbox|option|field|input)\\b", "")
                    .trim());
        }
        return "";
    }

    /** Distinctive quoted or capitalized / noun token for UNKNOWN / toast fallback. */
    static String distinctiveToken(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher q = QUOTED.matcher(text);
        if (q.find()) {
            return q.group(1).trim();
        }
        for (String t : text.replaceAll("[^A-Za-z0-9'_ -]", " ").split("\\s+")) {
            if (t.length() < 3) {
                continue;
            }
            String low = t.toLowerCase(Locale.ROOT);
            if (STOP.contains(low)) {
                continue;
            }
            return t;
        }
        return "";
    }

    private static final java.util.Set<String> STOP = java.util.Set.of(
            "confirm", "verify", "assert", "the", "and", "for", "with", "from", "that", "this",
            "into", "only", "shown", "visible", "displayed", "page", "screen", "view", "field",
            "button", "link", "item", "section", "menu", "submenu", "sidebar", "is", "are", "was",
            "have", "has", "should", "must", "see", "show", "shows", "display", "displays",
            "accepted", "filled", "clicked", "expands", "expand", "error", "success", "toast",
            "message", "text", "label", "heading", "keyboard", "enabled", "disabled", "value",
            "selected", "checked", "unchecked", "upload", "file", "drop", "zone", "browse");
}
