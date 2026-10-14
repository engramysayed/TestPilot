package delivery.effect;

import java.util.ArrayList;
import java.util.List;

/** Cheap before/after signature (Maestro-style hierarchy compare, web edition). */
public record EffectSignature(String url, String focus, String pageModelHash, String domDigest, int nodes, String targetState) {

    /** Names of the components that differ; empty = same. */
    public List<String> diff(EffectSignature o) {
        List<String> d = new ArrayList<>();
        if (!eq(url, o.url)) d.add("url");
        // Focus moving onto the clicked target is the click itself, not an application effect.
        if (!eq(focus, o.focus) && !"@target".equals(o.focus)) d.add("focus");
        if (!eq(pageModelHash, o.pageModelHash)) d.add("pageModel");
        if (!eq(domDigest, o.domDigest) || nodes != o.nodes) d.add("dom");
        if (!eq(targetState, o.targetState)) d.add("target");
        return d;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
