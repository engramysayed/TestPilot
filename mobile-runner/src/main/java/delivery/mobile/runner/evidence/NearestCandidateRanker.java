package delivery.mobile.runner.evidence;

import delivery.mobile.runner.bind.CandidateGenerator;
import delivery.mobile.runner.bind.MatchNormalizer;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import delivery.mobile.result.FailureEvidenceManifest.NearestCandidateRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Top-K nearest nodes by edit distance to intended target text/id (deterministic). */
public final class NearestCandidateRanker {
    private NearestCandidateRanker() {}

    public static List<NearestCandidateRef> topK(MobileSnapshot snap, String intended, int k) {
        if (snap == null || intended == null) return List.of();
        String want = MatchNormalizer.normalize(intended);
        List<NearestCandidateRef> scored = new ArrayList<>();
        for (MobileSnapshotNode n : CandidateGenerator.pool(snap)) {
            String id = n.resourceId();
            String text = n.password() ? "" : MatchNormalizer.normalize(n.rawText());
            String desc = MatchNormalizer.normalize(n.contentDesc());
            int d = min3(EditDistance.of(want, id), EditDistance.of(want, text), EditDistance.of(want, desc));
            // also compare resource-id tail
            int slash = id.lastIndexOf('/');
            if (slash >= 0) {
                d = Math.min(d, EditDistance.of(want, id.substring(slash + 1)));
            }
            String label = !id.isEmpty() ? id : (!desc.isEmpty() ? desc : text);
            String hint = !id.isEmpty() ? "resourceId" : (!desc.isEmpty() ? "accessibilityId" : "text");
            scored.add(new NearestCandidateRef(0, n.index(), hint, label, d));
        }
        scored.sort(Comparator
                .comparingInt(NearestCandidateRef::editDistance)
                .thenComparingInt(NearestCandidateRef::nodeIndex)
                .thenComparing(NearestCandidateRef::label));
        List<NearestCandidateRef> out = new ArrayList<>();
        int limit = Math.min(k, scored.size());
        for (int i = 0; i < limit; i++) {
            NearestCandidateRef c = scored.get(i);
            out.add(new NearestCandidateRef(i + 1, c.nodeIndex(), c.strategyHint(), c.label(), c.editDistance()));
        }
        return List.copyOf(out);
    }

    private static int min3(int a, int b, int c) {
        return Math.min(a, Math.min(b, c));
    }
}
