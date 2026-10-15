package delivery.mobile.runner.vision;

/** One fresh model answer. Coordinates are only valid for {@code capturedOn}. */
public record VisionProposal(
        int x,
        int y,
        int confidence,
        VisionFrame capturedOn,
        String model
) {}
