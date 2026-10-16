package delivery.authoring;

import java.util.List;

public record BindDecision(
        Kind kind,
        DomCandidate winner,
        List<DomCandidate> shortlist,
        List<CandidateScore> scores
) {
    public enum Kind { CLEAR, TIE, EMPTY }

    public BindDecision {
        shortlist = shortlist == null ? List.of() : List.copyOf(shortlist);
        scores = scores == null ? List.of() : List.copyOf(scores);
    }

    public static BindDecision empty() {
        return new BindDecision(Kind.EMPTY, null, List.of(), List.of());
    }

    public static BindDecision clear(DomCandidate winner, List<CandidateScore> scores) {
        return new BindDecision(Kind.CLEAR, winner, winner == null ? List.of() : List.of(winner), scores);
    }

    public static BindDecision tie(List<DomCandidate> shortlist, List<CandidateScore> scores) {
        return new BindDecision(Kind.TIE, null, shortlist, scores);
    }
}