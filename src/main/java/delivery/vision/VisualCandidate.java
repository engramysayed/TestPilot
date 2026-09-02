package delivery.vision;

public record VisualCandidate(String description, BoundingBox boundingBox, double confidence) {
}
