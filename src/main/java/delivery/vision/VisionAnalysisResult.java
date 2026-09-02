package delivery.vision;

import java.util.List;

public record VisionAnalysisResult(boolean found, List<VisualCandidate> candidates, String error) {

    public static VisionAnalysisResult empty() {
        return new VisionAnalysisResult(false, List.of(), null);
    }

    public static VisionAnalysisResult unavailable(String error) {
        return new VisionAnalysisResult(false, List.of(), error);
    }

    public static VisionAnalysisResult of(List<VisualCandidate> candidates) {
        List<VisualCandidate> safe = candidates == null ? List.of() : List.copyOf(candidates);
        return new VisionAnalysisResult(!safe.isEmpty(), safe, null);
    }
}
