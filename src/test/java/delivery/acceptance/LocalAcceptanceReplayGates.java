package delivery.acceptance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Replay exit/outcome gates. Approved expected misses are scored separately and do not fail the gate.
 */
public final class LocalAcceptanceReplayGates {
    public static final Set<String> REQUIRED_REPLAY_PASS = Set.of(
            "Create_account",
            "Login",
            "Duplicate_register",
            "Invalid_login",
            "Jacket_medium_blue",
            "Add_two_products",
            "Change_tote_quantity",
            "Remove_second_remove");

    /**
     * Checkout, order confirmation, and session expiry stay required when a pack runs them.
     * A scoped replay may omit them entirely; a FAIL is never an approved miss.
     */
    public static final Set<String> REQUIRED_WHEN_PRESENT = Set.of(
            "Checkout_required_address",
            "Simulated_payment_order",
            "Order_on_account",
            "Session_expiry_overlay");

    /** Unlabeled glyph remains the only approved miss. */
    public static final Set<String> APPROVED_REPLAY_MISSES = Set.of(
            "Unlabeled_pay_glyph");

    /** Login → cart → Pay later (simulated) → account order → session expiry. */
    public static final List<String> SCOPED_CHECKOUT_CHAIN = List.of(
            "Login",
            "Add_two_products",
            "Simulated_payment_order",
            "Order_on_account",
            "Session_expiry_overlay");

    private static final Set<String> IGNORE_METHODS = Set.of("setUp", "tearDown");

    private LocalAcceptanceReplayGates() {
    }

    public static List<String> violations(LocalAcceptanceReplay.MavenRun run) {
        List<String> out = new ArrayList<>();
        if (run == null) {
            out.add("replay result is missing");
            return out;
        }
        if (run.timedOut()) {
            out.add("replay timed out; process tree was killed");
        }
        if (run.cancelled()) {
            out.add("replay cancelled; process tree was killed");
        }
        if (run.alive()) {
            out.add("replay process still alive after supervisor returned");
        }
        Map<String, String> statuses = run.statuses() == null ? Map.of() : run.statuses();
        for (String required : REQUIRED_REPLAY_PASS) {
            String status = statuses.get(required);
            if (!isPass(status)) {
                out.add("required replay case " + required + " was "
                        + (status == null || status.isBlank() ? "missing" : status));
            }
        }
        for (String required : REQUIRED_WHEN_PRESENT) {
            if (!statuses.containsKey(required)) {
                continue;
            }
            String status = statuses.get(required);
            if (!isPass(status)) {
                out.add("required-when-run case " + required + " was "
                        + (status == null || status.isBlank() ? "blank" : status));
            }
        }
        Set<String> approvedFail = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : statuses.entrySet()) {
            String name = e.getKey();
            if (name == null || IGNORE_METHODS.contains(name)) {
                continue;
            }
            if (!isFail(e.getValue())) {
                continue;
            }
            if (REQUIRED_REPLAY_PASS.contains(name) || REQUIRED_WHEN_PRESENT.contains(name)) {
                continue;
            }
            if (APPROVED_REPLAY_MISSES.contains(name)) {
                approvedFail.add(name);
                continue;
            }
            out.add("unexpected replay failure: " + name + "=" + e.getValue());
        }
        if (run.exitCode() != 0 && !run.timedOut() && !run.cancelled() && !run.alive()
                && !hasRequiredOrUnexpectedFailure(out) && approvedFail.isEmpty()) {
            out.add("unexpected Maven exit code " + run.exitCode()
                    + " without a classified case failure");
        }
        return out;
    }

    /**
     * Every case in {@code requiredInOrder} must PASS. A FAIL records later cases that did not
     * run as blocked rather than as independent misses.
     */
    public static List<String> scopedChainViolations(
            LocalAcceptanceReplay.MavenRun run, List<String> requiredInOrder) {
        List<String> out = new ArrayList<>();
        if (run == null) {
            out.add("replay result is missing");
            return out;
        }
        if (run.timedOut()) {
            out.add("replay timed out; process tree was killed");
        }
        if (run.cancelled()) {
            out.add("replay cancelled; process tree was killed");
        }
        if (run.alive()) {
            out.add("replay process still alive after supervisor returned");
        }
        Map<String, String> statuses = run.statuses() == null ? Map.of() : run.statuses();
        List<String> chain = requiredInOrder == null ? List.of() : requiredInOrder;
        String firstFail = null;
        for (String required : chain) {
            String status = statuses.get(required);
            if (isPass(status)) {
                continue;
            }
            String shown = status == null || status.isBlank() ? "missing" : status;
            if (firstFail == null) {
                firstFail = required + "=" + shown;
                out.add("scoped chain case " + required + " was " + shown);
            } else if (isSkip(status) || status == null || status.isBlank()) {
                out.add("scoped chain case " + required + " blocked after " + firstFail);
            } else {
                out.add("scoped chain case " + required + " was " + shown
                        + " (prerequisite already failed: " + firstFail + ")");
            }
        }
        if (run.exitCode() != 0 && !run.timedOut() && !run.cancelled() && !run.alive()
                && out.isEmpty()) {
            out.add("unexpected Maven exit code " + run.exitCode()
                    + " without a classified chain failure");
        }
        return out;
    }

    private static boolean isSkip(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        return "SKIP".equals(s) || "SKIPPED".equals(s);
    }

    private static boolean hasRequiredOrUnexpectedFailure(List<String> violations) {
        for (String v : violations) {
            if (v.startsWith("required replay case")
                    || v.startsWith("required-when-run case")
                    || v.startsWith("unexpected replay failure")
                    || v.startsWith("unexpected Maven exit")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPass(String status) {
        return status != null && "PASS".equalsIgnoreCase(status.trim());
    }

    private static boolean isFail(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        return "FAIL".equals(s) || "FAILURE".equals(s) || "ERROR".equals(s);
    }
}
