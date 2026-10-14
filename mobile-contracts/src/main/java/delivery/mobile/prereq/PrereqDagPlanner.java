package delivery.mobile.prereq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles Call-before into a per-leaf DAG setup plan (M3-03 / architecture §7).
 * <ul>
 *   <li>First-seen topological order; shared prerequisites once per leaf invocation</li>
 *   <li>Cycle / missing-ref → reject</li>
 *   <li>Conflicting auth profile revisions in one closure → reject unless identical</li>
 *   <li>Negative login cases cannot serve as successful authenticated setup</li>
 *   <li>Run order: APP_BASELINE → AUTH_POLICY → START_STATE → PREREQUISITE* → LEAF</li>
 * </ul>
 */
public final class PrereqDagPlanner {
    public static final String CALL_BEFORE_CYCLE = "CALL_BEFORE_CYCLE";
    public static final String CALL_BEFORE_MISSING = "CALL_BEFORE_MISSING";
    public static final String AUTH_PROFILE_CONFLICT = "AUTH_PROFILE_CONFLICT";
    public static final String NEGATIVE_LOGIN_SETUP = "NEGATIVE_LOGIN_SETUP";
    public static final String LEAF_NOT_SELECTED = "LEAF_NOT_SELECTED";
    public static final String LEAF_MISSING = "LEAF_MISSING";

    private PrereqDagPlanner() {
    }

    /**
     * Build a setup plan for a single selected leaf.
     *
     * @param catalog all known cases (prereqs + leaf)
     * @param leafTcId the selected leaf TC
     * @param appBaselineRef optional app baseline step id (empty to omit)
     */
    public static LeafSetupPlan planLeaf(
            List<CaseNode> catalog, String leafTcId, String appBaselineRef) {
        Map<String, CaseNode> byId = index(catalog);
        if (leafTcId == null || leafTcId.isBlank()) {
            return LeafSetupPlan.reject("", LEAF_MISSING, "leaf tcId required");
        }
        String leaf = leafTcId.trim();
        CaseNode leafNode = byId.get(leaf);
        if (leafNode == null) {
            return LeafSetupPlan.reject(leaf, LEAF_MISSING, "unknown leaf: " + leaf);
        }
        if (!leafNode.selected()) {
            return LeafSetupPlan.reject(leaf, LEAF_NOT_SELECTED, "leaf not selected: " + leaf);
        }

        // Collect closure via DFS; detect missing + cycle
        LinkedHashSet<String> closure = new LinkedHashSet<>();
        List<String> stack = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        String err = collect(leaf, byId, closure, stack, visiting, true);
        if (err != null) {
            if (err.startsWith(CALL_BEFORE_CYCLE)) {
                return LeafSetupPlan.reject(leaf, CALL_BEFORE_CYCLE, err);
            }
            return LeafSetupPlan.reject(leaf, CALL_BEFORE_MISSING, err);
        }

        // Negative login cannot be a successful authenticated setup dependency
        for (String id : closure) {
            if (id.equals(leaf)) {
                continue;
            }
            CaseNode n = byId.get(id);
            if (n != null && n.negativeLogin()) {
                return LeafSetupPlan.reject(
                        leaf, NEGATIVE_LOGIN_SETUP,
                        "negative login case cannot serve as authenticated setup: " + id);
            }
        }

        // Auth profile conflict: distinct non-blank revision ids in closure
        Set<String> authRevs = new LinkedHashSet<>();
        for (String id : closure) {
            CaseNode n = byId.get(id);
            if (n != null && !n.authProfileRevisionId().isBlank()) {
                authRevs.add(n.authProfileRevisionId());
            }
        }
        if (authRevs.size() > 1) {
            return LeafSetupPlan.reject(
                    leaf, AUTH_PROFILE_CONFLICT,
                    "conflicting auth profile revisions in closure: " + authRevs);
        }

        // Topological order (first-seen Kahn from callBefore edges within closure)
        List<String> topo;
        try {
            topo = topoSort(closure, byId);
        } catch (PrereqException e) {
            return LeafSetupPlan.reject(leaf, e.code(), e.getMessage());
        }

        List<SetupPlanStep> steps = new ArrayList<>();
        String baseline = appBaselineRef == null ? "" : appBaselineRef.trim();
        if (!baseline.isBlank()) {
            steps.add(new SetupPlanStep(SetupPhaseKind.APP_BASELINE, baseline, "", false));
        }

        String authRev = authRevs.isEmpty() ? leafNode.authProfileRevisionId() : authRevs.iterator().next();
        if (!authRev.isBlank()) {
            steps.add(new SetupPlanStep(SetupPhaseKind.AUTH_POLICY, leaf, authRev, false));
        }

        // Start-state TCs (declared on leaf and prereqs) — once each, before prereq bodies
        LinkedHashSet<String> startStates = new LinkedHashSet<>();
        for (String id : topo) {
            CaseNode n = byId.get(id);
            if (n != null && !n.startStateTcId().isBlank()) {
                startStates.add(n.startStateTcId());
            }
        }
        for (String ss : startStates) {
            steps.add(new SetupPlanStep(SetupPhaseKind.START_STATE, ss, authRev, true));
        }

        Set<String> seenPrereq = new HashSet<>();
        for (String id : topo) {
            if (id.equals(leaf)) {
                continue;
            }
            if (!seenPrereq.add(id)) {
                continue; // shared once
            }
            CaseNode n = byId.get(id);
            steps.add(new SetupPlanStep(
                    SetupPhaseKind.PREREQUISITE, id,
                    n == null ? "" : n.authProfileRevisionId(), true));
        }
        steps.add(new SetupPlanStep(
                SetupPhaseKind.LEAF, leaf, leafNode.authProfileRevisionId(), false));
        return LeafSetupPlan.accept(leaf, steps);
    }

