package delivery.mobile.runner.snapshot;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * M2-01: secure Appium/UiAutomator XML page-source parser.
 * DTD / external entities disabled; byte / node / depth caps enforced.
 */
public final class MobileSnapshotParser {
    private final MobileSnapshotLimits limits;

    public MobileSnapshotParser() {
        this(MobileSnapshotLimits.DEFAULT);
    }

    public MobileSnapshotParser(MobileSnapshotLimits limits) {
        this.limits = limits == null ? MobileSnapshotLimits.DEFAULT : limits;
    }

    public MobileSnapshot parse(String xml, MobileSnapshotMeta meta) {
        if (xml == null || xml.isBlank()) {
            throw new MobileSnapshotException(MobileSnapshotException.Code.EMPTY, "page source XML is empty");
        }
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limits.maxBytes()) {
            throw new MobileSnapshotException(MobileSnapshotException.Code.OVERSIZE_BYTES,
                    "page source " + bytes.length + " bytes exceeds limit " + limits.maxBytes());
        }
        Document doc = parseDocument(bytes);
        Element root = doc.getDocumentElement();
        if (root == null) {
            throw new MobileSnapshotException(MobileSnapshotException.Code.MALFORMED, "XML has no document element");
        }
        List<MobileSnapshotNode> nodes = new ArrayList<>();
        walk(root, null, 0, nodes, "");
        if (nodes.isEmpty()) {
            throw new MobileSnapshotException(MobileSnapshotException.Code.MALFORMED, "no hierarchy nodes");
        }
        return new MobileSnapshot(meta, nodes, 0, sha256(bytes), bytes.length);
    }

    private Document parseDocument(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setValidating(false);
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (Exception ignored) { /* best effort */ }
            // Harden against XXE (architecture §5)
            setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException ignored) { /* older impl */ }

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void fatalError(org.xml.sax.SAXParseException e) {
                    // DocumentBuilder will still throw
                }
            });
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (MobileSnapshotException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String lower = msg.toLowerCase(Locale.ROOT);
            if (lower.contains("doctype") || lower.contains("entity") || lower.contains("external")) {
                throw new MobileSnapshotException(MobileSnapshotException.Code.XXE_REJECTED,
                        "XXE/DTD rejected: " + msg, e);
            }
            throw new MobileSnapshotException(MobileSnapshotException.Code.MALFORMED,
                    "malformed hierarchy XML: " + msg, e);
        }
    }

    private static void setFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (Exception ignored) {
            // Feature may be unsupported; other hardening still applies
        }
    }

    private void walk(Element el, Integer parentIndex, int depth, List<MobileSnapshotNode> out, String windowId) {
        if (depth > limits.maxDepth()) {
            throw new MobileSnapshotException(MobileSnapshotException.Code.OVERSIZE_DEPTH,
                    "hierarchy depth " + depth + " exceeds limit " + limits.maxDepth());
        }
        if (out.size() >= limits.maxNodes()) {
            throw new MobileSnapshotException(MobileSnapshotException.Code.OVERSIZE_NODES,
                    "hierarchy nodes exceed limit " + limits.maxNodes());
        }
        String tag = el.getTagName();
        // UiAutomator roots are typically hierarchy / node; multi-window may wrap extra layers
        if (!"node".equalsIgnoreCase(tag) && !"hierarchy".equalsIgnoreCase(tag)
                && !"android.widget.FrameLayout".equals(tag)) {
            // Still accept any element with bounds or resource-id as a node (Flutter / OEM variants)
            if (!el.hasAttribute("bounds") && !el.hasAttribute("resource-id") && !el.hasAttribute("class")) {
                NodeList kids = el.getChildNodes();
                for (int i = 0; i < kids.getLength(); i++) {
                    Node n = kids.item(i);
                    if (n.getNodeType() == Node.ELEMENT_NODE) {
                        walk((Element) n, parentIndex, depth, out, windowId);
                    }
                }
                return;
            }
        }

        String win = el.hasAttribute("windowId") ? el.getAttribute("windowId") : windowId;
        int index = out.size();
        List<Integer> childSlots = new ArrayList<>();
        // Placeholder — children filled after we know their indexes
        MobileBounds bounds = null;
        if (el.hasAttribute("bounds") && !el.getAttribute("bounds").isBlank()) {
            bounds = MobileBounds.parse(el.getAttribute("bounds"));
        }
        boolean password = bool(el, "password");
        String text = attr(el, "text");
        MobileSnapshotNode node = new MobileSnapshotNode(
                index,
                parentIndex,
                firstNonBlank(attr(el, "class"), tag),
                attr(el, "package"),
                attr(el, "resource-id"),
                attr(el, "content-desc"),
                text,
                firstNonBlank(attr(el, "hint"), attr(el, "hint-text")),
                bounds,
                boolDefaultTrue(el, "enabled"),
                boolDefaultTrue(el, "displayed"),
                bool(el, "clickable"),
                bool(el, "focusable"),
                bool(el, "selected"),
                password,
                bool(el, "checkable"),
                bool(el, "checked"),
                bool(el, "scrollable"),
                bool(el, "long-clickable"),
                win,
                childSlots
        );
        out.add(node);

        NodeList kids = el.getChildNodes();
        List<Integer> children = new ArrayList<>();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            int before = out.size();
            walk((Element) n, index, depth + 1, out, win);
            // Direct child root is the first node appended whose parent is this index
            if (out.size() > before && Integer.valueOf(index).equals(out.get(before).parentIndex())) {
                children.add(before);
            }
        }
        // Rebuild node with children (immutable) — replace at index
        out.set(index, new MobileSnapshotNode(
                node.index(), node.parentIndex(), node.className(), node.packageName(),
                node.resourceId(), node.contentDesc(), node.rawText(), node.hint(), node.bounds(),
                node.enabled(), node.displayed(), node.clickable(), node.focusable(), node.selected(),
                node.password(), node.checkable(), node.checked(), node.scrollable(), node.longClickable(),
                node.windowId(), children
        ));
    }

    private static String attr(Element el, String name) {
        if (!el.hasAttribute(name)) return "";
        return el.getAttribute(name);
    }

    private static boolean bool(Element el, String name) {
        String v = attr(el, name);
        return "true".equalsIgnoreCase(v);
    }

    private static boolean boolDefaultTrue(Element el, String name) {
        if (!el.hasAttribute(name)) return true;
        return !"false".equalsIgnoreCase(el.getAttribute(name));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return "";
        }
    }
}
