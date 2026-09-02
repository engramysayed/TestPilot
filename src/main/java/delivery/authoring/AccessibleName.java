package delivery.authoring;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The words a person reads next to a control, resolved the way a browser resolves them.
 *
 * <p>Standard accessible markup puts the name in a sibling node, not in an attribute on the
 * control: {@code <label><input type="text"><span>First name</span></label>}. Reading only
 * attributes leaves such a field anonymous, so nothing can be bound to it and no amount of
 * healing recovers it — the control is simply not in the candidate table.
 *
 * <p>Resolution order follows the HTML accessible-name computation, most authoritative first.
 */
public final class AccessibleName {
    private static final int MAX_LENGTH = 60;

    /**
     * The HTML autocomplete vocabulary is a fixed spec list, so mapping its tokens to the words a
     * test author would write is site-agnostic.
     */
    private static final Map<String, String> AUTOCOMPLETE_WORDS = Map.ofEntries(
            Map.entry("given-name", "first name"),
            Map.entry("additional-name", "middle name"),
            Map.entry("family-name", "last name surname"),
            Map.entry("nickname", "nickname"),
            Map.entry("username", "username"),
            Map.entry("new-password", "new password"),
            Map.entry("current-password", "current password"),
            Map.entry("email", "email"),
            Map.entry("tel", "phone number"),
            Map.entry("tel-national", "phone number"),
            Map.entry("street-address", "street address"),
            Map.entry("address-line1", "address line 1"),
            Map.entry("address-line2", "address line 2"),
            Map.entry("address-level1", "state province"),
            Map.entry("address-level2", "city"),
            Map.entry("postal-code", "postal code zip"),
            Map.entry("country", "country"),
            Map.entry("country-name", "country"),
            Map.entry("organization", "company organization"),
            Map.entry("cc-name", "cardholder name"),
            Map.entry("cc-number", "card number"),
            Map.entry("cc-exp", "expiry date"),
            Map.entry("cc-csc", "security code cvv"),
            Map.entry("bday", "date of birth"),
            Map.entry("bday-day", "day"),
            Map.entry("bday-month", "month"),
            Map.entry("bday-year", "year"),
            Map.entry("sex", "gender"));

    private AccessibleName() {
    }

    public static String of(Element el) {
        if (el == null) {
            return "";
        }
        String aria = clean(el.attr("aria-label"));
        if (!aria.isBlank()) {
            return aria;
        }
        String labelledBy = fromLabelledBy(el);
        if (!labelledBy.isBlank()) {
            return labelledBy;
        }
        String explicit = fromLabelFor(el);
        if (!explicit.isBlank()) {
            return explicit;
        }
        String wrapping = fromWrappingLabel(el);
        if (!wrapping.isBlank()) {
            return wrapping;
        }
        String placeholder = clean(el.attr("placeholder"));
        if (!placeholder.isBlank()) {
            return placeholder;
        }
        String title = clean(el.attr("title"));
        if (!title.isBlank()) {
            return title;
        }
        String adjacent = fromAdjacentText(el);
        if (!adjacent.isBlank()) {
            return adjacent;
        }
        return fromAutocomplete(el);
    }

    /** The label element a locator can be anchored to, or null when the name came from elsewhere. */
    public static Element labelElement(Element el) {
        if (el == null) {
            return null;
        }
        Element explicit = labelFor(el);
        if (explicit != null && !clean(explicit.text()).isBlank()) {
            return explicit;
        }
        Element wrapping = el.closest("label");
        if (wrapping != null && !clean(wrapping.text()).isBlank()) {
            return wrapping;
        }
        return null;
    }

    /** True when the label wraps the control, which decides descendant vs following anchoring. */
    public static boolean labelWrapsControl(Element el) {
        Element wrapping = el == null ? null : el.closest("label");
        return wrapping != null && !clean(wrapping.text()).isBlank() && labelFor(el) == null;
    }

    private static Element labelFor(Element el) {
        String id = el.id();
        if (id == null || id.isBlank()) {
            return null;
        }
        Document doc = el.ownerDocument();
        if (doc == null) {
            return null;
        }
        try {
            return doc.selectFirst("label[for=" + cssEscape(id) + "]");
        } catch (Exception invalidSelector) {
            return null;
        }
    }

