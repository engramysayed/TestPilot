package delivery.vision;

import delivery.authoring.StepIntentBinder;

import java.util.List;

public final class FakeVisionGroundingProvider implements VisionGroundingProvider {

    private final List<VisualCandidate> canned;
    private final VisionAssertionResult cannedAssert;

    public FakeVisionGroundingProvider() {
        this(List.of(), null);
    }

    public FakeVisionGroundingProvider(List<VisualCandidate> canned) {
        this(canned, null);
    }

    public FakeVisionGroundingProvider(VisionAssertionResult cannedAssert) {
        this(List.of(), cannedAssert);
    }

    public FakeVisionGroundingProvider(List<VisualCandidate> canned, VisionAssertionResult cannedAssert) {
        this.canned = canned == null ? List.of() : List.copyOf(canned);
        this.cannedAssert = cannedAssert;
    }

    @Override
    public VisionAnalysisResult analyze(byte[] screenshotPng, StepIntentBinder.IntentLine intent) {
        if (canned.isEmpty()) {
            return VisionAnalysisResult.empty();
        }
        return VisionAnalysisResult.of(canned);
    }

    @Override
    public VisionAssertionResult assertVisual(
            byte[] screenshotPng,
            String assertionText,
            String referenceImagePathOrNull) {
        if (cannedAssert != null) {
            return cannedAssert;
        }
        return VisionAssertionResult.uncertain("assertions not supported");
    }
}
