package delivery.job;

import delivery.excel.CallBefore;
import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CallBeforeExpander {

    private CallBeforeExpander() {
    }

    /**
     * Expand selected leaves into a run list. Call-before chains are re-run before each leaf
     * (duplicates across leaves are intentional). Within one leaf's chain, overlapping nested
     * refs are kept only once (first occurrence).
     */
    public static List<ManualTestCase> expand(List<ManualTestCase> allCases, List<String> selectedLeafIds) {
        Map<String, ManualTestCase> byId = index(allCases);
        List<ManualTestCase> out = new ArrayList<>();
        List<String> leaves = selectedLeafIds == null ? List.of() : selectedLeafIds;
        for (String leaf : leaves) {
            String id = leaf == null ? "" : leaf.trim();
            if (id.isEmpty()) {
                continue;
            }
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException("UNKNOWN_TC: " + id);
            }
            List<String> chainIds = new ArrayList<>();
            expandInto(id, byId, new LinkedHashSet<>(), new LinkedHashSet<>(), chainIds);
            for (String cid : chainIds) {
                out.add(byId.get(cid));
            }
        }
        return out;
    }

    /**
     * For an already-expanded run list, mark where a new leaf chain starts (fresh browser).
     * Within a chain (Call-before → leaf), later rows are {@code false} so the session continues.
     */
    public static List<Boolean> freshSessionAt(List<ManualTestCase> expanded) {
        List<Boolean> flags = new ArrayList<>();
        if (expanded == null || expanded.isEmpty()) {
            return flags;
        }
        Map<String, ManualTestCase> byId = index(expanded);
        int pos = 0;
        while (pos < expanded.size()) {
            int end = longestChainEnd(expanded, byId, pos);
            for (int i = pos; i <= end; i++) {
                flags.add(i == pos);
            }
            pos = end + 1;
        }
        return flags;
    }

    /** Longest {@code end} such that {@code expand(leaf=cases[end])} equals {@code cases[pos..end]}. */
    private static int longestChainEnd(
            List<ManualTestCase> expanded,
            Map<String, ManualTestCase> byId,
            int pos
    ) {
        int best = pos;
        for (int end = pos; end < expanded.size(); end++) {
            ManualTestCase leaf = expanded.get(end);
            if (leaf == null || leaf.tcId() == null || leaf.tcId().isBlank()) {
                continue;
            }
            String leafId = leaf.tcId().trim();
            if (!byId.containsKey(leafId)) {
                continue;
            }
            List<String> expected = new ArrayList<>();
            expandInto(leafId, byId, new LinkedHashSet<>(), new LinkedHashSet<>(), expected);
            if (sliceMatches(expanded, pos, end, expected)) {
                best = end;
            }
        }
        return best;
    }

    private static boolean sliceMatches(
            List<ManualTestCase> expanded,
            int pos,
            int end,
            List<String> expected
    ) {
        if (expected.size() != (end - pos + 1)) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            ManualTestCase row = expanded.get(pos + i);
            String id = row == null || row.tcId() == null ? "" : row.tcId().trim();
            if (!expected.get(i).equals(id)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, ManualTestCase> index(List<ManualTestCase> allCases) {
        Map<String, ManualTestCase> byId = new LinkedHashMap<>();
        if (allCases != null) {
            for (ManualTestCase tc : allCases) {
                if (tc != null && tc.tcId() != null && !tc.tcId().isBlank()) {
                    byId.put(tc.tcId().trim(), tc);
                }
            }
        }
        return byId;
    }

    private static void expandInto(
            String id,
            Map<String, ManualTestCase> byId,
            Set<String> path,
            Set<String> seenInLeaf,
            List<String> chainIds
    ) {
        if (path.contains(id)) {
            throw new IllegalArgumentException("CALL_BEFORE_CYCLE: " + formatCycle(path, id));
        }
        path.add(id);

        ManualTestCase tc = byId.get(id);
        for (String beforeId : CallBefore.parse(tc.callBefore())) {
            if (!byId.containsKey(beforeId)) {
                throw new IllegalArgumentException("UNKNOWN_CALL_BEFORE: " + beforeId);
            }
            expandInto(beforeId, byId, path, seenInLeaf, chainIds);
        }

        if (seenInLeaf.add(id)) {
            chainIds.add(id);
        }

        path.remove(id);
    }

    private static String formatCycle(Set<String> path, String id) {
        List<String> cycle = new ArrayList<>(path);
        cycle.add(id);
        return String.join(" → ", cycle);
    }
}
