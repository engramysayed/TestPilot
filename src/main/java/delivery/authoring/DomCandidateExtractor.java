package delivery.authoring;

import delivery.store.PreferredHooksStore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts locator candidates from live HTML in locked priority order:
 * id → data-test* / data-qa → name → CSS attr → XPath attr.
 * CSS/XPath are emitted for interactive elements that lack higher-priority attributes.
 */
public final class DomCandidateExtractor {
    private static final Pattern UUID_LIKE = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern LONG_DIGITS = Pattern.compile(".*\\d{6,}.*");
    private static final int DEFAULT_MAX = 120;
    private static final List<String> CSS_ATTR_FALLBACKS = List.of(
            "aria-label", "placeholder", "title", "role", "type", "href", "alt");
    /**
     * Controls a user can operate. ARIA roles matter as much as tags: a modern date picker or
     * gender selector is a div wearing {@code role="combobox"}, and it is invisible to a
     * tag-only query.
     */
    public static final String INTERACTIVE_QUERY = String.join(", ",
            "a", "button", "input", "select", "textarea",
            "[role=button]", "[role=link]", "[onclick]",
            "[role=combobox]", "[role=listbox]", "[role=textbox]", "[role=searchbox]",
            "[role=checkbox]", "[role=radio]", "[role=switch]", "[role=spinbutton]",
            "[role=slider]", "[role=tab]", "[role=menuitem]", "[role=menuitemcheckbox]",
            "[role=menuitemradio]", "[aria-expanded]", "[aria-haspopup]",
            "[contenteditable=true]");
    /** Roles that describe the control better than the tag it happens to be built from. */
    private static final List<String> WIDGET_ROLES = List.of(
            "combobox", "listbox", "textbox", "searchbox", "checkbox", "radio", "switch",
            "spinbutton", "slider", "tab", "menuitem", "button", "link");
    private static final List<String> GENERIC_TAGS = List.of("div", "span", "label", "li", "td", "p");
    private static final List<String> NON_RENDERED_TAGS = List.of(
            "template", "noscript", "script", "style", "head", "title");
    private static final List<String> GENERIC_TYPE_VALUES = List.of("text", "password", "email", "number");
    /** Layout tags whose document-order index is too brittle to emit as bind targets. */
    private static final Set<String> NON_INDEXABLE_TAGS = Set.of("div", "span", "section");
    private static final Set<String> FORM_CONTROL_TAGS = Set.of("input", "select", "textarea");

    private DomCandidateExtractor() {
    }

    public static List<DomCandidate> extract(String html) {
        return extract(html, DEFAULT_MAX);
    }

