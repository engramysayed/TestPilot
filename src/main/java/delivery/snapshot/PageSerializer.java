package delivery.snapshot;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * New page serializer (A2). Unlike {@code getPageSource()+HtmlSlimmer} it:
 * keeps offscreen and scroll-container items, always keeps interactive and step-keyword elements,
 * walks same-origin frames recursively (depth <= 5) recording a frame path, and pierces open
 * shadow roots plus closed ones captured by {@link #installClosedShadowHook(WebDriver)}.
 */
public final class PageSerializer {

    public static final int MAX_FRAME_DEPTH = 5;
    static final String HOOK_JS = resource("/keel/closed-shadow-hook.js");
    static final String SERIALIZER_JS = resource("/keel/page-serializer.js");

    private PageSerializer() {
    }

    /**
     * Registers the attachShadow hook so it runs in every new document (and every same-process
     * frame) before page scripts. Must be called before navigation. Returns false when the driver
     * has no CDP (closed roots then stay invisible; open roots and frames still work).
     */
    public static boolean installClosedShadowHook(WebDriver driver) {
        if (driver instanceof HasCdp cdp) {
            try {
                cdp.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", Map.of("source", HOOK_JS));
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    public static PageModel serialize(WebDriver driver, List<String> stepKeywords, List<String> preferredAttrs) {
        if (!(driver instanceof JavascriptExecutor js)) return new PageModel(false, 0, false, List.of());
        Map<String, Object> opt = new LinkedHashMap<>();
        opt.put("maxDepth", MAX_FRAME_DEPTH);
        opt.put("keywords", stepKeywords == null ? List.of() : stepKeywords);
        opt.put("preferredAttrs", preferredAttrs == null ? List.of() : preferredAttrs);
        opt.put("maxCandidates", 4000);
        Object raw = js.executeScript(SERIALIZER_JS, opt);
        return parse(raw);
    }

    @SuppressWarnings("unchecked")
    static PageModel parse(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return new PageModel(false, 0, false, List.of());
        List<SnapshotCandidate> out = new ArrayList<>();
        Object list = m.get("candidates");
        if (list instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> c)) continue;
                Map<String, String> attrs = new LinkedHashMap<>();
                if (c.get("attrs") instanceof Map<?, ?> a) a.forEach((k, v) -> attrs.put(String.valueOf(k), String.valueOf(v)));
                out.add(new SnapshotCandidate(str(c.get("tag")), str(c.get("role")), str(c.get("name")), attrs,
                        str(c.get("css")), frames(c.get("framePath")), shadows(c.get("shadowPath")),
                        bool(c.get("interactive")), bool(c.get("keyword")), bool(c.get("inViewport")),
                        bool(c.get("offscreen")), str(c.get("scrollContainer")), bool(c.get("navItem")),
                        bool(c.get("unreadable"))));
            }
        }
        return new PageModel(bool(m.get("hook")), m.get("frames") instanceof Number n ? n.intValue() : 0,
                bool(m.get("truncated")), out);
    }

    private static List<SnapshotCandidate.FrameHop> frames(Object o) {
        List<SnapshotCandidate.FrameHop> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) if (x instanceof Map<?, ?> f)
            out.add(new SnapshotCandidate.FrameHop(str(f.get("selector")),
                    f.get("index") instanceof Number n ? n.intValue() : -1, str(f.get("name")), str(f.get("src")),
                    bool(f.get("crossOrigin")), shadows(f.get("shadowPath"))));
        return out;
    }

    private static List<SnapshotCandidate.ShadowHop> shadows(Object o) {
        List<SnapshotCandidate.ShadowHop> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) if (x instanceof Map<?, ?> s)
            out.add(new SnapshotCandidate.ShadowHop(str(s.get("host")), str(s.get("mode"))));
        return out;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static boolean bool(Object o) { return o instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(o)); }

    private static String resource(String path) {
        try (InputStream in = PageSerializer.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
