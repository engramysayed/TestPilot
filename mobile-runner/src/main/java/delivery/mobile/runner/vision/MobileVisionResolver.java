package delivery.mobile.runner.vision;

/**
 * Decides a vision tap from a fresh frame. Stored coordinates, a blank screen,
 * low confidence, or an uncertain result never become a pass or a retry.
 */
public final class MobileVisionResolver {
    public static final int MIN_CONFIDENCE = 70;

    private MobileVisionResolver() {}

    public static VisionOutcome decide(VisionInput input) {
        if (input.storedCoordinates()) {
            return VisionOutcome.blocked("STORED_COORDINATES");
        }
        if (input.modelName() == null || input.modelName().isBlank()) {
            return VisionOutcome.blocked("MISSING_PROVIDER");
        }
        VisionFrame fresh = input.fresh();
        if (fresh.blank() || fresh.secure()) {
            return VisionOutcome.blocked("BLANK_OR_SECURE");
        }
        if (!fresh.redactionEstablished()) {
            return VisionOutcome.blocked("REDACTION_REQUIRED");
        }
        if (input.uncertainAfterTap()) {
            return VisionOutcome.blocked("UNCERTAIN_NO_RETRY");
        }
        VisionProposal proposal = input.proposal();
        if (proposal == null || !sameGeometry(fresh, proposal.capturedOn())) {
            return VisionOutcome.blocked("STALE_GEOMETRY");
        }
        if (proposal.confidence() < MIN_CONFIDENCE) {
            return VisionOutcome.blocked("LOW_CONFIDENCE");
        }
        return VisionOutcome.tap(proposal.x(), proposal.y(), input.modelName());
    }

    private static boolean sameGeometry(VisionFrame fresh, VisionFrame captured) {
        if (fresh == null || captured == null) {
            return false;
        }
        return fresh.width() == captured.width()
                && fresh.height() == captured.height()
                && fresh.rotation() == captured.rotation()
                && fresh.insetLeft() == captured.insetLeft()
                && fresh.insetTop() == captured.insetTop()
                && fresh.contextKind().equals(captured.contextKind());
    }
}
