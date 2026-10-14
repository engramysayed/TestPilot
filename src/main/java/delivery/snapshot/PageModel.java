package delivery.snapshot;

import java.util.List;

/** Serializer result. {@code closedShadowHook} reports whether the attachShadow hook was active. */
public record PageModel(boolean closedShadowHook, int frames, boolean truncated, List<SnapshotCandidate> candidates) {

    /** Stable element id the AI answers with: "e" + index in {@link #candidates()}. */
    public static String idOf(int index) {
        return "e" + index;
    }

    public java.util.Optional<SnapshotCandidate> byId(String id) {
        if (id == null || !id.trim().matches("e\\d+")) return java.util.Optional.empty();
        int i = Integer.parseInt(id.trim().substring(1));
        return i < candidates.size() ? java.util.Optional.of(candidates.get(i)) : java.util.Optional.empty();
    }

    /** Compact prompt/binder form: one line per candidate, "[eN]" id then target string. */
    public String render(int maxChars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            SnapshotCandidate c = candidates.get(i);
            String line = "[" + idOf(i) + "] " + c.target().format() + " | " + (c.role().isBlank() ? c.tag() : c.role())
                    + " | " + c.name().replace('\n', ' ')
                    + (c.offscreen() ? " | offscreen" : "")
                    + (c.scrollContainer().isBlank() ? "" : " | scroll=" + c.scrollContainer())
                    + "\n";
            if (maxChars > 0 && sb.length() + line.length() > maxChars) break;
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * A11: render within a char budget, keeping the most useful elements: interactive + step-keyword first,
     * then interactive in viewport, other interactive, keyword, nav items, the rest. Selected lines keep
     * their eN ids and document order; a footer reports omissions so the model knows the list is partial.
     */
    public String renderBudgeted(int maxChars) {
        String full = render(0);
        if (maxChars <= 0 || full.length() <= maxChars) return full;
        int n = candidates.size();
        String[] lines = new String[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            lines[i] = line(i);
            order[i] = i;
        }
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt((Integer i) -> tier(candidates.get(i))).thenComparingInt(i -> i));
        String footerTemplate = "... %d more elements omitted (observation budget)\n";
        int reserve = String.format(footerTemplate, n).length();
        boolean[] keep = new boolean[n];
        int used = 0;
        for (Integer i : order) {
            if (used + lines[i].length() + reserve > maxChars) continue; // smaller lower-tier lines may still fit
            keep[i] = true;
            used += lines[i].length();
        }
        StringBuilder sb = new StringBuilder();
        int omitted = 0;
        for (int i = 0; i < n; i++) {
            if (keep[i]) sb.append(lines[i]); else omitted++;
        }
        if (omitted > 0) sb.append(String.format(footerTemplate, omitted));
        return sb.toString();
    }

    static int tier(SnapshotCandidate c) {
        if (c.interactive() && c.keyword()) return 0;
        if (c.interactive() && c.inViewport()) return 1;
        if (c.interactive()) return 2;
        if (c.keyword()) return 3;
        if (c.navItem()) return 4;
        return 5;
    }

    private String line(int i) {
        SnapshotCandidate c = candidates.get(i);
        return "[" + idOf(i) + "] " + c.target().format() + " | " + (c.role().isBlank() ? c.tag() : c.role())
                + " | " + c.name().replace('\n', ' ')
                + (c.offscreen() ? " | offscreen" : "")
                + (c.scrollContainer().isBlank() ? "" : " | scroll=" + c.scrollContainer())
                + "\n";
    }
}