    public static List<DomCandidate> extract(String html, List<String> preferredHooks) {
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(preferredHooks)) {
            return extract(html, DEFAULT_MAX);
        }
    }

    public static List<DomCandidate> extract(String html, int max) {
        List<DomCandidate> out = new ArrayList<>();
        if (html == null || html.isBlank() || max <= 0) {
            return out;
        }
        Document doc = Jsoup.parse(html);
        Map<String, DomCandidate> byKey = new LinkedHashMap<>();
        Set<String> repeatedIds = repeatedIds(doc);
        int seq = 1;

        // Surface iframes so binder/LLM know a context switch may be required.
        int frameIdx = 0;
        for (Element frame : doc.select("iframe, frame")) {
            String name = frame.attr("name");
            String id = frame.id();
            String src = frame.attr("src");
            String label = "iframe[" + frameIdx + "]"
                    + (usableIdentifier(name) ? " name=" + name : "")
                    + (usableIdentifier(id) ? " id=" + id : "")
                    + (src != null && !src.isBlank() ? " src=" + src : "");
            if (usableIdentifier(id)) {
                seq = put(byKey, seq, "iframe", id, "iframe", label);
            } else if (usableIdentifier(name)) {
                seq = put(byKey, seq, "iframe", name, "iframe", label);
            } else {
                seq = put(byKey, seq, "iframe", "index:" + frameIdx, "iframe", label);
            }
            frameIdx++;
        }

        for (Element el : doc.getAllElements()) {
            if ("html".equals(el.tagName()) || "body".equals(el.tagName()) || "head".equals(el.tagName())) {
                continue;
            }
            if (isHidden(el)) {
                continue;
            }
            String tag = el.tagName().toLowerCase(Locale.ROOT);
            List<String> preferredOnEl = preferredAttrsOn(el);
            if (!preferredOnEl.isEmpty()) {
                for (String attr : preferredOnEl) {
                    String v = el.attr(attr);
                    seq = put(byKey, seq, "css", cssAttrSelector(attr, v), tag, labelOf(el, v));
                }
                continue;
            }
            // One locator family per element: test hooks win over id/name twins.
            if (hasTestHookAttr(el)) {
                for (org.jsoup.nodes.Attribute attr : el.attributes()) {
                    if (!isTestHookAttr(attr.getKey())) {
                        continue;
                    }
                    String v = attr.getValue();
                    if (!usableIdentifier(v)) {
                        continue;
                    }
                    String key = attr.getKey().toLowerCase(Locale.ROOT);
                    if ("data-test".equals(key) || "data-testid".equals(key) || "data-qa".equals(key)) {
                        seq = put(byKey, seq, key, v, tag, labelOf(el, v));
                    } else {
                        seq = put(byKey, seq, "css", cssAttrSelector(attr.getKey(), v), tag, labelOf(el, v));
                    }
                }
                continue;
            }
            String id = el.id();
            if (usableIdentifier(id) && !repeatedIds.contains(id)) {
                seq = put(byKey, seq, "id", id, tag, labelOf(el, id));
            }
            String name = el.attr("name");
            if (usableIdentifier(name)) {
                seq = put(byKey, seq, "name", name, tag, labelOf(el, name));
            }
        }

        // CSS / XPath attribute fallbacks for interactive controls without stable attrs above
        for (Element el : doc.select(INTERACTIVE_QUERY)) {
            if (hasStableAttr(el, repeatedIds) || isHidden(el)) {
                continue;
            }
            String kind = controlKind(el);
            AttrSelector selector = uniqueAttrSelector(doc, el);
            if (selector != null) {
                String label = labelOf(el, selector.label());
                // A non-unique CSS (div[role='combobox']) resolves to the first match at runtime —
                // usually a control this TC already filled. Keep only the indexed XPath then.
                if (selector.css() != null && !selector.css().isBlank() && selector.uniqueCss()) {
                    seq = put(byKey, seq, "css", selector.css(), kind, label);
                }
                seq = put(byKey, seq, "xpath", selector.xpath(), kind, label);
                continue;
            }
            // No attribute carries the name — anchor on the label the user actually reads.
            LabelSelector byLabel = labelAnchoredSelector(doc, el);
            if (byLabel != null) {
                seq = put(byKey, seq, "xpath", byLabel.xpath(), kind, byLabel.name());
                continue;
            }
            // Named by nearby text rather than a label element: keep the words, address by ordinal.
            LabelSelector indexed = indexedSelector(doc, el);
            if (indexed != null) {
                seq = put(byKey, seq, "xpath", indexed.xpath(), kind, indexed.name());
            }
        }

        // Indexed checkbox / radio inputs (often have no id/name) — bindable by ordinal.
        seq = emitIndexedInputs(doc, byKey, seq, "checkbox", repeatedIds);
        seq = emitIndexedInputs(doc, byKey, seq, "radio", repeatedIds);

        // Buttons / links / menu entries identified by visible text when they lack stable attributes.
        for (Element el : doc.select(
                "button, a, [role=button], [role=menuitem], [aria-expanded], [aria-haspopup]")) {
            if (hasStableAttr(el, repeatedIds) || isHidden(el)) {
                continue;
            }
            String text = visibleLabel(el);
            if (text == null || text.length() < 2 || text.length() > 60) {
                continue;
            }
            String tag = el.tagName().toLowerCase(Locale.ROOT);
            String kind = controlKind(el);
            seq = put(byKey, seq, "xpath", innermostTextXpath(tag, text), kind, text);
        }

        return capped(byKey.values(), max);
    }

    /**
     * Tagged buttons/links already exclude a wrapper {@code div}. The extra
     * {@code not(.//*)} predicate is only for generic ancestors; on a {@code button} it
     * drops the control when the label lives in a child {@code span}.
     */
    static String innermostTextXpath(String tag, String text) {
        String lit = XpathLiterals.quote(text);
        String tagged = "//" + tag + "[contains(normalize-space(.)," + lit + ")]";
        if (isInteractiveTextTag(tag)) {
            return tagged;
        }
        return tagged + "[not(.//*[contains(normalize-space(.)," + lit + ")])]";
    }

    private static boolean isInteractiveTextTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        String t = tag.toLowerCase(Locale.ROOT);
        return "button".equals(t) || "a".equals(t) || "li".equals(t);
    }

    /**
     * Candidates are built id-first, so a plain head-truncation on a page with hundreds of ids
     * emits no CSS/XPath candidate at all — starving exactly the pages that depend on fallbacks.
     */
    private static List<DomCandidate> capped(java.util.Collection<DomCandidate> all, int max) {
        List<DomCandidate> stable = new ArrayList<>();
        List<DomCandidate> fallback = new ArrayList<>();
        for (DomCandidate c : all) {
            if (isStableStrategy(c.strategy(), c.value())) {
                stable.add(c);
            } else {
                fallback.add(c);
            }
        }
        int reserved = Math.min(fallback.size(), max / 3);
        List<DomCandidate> out = new ArrayList<>();
        for (DomCandidate c : stable) {
            if (out.size() >= max - reserved) {
                break;
            }
            out.add(c);
        }
        for (DomCandidate c : fallback) {
            if (out.size() >= max) {
                break;
            }
            out.add(c);
        }
        for (DomCandidate c : stable) {
            if (out.size() >= max) {
                break;
            }
            if (!out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Jsoup has no CSS engine, so hidden markers are all we can read from the snapshot. Worth
     * reading: responsive sites render the same nav twice and the hidden copy wins on document
     * order, which costs a full element-wait before anything notices.
     */
    private static boolean isHidden(Element el) {
        if (el == null) {
            return true;
        }
        if (hiddenMarker(el)) {
            return true;
        }
        for (Element ancestor : el.parents()) {
            if (hiddenMarker(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hiddenMarker(Element el) {
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        if (NON_RENDERED_TAGS.contains(tag)) {
            return true;
        }
        if (el.hasAttr("hidden") || "true".equalsIgnoreCase(el.attr("aria-hidden"))) {
            return true;
        }
        if ("hidden".equalsIgnoreCase(el.attr("type"))) {
            return true;
        }
        String style = el.attr("style").toLowerCase(Locale.ROOT).replace(" ", "");
        return style.contains("display:none") || style.contains("visibility:hidden");
    }

    /**
     * Builds the narrowest attribute selector that matches this element and nothing else. A single
     * attribute is tried first, then a pair — a form of four {@code div[role='combobox']} controls
     * needs the pair to tell Day from Year. Positional indexing is the last resort.
     */
    private static AttrSelector uniqueAttrSelector(Document doc, Element el) {
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        List<String> attrs = new ArrayList<>();
        for (String attr : CSS_ATTR_FALLBACKS) {
            String v = el.attr(attr);
            if (!usableStable(v)) {
                continue;
            }
            if ("type".equals(attr) && GENERIC_TYPE_VALUES.contains(v.toLowerCase(Locale.ROOT))) {
                continue;
            }
            attrs.add(attr);
        }
        if (attrs.isEmpty()) {
            return null;
        }
        for (String attr : attrs) {
            AttrSelector single = selectorOf(tag, List.of(attr), el);
            if (matchCount(doc, single.css()) == 1) {
                return single;
            }
        }
        for (String first : attrs) {
            for (String second : attrs) {
                if (first.equals(second)) {
                    continue;
                }
                AttrSelector pair = selectorOf(tag, List.of(first, second), el);
                if (matchCount(doc, pair.css()) == 1) {
                    return pair;
                }
            }
        }
        // Identical siblings — the only thing left that separates them is their order.
        AttrSelector base = selectorOf(tag, List.of(attrs.get(0)), el);
        int index = indexOf(doc, base.css(), el);
        if (index < 1) {
            return null;
        }
        return new AttrSelector(base.css(), "(" + base.xpath() + ")[" + index + "]",
                base.label(), false);
    }

    private static AttrSelector selectorOf(String tag, List<String> attrs, Element el) {
        StringBuilder css = new StringBuilder(tag);
        StringBuilder xpath = new StringBuilder("//").append(tag).append('[');
        for (int i = 0; i < attrs.size(); i++) {
            String attr = attrs.get(i);
            String value = el.attr(attr);
            css.append(cssAttrSelector(attr, value));
            if (i > 0) {
                xpath.append(" and ");
            }
            xpath.append('@').append(attr).append('=').append(XpathLiterals.quote(value));
        }
        xpath.append(']');
        return new AttrSelector(css.toString(), xpath.toString(), el.attr(attrs.get(0)));
    }

    private static int matchCount(Document doc, String css) {
        try {
            return doc.select(css).size();
        } catch (Exception invalidSelector) {
            return 0;
        }
    }

    private static int indexOf(Document doc, String css, Element el) {
        try {
            org.jsoup.select.Elements matches = doc.select(css);
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i) == el) {
                    return i + 1;
                }
            }
        } catch (Exception invalidSelector) {
            return -1;
        }
        return -1;
    }

    /** A div with {@code role="combobox"} is a dropdown; reporting it as a div hides that. */
    private static String controlKind(Element el) {
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        String role = el.attr("role").trim().toLowerCase(Locale.ROOT);
        if (GENERIC_TAGS.contains(tag) && WIDGET_ROLES.contains(role)) {
            return role;
        }
        return tag;
    }

    private record AttrSelector(String css, String xpath, String label, boolean uniqueCss) {
        AttrSelector(String css, String xpath, String label) {
            this(css, xpath, label, true);
        }
    }

    private record LabelSelector(String xpath, String name) {
    }

    /**
     * Anchors a control on its visible label, which is where standard accessible markup puts the
     * name. Only emitted when exactly one label on the page carries that text, so the locator
     * still identifies a single control.
     */
    private static LabelSelector labelAnchoredSelector(Document doc, Element el) {
        Element label = AccessibleName.labelElement(el);
        if (label == null) {
            return null;
        }
        String name = AccessibleName.of(el);
        if (name.length() < 2) {
            return null;
        }
        if (countLabelsWithExactName(doc, name) != 1) {
            return null;
        }
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        String quoted = XpathLiterals.quote(name);
        String forId = label.attr("for");
        if (forId != null && !forId.isBlank() && forId.equals(el.id())) {
            return new LabelSelector(
                    "//" + tag + "[@id=//label[normalize-space(.)=" + quoted + "]/@for]",
                    name);
        }
        String anchor = "//label[normalize-space(.)=" + quoted + "]";
        if (AccessibleName.labelWrapsControl(el)) {
            return new LabelSelector(anchor + "//" + tag, name);
        }
        if (isFollowingSiblingControl(label, el, tag)) {
            return new LabelSelector(anchor + "/following-sibling::" + tag + "[1]", name);
        }
        if (isFirstFollowingOfTag(label, el, tag)) {
            return new LabelSelector(anchor + "/following::" + tag + "[1]", name);
        }
        return null;
    }

    /** True when {@code el} is the next sibling control of {@code label} (or the only one inside it). */
    private static boolean isFollowingSiblingControl(Element label, Element el, String tag) {
        Element sib = label.nextElementSibling();
        if (sib == null) {
            return false;
        }
        if (sib == el && tag.equalsIgnoreCase(sib.tagName())) {
            return true;
        }
        return sib.select(tag).size() == 1 && sib.selectFirst(tag) == el;
    }

    /**
     * True only when this element is the first {@code tag} in document order after the label.
     * Otherwise {@code following::tag[1]} would type into a different field.
     */
    private static boolean isFirstFollowingOfTag(Element label, Element el, String tag) {
        if (label == null || el == null || tag == null) {
            return false;
        }
        Document doc = label.ownerDocument();
        if (doc == null) {
            return false;
        }
        boolean afterLabel = false;
        for (Element n : doc.getAllElements()) {
            if (afterLabel && tag.equalsIgnoreCase(n.tagName())) {
                return n == el;
            }
            if (n == label) {
                afterLabel = true;
            }
        }
        return false;
    }

    /**
     * Document-order ordinal for a control whose name is only rendered nearby. The words go in the
     * label so the binder can still match the intent; the ordinal is what addresses the element.
     */
    private static LabelSelector indexedSelector(Document doc, Element el) {
        String name = AccessibleName.of(el);
        if (name.isBlank()) {
            return null;
        }
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        if (NON_INDEXABLE_TAGS.contains(tag)) {
            return null;
        }
        String scopeQuery;
        String xpathBase;
        if (FORM_CONTROL_TAGS.contains(tag)) {
            if ("input".equals(tag) && el.hasAttr("type")) {
                String inputType = el.attr("type").trim();
                if (!inputType.isBlank()) {
                    scopeQuery = "input[type=" + inputType + "]";
                    xpathBase = "//input[@type='" + inputType + "']";
                } else {
                    scopeQuery = tag;
                    xpathBase = "//" + tag;
                }
            } else {
                scopeQuery = tag;
                xpathBase = "//" + tag;
            }
        } else {
            scopeQuery = tag;
            xpathBase = "//" + tag;
        }
        int index = doc.select(scopeQuery).indexOf(el) + 1;
        if (index <= 0) {
            return null;
        }
        return new LabelSelector("(" + xpathBase + ")[" + index + "]", name);
    }

    private static int countLabelsWithExactName(Document doc, String name) {
        String want = name == null ? "" : name.trim();
        int n = 0;
        for (Element label : doc.select("label")) {
            String t = label.ownText().isBlank() ? label.text() : label.ownText();
            if (want.equalsIgnoreCase(t == null ? "" : t.trim())) {
                n++;
            }
        }
        return n;
    }

    private static int emitIndexedInputs(
            Document doc, Map<String, DomCandidate> byKey, int seq, String inputType,
            Set<String> repeatedIds) {
        int index = 1;
        for (Element el : doc.select("input[type=" + inputType + "]")) {
            String adj = adjacentText(el);
            String label = inputType + " " + index
                    + (adj == null || adj.isBlank() ? "" : " " + adj);
            String xpath = "(//input[@type='" + inputType + "'])[" + index + "]";
            seq = put(byKey, seq, "xpath", xpath, "input", label);
            index++;
        }
        return seq;
    }

    private static String adjacentText(Element el) {
        if (el == null) {
            return null;
        }
        String own = el.ownText();
        if (own != null && !own.isBlank()) {
            return trim40(own);
        }
        // Immediate following text sibling only (not full parent text — that mixes all labels)
        org.jsoup.nodes.Node next = el.nextSibling();
        while (next != null) {
            if (next instanceof org.jsoup.nodes.TextNode tn) {
                String tx = tn.text();
                if (tx != null && !tx.isBlank()) {
                    return trim40(tx);
                }
            }
            if (next instanceof Element) {
                break;
            }
            next = next.nextSibling();
        }
        return null;
    }

    private static String visibleLabel(Element el) {
        if (el == null) {
            return null;
        }
        String t = el.text();
        if (t != null && !t.isBlank()) {
            return trim40(t.trim());
        }
        return labelOf(el, null);
    }

    private static String trim40(String s) {
        String t = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    public static boolean isStableStrategy(String strategy) {
        return isStableStrategy(strategy, null);
    }

    public static boolean isStableStrategy(String strategy, String value) {
        if (PreferredHooksStore.matches(value, PreferredHooksStore.current())
                || PreferredHooksStore.matches(strategy, PreferredHooksStore.current())) {
            return true;
        }
        if (strategy == null) {
            return false;
        }
        String s = strategy.trim().toLowerCase(Locale.ROOT);
        return "id".equals(s) || "name".equals(s)
                || "data-test".equals(s) || "data-testid".equals(s)
                || "data-qa".equals(s) || "testid".equals(s);
    }

    public static boolean isBindableStrategy(String strategy) {
        if (isStableStrategy(strategy)) {
            return true;
        }
        if (strategy == null) {
            return false;
        }
        String s = strategy.trim().toLowerCase(Locale.ROOT);
        return "css".equals(s) || "cssselector".equals(s) || "xpath".equals(s);
    }

    /** Higher = preferred when token scores tie. */
    public static int strategyRank(String strategy) {
        return strategyRank(strategy, null);
    }

    public static int strategyRank(String strategy, String value) {
        return strategyRank(strategy, value, PreferredHooksStore.current());
    }

    public static int strategyRank(String strategy, String value, List<String> preferredHooks) {
        if (PreferredHooksStore.matches(value, preferredHooks)
                || PreferredHooksStore.matches(strategy, preferredHooks)) {
            return 45;
        }
        if (strategy == null) {
            return 0;
        }
        return switch (strategy.trim().toLowerCase(Locale.ROOT)) {
            case "id" -> 40;
            case "data-test", "data-testid", "data-qa", "testid" -> 35;
            case "name" -> 30;
            case "css", "cssselector" -> 15;
            case "xpath" -> 10;
            default -> 0;
        };
    }

    public static DomCandidate findById(List<DomCandidate> candidates, String candidateId) {
        if (candidates == null || candidateId == null) {
            return null;
        }
        String want = candidateId.trim();
        for (DomCandidate c : candidates) {
            if (c.id().equalsIgnoreCase(want)) {
                return c;
            }
        }
        return null;
    }

    public static DomCandidate findByStrategyValue(List<DomCandidate> candidates, String strategy, String value) {
        if (candidates == null || strategy == null || value == null) {
            return null;
        }
        String s = strategy.trim().toLowerCase(Locale.ROOT);
        String v = value.trim();
        for (DomCandidate c : candidates) {
            if (c.strategy().equalsIgnoreCase(s) && c.value().equals(v)) {
                return c;
            }
        }
        return null;
    }

    public static String formatTable(List<DomCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "(no candidates)";
        }
        StringBuilder sb = new StringBuilder();
        for (DomCandidate c : candidates) {
            sb.append(c.id()).append(" | ").append(c.strategy()).append(" | ")
                    .append(c.value()).append(" | ").append(c.tag())
                    .append(" | ").append(c.label()).append('\n');
        }
        return sb.toString();
    }

    private static boolean hasStableAttr(Element el, Set<String> repeatedIds) {
        return (usableIdentifier(el.id()) && !repeatedIds.contains(el.id()))
                || hasTestHookAttr(el)
                || !preferredAttrsOn(el).isEmpty()
                || usableIdentifier(el.attr("name"));
    }

    private static List<String> preferredAttrsOn(Element el) {
        List<String> wanted = PreferredHooksStore.current();
        if (el == null || wanted.isEmpty()) {
            return List.of();
        }
        List<String> hit = new ArrayList<>();
        for (String hook : wanted) {
            if (hook == null || hook.isBlank()) {
                continue;
            }
            String v = el.attr(hook);
            if (usableIdentifier(v)) {
                hit.add(hook);
            }
        }
        return hit;
    }

    static boolean isTestHookAttr(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return "data-qa".equals(n) || (n.startsWith("data-") && n.contains("test"));
    }

    private static boolean hasTestHookAttr(Element el) {
        for (org.jsoup.nodes.Attribute attr : el.attributes()) {
            if (isTestHookAttr(attr.getKey()) && usableIdentifier(attr.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** An id shared by several elements identifies none of them. */
    private static Set<String> repeatedIds(Document doc) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Element el : doc.getAllElements()) {
            String id = el.id();
            if (id != null && !id.isBlank()) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        Set<String> repeated = new LinkedHashSet<>();
        counts.forEach((id, count) -> {
            if (count > 1) {
                repeated.add(id);
            }
        });
        return repeated;
    }

    private static int put(Map<String, DomCandidate> byKey, int seq, String strategy, String value,
                           String tag, String label) {
        String key = strategy + ":" + value;
        if (byKey.containsKey(key)) {
            return seq;
        }
        String id = "c" + seq;
        byKey.put(key, new DomCandidate(id, strategy, value, tag, label));
        return seq + 1;
    }

    private static boolean usable(String v) {
        return v != null && !v.isBlank() && v.length() < 120;
    }

    private static boolean usableStable(String v) {
        if (!usable(v)) {
            return false;
        }
        return !UUID_LIKE.matcher(v).find() && !LONG_DIGITS.matcher(v).matches();
    }

    /**
     * Identifier attributes (id / name / data-test*) additionally have to survive a reload, so a
     * framework-minted value is dropped here and the element falls through to a CSS selector built
     * on a human attribute. Text-ish attributes keep using {@link #usableStable} — a placeholder
     * or href legitimately looks nothing like a hand-written identifier.
     */
    private static boolean usableIdentifier(String v) {
        return usableStable(v) && !GeneratedIdDetector.looksGenerated(v);
    }

    private static String cssAttrSelector(String attr, String value) {
        if (value.indexOf('\'') >= 0 && value.indexOf('"') < 0) {
            return "[" + attr + "=\"" + value + "\"]";
        }
        if (value.indexOf('"') >= 0) {
            return "[" + attr + "='" + value.replace("'", "\\'") + "']";
        }
        return "[" + attr + "='" + value + "']";
    }

    private static String labelOf(Element el, String fallback) {
        String text = el.ownText();
        if (text != null && !text.isBlank()) {
            return text.trim().length() > 40 ? text.trim().substring(0, 40) : text.trim();
        }
        String aria = el.attr("aria-label");
        if (usable(aria)) {
            return aria;
        }
        String placeholder = el.attr("placeholder");
        if (usable(placeholder)) {
            return placeholder;
        }
        String accessible = AccessibleName.of(el);
        if (usable(accessible)) {
            return accessible;
        }
        return fallback;
    }
}
