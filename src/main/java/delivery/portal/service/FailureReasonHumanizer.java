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
                    + "The earlier steps may have worked, but the final check text did not match what the app showed.";
        }
        if (lower.contains("heal_exhausted") && lower.contains("phone")) {
            return "Could not find a dedicated Phone field. On this site, email and phone may share one box — "
                    + "prefer “Enter … in the Email or phone field”.";
        }
        if (lower.contains("heal_exhausted") || lower.contains("cursor solved nothing")) {
            return "Could not reliably find or use the control for this step after trying backup methods. "
                    + "Check that the Excel step names a real, visible control.";
        }
        if (lower.contains("login_failed")) {
            return "Login did not succeed with the credentials used for this run.";
        }
        if (lower.contains("err_name_not_resolved") || lower.contains("unknown host")
                || lower.contains("nodename nor servname")) {
            return "Could not open the site URL — DNS lookup failed (host not found). "
                    + "Check VPN, base URL spelling, and that this machine can resolve the host.";
        }
        if (lower.contains("invalid session id") || lower.contains("chrome not reachable")
                || lower.contains("not connected to devtools") || lower.contains("no such window")) {
            return "The Chrome browser session closed mid-hunt (window closed or crashed). "
                    + "Re-run the hunt and leave the browser window open.";
        }
        if (lower.contains("err_connection_refused") || lower.contains("connection refused")) {
            return "Could not open the site URL — connection refused. "
                    + "Check that the app is up and reachable from this machine.";
        }
        if (lower.contains("err_connection_timed_out") || lower.contains("err_timed_out")) {
            return "Could not open the site URL — connection timed out. "
                    + "Check network/VPN and that the host is reachable.";
        }
        if (r.length() > 180) {
            return r.substring(0, 177) + "…";
        }
        return r;
    }
}
