package delivery.authoring;

public record CandidateScore(
        DomCandidate candidate,
        int stability,
        int uniqueness,
        int intentMatch,
        int roleFit,
        int labelDistance
) {
    public int total() {
        return stability + uniqueness + intentMatch + roleFit + labelDistance;
    }
}