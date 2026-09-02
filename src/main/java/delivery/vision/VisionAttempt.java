package delivery.vision;

import java.util.Locale;

public record VisionAttempt(
        String provider,
        String model,
        BoundingBox bbox,
        double confidence,
        String groundedCandidateId,
        boolean used,
        String outcome
) {
    public static VisionAttempt of(
            BoundingBox bbox,
            double confidence,
            String groundedCandidateId,
            boolean used,
            String outcome) {
        String model = VisionGroundingConfig.groundingModel();
        return new VisionAttempt(
                VisionGroundingConfig.groundingProviderId(),
                model == null || model.isBlank() ? "-" : model,
                bbox,
                confidence,
                groundedCandidateId == null || groundedCandidateId.isBlank() ? "none" : groundedCandidateId,
                used,
                outcome == null || outcome.isBlank() ? "miss" : outcome);
    }

    public String formatLine() {
        String bboxPart = bbox == null
                ? ""
                : " bbox=" + bbox.x() + "," + bbox.y() + "," + bbox.width() + "," + bbox.height();
        String id = groundedCandidateId == null || groundedCandidateId.isBlank()
                ? "none" : groundedCandidateId;
        return ("VISION " + safe(provider) + "/" + safe(model)
                + bboxPart
                + " conf=" + String.format(Locale.ROOT, "%.2f", confidence)
                + " grounded=" + id
                + " used=" + (used ? "yes" : "no")
                + " outcome=" + safe(outcome)).trim();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