    private static String fromLabelledBy(Element el) {
        String ids = el.attr("aria-labelledby");
        if (ids == null || ids.isBlank()) {
            return "";
        }
        Document doc = el.ownerDocument();
        if (doc == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : ids.trim().split("\\s+")) {
            Element target;
            try {
                target = doc.getElementById(id);
            } catch (Exception e) {
                continue;
            }
            if (target == null) {
                continue;
            }
            String text = clean(target.text());
            if (!text.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(text);
            }
        }
        return clean(sb.toString());
    }

    private static String fromLabelFor(Element el) {
        Element label = labelFor(el);
        return label == null ? "" : clean(label.text());
    }

    private static String fromWrappingLabel(Element el) {
        Element label = el.closest("label");
        return label == null ? "" : clean(label.text());
    }

    /**
     * Tags that commonly hold a short field caption. A {@code div} is allowed only when
     * {@link #isCaptionSibling} says it is a leaf caption — never a layout column that
     * contains other controls (that would steal the whole page's words onto a chrome link).
     */
    private static final List<String> CAPTION_TAGS = List.of("span", "label", "p", "strong", "b");

    /** A caption rendered just before or after the control, common in grid-based forms. */
    private static String fromAdjacentText(Element el) {
        String previous = siblingText(el, false);
        if (!previous.isBlank()) {
            return previous;
        }
        return siblingText(el, true);
    }

    private static String siblingText(Element el, boolean forward) {
        Node node = forward ? el.nextSibling() : el.previousSibling();
        int hops = 0;
        while (node != null && hops < 3) {
            if (node instanceof TextNode text) {
                String value = clean(text.text());
                if (!value.isBlank()) {
                    return value;
                }
            } else if (node instanceof Element sibling) {
                if (isInteractiveSibling(sibling)) {
                    return "";
                }
                if (isCaptionSibling(sibling)) {
                    String value = clean(sibling.ownText().isBlank() ? sibling.text() : sibling.ownText());
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
            node = forward ? node.nextSibling() : node.previousSibling();
            hops++;
        }
        return "";
    }

    /** True when the sibling is itself an interactive control — do not skip past it. */
    private static boolean isInteractiveSibling(Element sibling) {
        return sibling != null && sibling.is(
                "a, button, input, select, textarea, [role=button], [role=link], [role=combobox]");
    }

    /**
     * True for a short caption node next to a control. Rejects layout blocks that contain
     * interactive descendants or a deep subtree — those are page columns, not labels.
     */
    private static boolean isCaptionSibling(Element sibling) {
        if (sibling == null) {
            return false;
        }
        String tag = sibling.tagName().toLowerCase(Locale.ROOT);
        if (!CAPTION_TAGS.contains(tag) && !"div".equals(tag)) {
            return false;
        }
        if (!sibling.select(
                "a, button, input, select, textarea, [role=button], [role=link], [role=combobox]")
                .isEmpty()) {
            return false;
        }
        if (sibling.select("*").size() > 2) {
            return false;
        }
        String text = clean(sibling.ownText().isBlank() ? sibling.text() : sibling.ownText());
        return !text.isBlank() && text.length() <= 40;
    }

    private static String fromAutocomplete(Element el) {
        String token = el.attr("autocomplete").trim().toLowerCase(Locale.ROOT);
        if (token.isBlank()) {
            return "";
        }
        // "shipping street-address" and friends carry a section prefix before the field token.
        String[] parts = token.split("\\s+");
        String last = parts[parts.length - 1];
        String mapped = AUTOCOMPLETE_WORDS.get(last);
        if (mapped != null) {
            return mapped;
        }
        return "off".equals(last) || "on".equals(last) ? "" : last.replace('-', ' ');
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String out = value.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
        if (out.length() > MAX_LENGTH) {
            out = out.substring(0, MAX_LENGTH).trim();
        }
        return out;
    }

    private static String cssEscape(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
