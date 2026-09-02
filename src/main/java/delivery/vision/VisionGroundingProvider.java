package delivery.vision;

import delivery.authoring.StepIntentBinder;

public interface VisionGroundingProvider {

    VisionAnalysisResult analyze(byte[] screenshotPng, StepIntentBinder.IntentLine intent);

    default VisionAssertionResult assertVisual(
            byte[] screenshotPng,
            String assertionText,
            String referenceImagePathOrNull) {
        return VisionAssertionResult.uncertain("assertions not supported");
    }
}
