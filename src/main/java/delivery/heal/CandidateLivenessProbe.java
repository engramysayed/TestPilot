package delivery.heal;

import delivery.authoring.DomCandidate;

/** Optional live probe used by heal to drop zero-size / hidden candidates. */
@FunctionalInterface
public interface CandidateLivenessProbe {
    CandidateLiveness probe(DomCandidate candidate);
}
