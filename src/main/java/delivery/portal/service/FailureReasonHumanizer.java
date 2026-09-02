package delivery.portal.service;

/**
 * Turns engine failure strings into short user-facing explanations.
 * Raw technical detail stays available for operators / bug-report CSV.
 */
public final class FailureReasonHumanizer {
    private FailureReasonHumanizer() {
    }

    public static String forUser(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String r = raw.trim();
        String lower = r.toLowerCase();

        if (lower.contains("ambiguous:type_field") || lower.contains("ambiguous:type_")) {
            return "Could not tell which input field to use (the page has more than one similar field). "
                    + "Try clearer steps like “Enter email or phone in the Email or phone field”.";
        }
        if (lower.contains("ambiguous:")) {
            return "Could not choose one clear control on the page for this step (several looked similar). "
                    + "Make the Excel step name the control more specifically.";
        }
        if (lower.contains("no dom candidate") && lower.contains("assert")) {
            return "Could not find the expected error/message on the page after the actions. "
                    + "The earlier steps may have worked, but the final check text did not match what Facebook showed.";
        }
        if (lower.contains("heal_exhausted") && lower.contains("phone")) {
            return "Could not find a dedicated Phone field. On Facebook, email and phone share one box — "
                    + "prefer “Enter … in the Email or phone field”.";
        }
        if (lower.contains("heal_exhausted") || lower.contains("cursor solved nothing")) {
            return "Could not reliably find or use the control for this step after trying backup methods. "
                    + "Check that the Excel step names a real, visible control.";
        }
        if (lower.contains("login_failed")) {
            return "Login did not succeed with the credentials used for this run.";
        }
        if (r.length() > 180) {
            return r.substring(0, 177) + "…";
        }
        return r;
    }
}
