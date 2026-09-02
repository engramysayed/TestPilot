package delivery.vision;

import delivery.authoring.DomCandidate;

import java.util.List;

public record GroundingHit(String candidateId, List<DomCandidate> table, boolean added) {
}