    /**
     * Plan each selected leaf independently (independent cases reset declared state).
     */
    public static List<LeafSetupPlan> planSelected(List<CaseNode> catalog, String appBaselineRef) {
        List<LeafSetupPlan> out = new ArrayList<>();
        if (catalog == null) {
            return List.of();
        }
        for (CaseNode n : catalog) {
            if (n.selected()) {
                out.add(planLeaf(catalog, n.tcId(), appBaselineRef));
            }
        }
        return List.copyOf(out);
    }

    private static Map<String, CaseNode> index(List<CaseNode> catalog) {
        Map<String, CaseNode> byId = new LinkedHashMap<>();
        if (catalog != null) {
            for (CaseNode n : catalog) {
                byId.put(n.tcId(), n);
            }
        }
        return byId;
    }

    private static String collect(
            String id,
            Map<String, CaseNode> byId,
            LinkedHashSet<String> closure,
            List<String> stack,
            Set<String> visiting,
            boolean isRoot) {
        if (visiting.contains(id)) {
            return CALL_BEFORE_CYCLE + ": " + String.join(" -> ", stack) + " -> " + id;
        }
        if (closure.contains(id)) {
            return null;
        }
        CaseNode node = byId.get(id);
        if (node == null) {
            return "missing case ref: " + id;
        }
        visiting.add(id);
        stack.add(id);
        for (String dep : node.callBefore()) {
            String depId = dep == null ? "" : dep.trim();
            if (depId.isBlank()) {
                continue;
            }
            if (!byId.containsKey(depId)) {
                return "missing case ref: " + depId;
            }
            String err = collect(depId, byId, closure, stack, visiting, false);
            if (err != null) {
                return err;
            }
        }
        stack.remove(stack.size() - 1);
        visiting.remove(id);
        closure.add(id);
        return null;
    }

    private static List<String> topoSort(Set<String> closure, Map<String, CaseNode> byId) {
        Map<String, Integer> indeg = new HashMap<>();
        Map<String, List<String>> forward = new HashMap<>();
        for (String id : closure) {
            indeg.put(id, 0);
            forward.put(id, new ArrayList<>());
        }
        for (String id : closure) {
            CaseNode n = byId.get(id);
            if (n == null) {
                continue;
            }
            for (String dep : n.callBefore()) {
                if (dep != null && closure.contains(dep.trim())) {
                    String d = dep.trim();
                    forward.get(d).add(id);
                    indeg.put(id, indeg.get(id) + 1);
                }
            }
        }
        // first-seen: iterate closure order for zero-indegree seed
        List<String> queue = new ArrayList<>();
        for (String id : closure) {
            if (indeg.get(id) == 0) {
                queue.add(id);
            }
        }
        List<String> out = new ArrayList<>();
        while (!queue.isEmpty()) {
            String n = queue.remove(0);
            out.add(n);
            for (String next : forward.get(n)) {
                int v = indeg.get(next) - 1;
                indeg.put(next, v);
                if (v == 0) {
                    queue.add(next);
                }
            }
        }
        if (out.size() != closure.size()) {
            throw new PrereqException(CALL_BEFORE_CYCLE, "cycle detected during topo sort");
        }
        return out;
    }
}
