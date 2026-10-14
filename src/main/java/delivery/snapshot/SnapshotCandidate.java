package delivery.snapshot;

import java.util.List;
import java.util.Map;

/** One element kept by {@link PageSerializer}, with its frame path and shadow-host chain. */
public record SnapshotCandidate(
        String tag,
        String role,
        String name,
        Map<String, String> attrs,
        String css,
        List<FrameHop> framePath,
        List<ShadowHop> shadowPath,
        boolean interactive,
        boolean keyword,
        boolean inViewport,
        boolean offscreen,
        String scrollContainer,
        boolean navItem,
        boolean unreadableFrame) {

    /** index = position in parent window.frames (-1 unknown); shadowPath = hosts to reach the iframe. */
    public record FrameHop(String selector, int index, String name, String src, boolean crossOrigin,
                           List<ShadowHop> shadowPath) {
    }

    /** host = css of the shadow host, unique inside the enclosing root; mode = open|closed. */
    public record ShadowHop(String host, String mode) {
    }

    public KeelTarget target() {
        return KeelTarget.of(framePath, shadowPath, css);
    }
}
