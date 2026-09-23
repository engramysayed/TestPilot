package delivery.vision;

public record GroundedNode(
        String tag,
        String id,
        String name,
        String dataTest,
        String ariaLabel,
        String role,
        boolean displayed,
        boolean enabled,
        String outerFingerprint,
        String visibleText,
        String dataTestAttribute) {

    public GroundedNode(String tag, String id, String name, String dataTest, String ariaLabel,
                        String role, boolean displayed, boolean enabled, String fingerprint, String visibleText) {
        this(tag, id, name, dataTest, ariaLabel, role, displayed, enabled, fingerprint, visibleText, "data-test");
    }

    public GroundedNode(
            String tag,
            String id,
            String name,
            String dataTest,
            String ariaLabel,
            String role,
            boolean displayed,
            boolean enabled,
            String outerFingerprint) {
        this(tag, id, name, dataTest, ariaLabel, role, displayed, enabled, outerFingerprint, "");
    }
}
