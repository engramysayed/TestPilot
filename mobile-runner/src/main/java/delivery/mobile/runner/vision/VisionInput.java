package delivery.mobile.runner.vision;

public record VisionInput(
        VisionFrame fresh,
        VisionProposal proposal,
        boolean storedCoordinates,
        boolean uncertainAfterTap,
        String modelName
) {}
