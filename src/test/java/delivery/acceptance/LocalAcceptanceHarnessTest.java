package delivery.acceptance;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Focused harness tests: ProcessSupervisor replay, outcome gates, unique evidence dirs.
 * No Chrome, Ollama, or shop pack.
 */
public class LocalAcceptanceHarnessTest {

    @Test
    public void replayUsesSupervisorDeadlineAndKillsProcessTree() throws Exception {
        Path log = Files.createTempFile("kla-replay-", ".log");
        LocalAcceptanceReplay.MavenRun run = LocalAcceptanceReplay.run(
                hangingBuilder(), Duration.ofMillis(900), log);
        Assert.assertTrue(run.timedOut(), run.log());
        Assert.assertFalse(run.alive(), "process tree must be released");
        Assert.assertTrue(run.cancelled() || run.exitCode() != 0 || run.timedOut());
        String written = Files.readString(log, StandardCharsets.UTF_8);
        Assert.assertFalse(written.isBlank() || written.length() < 1, "log must be persisted");
        List<String> gate = LocalAcceptanceReplayGates.violations(run);
        Assert.assertFalse(gate.isEmpty(), "timeout must fail the replay gate");
        Assert.assertTrue(gate.stream().anyMatch(s -> s.toLowerCase().contains("timeout")
                || s.toLowerCase().contains("timed")), gate.toString());
    }

    @Test
    public void replayExitZeroWithRequiredPassesClearsTheGate() {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (String name : LocalAcceptanceReplayGates.REQUIRED_REPLAY_PASS) {
            statuses.put(name, "PASS");
        }
        statuses.put("Unlabeled_pay_glyph", "FAIL");
        LocalAcceptanceReplay.MavenRun run = new LocalAcceptanceReplay.MavenRun(
                0, statuses, "BUILD SUCCESS", false, false, false);
        Assert.assertTrue(LocalAcceptanceReplayGates.violations(run).isEmpty(),
                LocalAcceptanceReplayGates.violations(run).toString());
    }

    @Test
    public void approvedMissFailureDoesNotFailTheGateWhenExitIsNonZero() {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (String name : LocalAcceptanceReplayGates.REQUIRED_REPLAY_PASS) {
            statuses.put(name, "PASS");
        }
        statuses.put("Unlabeled_pay_glyph", "FAIL");
        LocalAcceptanceReplay.MavenRun run = new LocalAcceptanceReplay.MavenRun(
                1, statuses, "BUILD FAILURE", false, false, false);
        Assert.assertTrue(LocalAcceptanceReplayGates.violations(run).isEmpty(),
                LocalAcceptanceReplayGates.violations(run).toString());
    }

    @Test
    public void unexpectedFailureAndMissingRequiredAreGateViolations() {
        Map<String, String> statuses = new LinkedHashMap<>();
        statuses.put("Login", "PASS");
        statuses.put("Add_two_products", "FAIL");
        LocalAcceptanceReplay.MavenRun unexpected = new LocalAcceptanceReplay.MavenRun(
                1, statuses, "BUILD FAILURE", false, false, false);
        List<String> unexpectedViolations = LocalAcceptanceReplayGates.violations(unexpected);
        Assert.assertTrue(unexpectedViolations.stream().anyMatch(s -> s.contains("Add_two_products")),
                unexpectedViolations.toString());
        Assert.assertTrue(unexpectedViolations.stream().anyMatch(s -> s.contains("required")),
                unexpectedViolations.toString());

        LocalAcceptanceReplay.MavenRun empty = new LocalAcceptanceReplay.MavenRun(
                1, Map.of(), "compiler error", false, false, false);
        List<String> emptyViolations = LocalAcceptanceReplayGates.violations(empty);
        Assert.assertFalse(emptyViolations.isEmpty(), emptyViolations.toString());
        Assert.assertTrue(emptyViolations.stream().anyMatch(s -> s.toLowerCase().contains("exit")
                || s.contains("required")), emptyViolations.toString());

        Map<String, String> allRequiredPass = new LinkedHashMap<>();
        for (String name : LocalAcceptanceReplayGates.REQUIRED_REPLAY_PASS) {
            allRequiredPass.put(name, "PASS");
        }
        LocalAcceptanceReplay.MavenRun pluginFail = new LocalAcceptanceReplay.MavenRun(
                1, allRequiredPass, "BUILD FAILURE", false, false, false);
        List<String> pluginViolations = LocalAcceptanceReplayGates.violations(pluginFail);
        Assert.assertTrue(pluginViolations.stream().anyMatch(s -> s.toLowerCase().contains("exit")),
                pluginViolations.toString());
    }

    @Test
    public void scopedChainRequiresEveryListedCaseToPass() {
        Map<String, String> statuses = new LinkedHashMap<>();
        statuses.put("Login", "PASS");
        statuses.put("Add_two_products", "PASS");
        statuses.put("Simulated_payment_order", "FAIL");
        statuses.put("Order_on_account", "SKIP");
        statuses.put("Session_expiry_overlay", "SKIP");
        LocalAcceptanceReplay.MavenRun run = new LocalAcceptanceReplay.MavenRun(
                1, statuses, "BUILD FAILURE", false, false, false);
        List<String> violations = LocalAcceptanceReplayGates.scopedChainViolations(
                run, LocalAcceptanceReplayGates.SCOPED_CHECKOUT_CHAIN);
        Assert.assertTrue(violations.stream().anyMatch(s -> s.contains("Simulated_payment_order")),
                violations.toString());
        Assert.assertTrue(violations.stream().anyMatch(s -> s.contains("Order_on_account")
                && s.toLowerCase().contains("blocked")), violations.toString());
        Assert.assertTrue(violations.stream().anyMatch(s -> s.contains("Session_expiry_overlay")
                && s.toLowerCase().contains("blocked")), violations.toString());
    }

    @Test
    public void scopedReplayMayOmitCheckoutOrderAndSession() {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (String name : LocalAcceptanceReplayGates.REQUIRED_REPLAY_PASS) {
            statuses.put(name, "PASS");
        }
        LocalAcceptanceReplay.MavenRun run = new LocalAcceptanceReplay.MavenRun(
                0, statuses, "BUILD SUCCESS", false, false, false);
        Assert.assertTrue(LocalAcceptanceReplayGates.violations(run).isEmpty(),
                LocalAcceptanceReplayGates.violations(run).toString());
    }

    @Test
    public void checkoutOrderAndSessionFailuresAreNotApprovedMisses() {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (String name : LocalAcceptanceReplayGates.REQUIRED_REPLAY_PASS) {
            statuses.put(name, "PASS");
        }
        statuses.put("Checkout_required_address", "FAIL");
        statuses.put("Simulated_payment_order", "FAIL");
        statuses.put("Order_on_account", "FAIL");
        statuses.put("Session_expiry_overlay", "FAIL");
        LocalAcceptanceReplay.MavenRun run = new LocalAcceptanceReplay.MavenRun(
                1, statuses, "BUILD FAILURE", false, false, false);
        List<String> violations = LocalAcceptanceReplayGates.violations(run);
        Assert.assertTrue(violations.stream().anyMatch(s -> s.contains("Checkout_required_address")),
                violations.toString());
        Assert.assertTrue(violations.stream().anyMatch(s -> s.contains("Simulated_payment_order")),
                violations.toString());
        Assert.assertTrue(violations.stream().anyMatch(s -> s.contains("Order_on_account")),
                violations.toString());
        Assert.assertTrue(violations.stream().anyMatch(s -> s.contains("Session_expiry_overlay")),
                violations.toString());
    }

    @Test
    public void uniqueEvidenceDirsDoNotOverwriteACompletedRun() throws Exception {
        Path root = Files.createTempDirectory("kla-evidence-");
        Path first = LocalAcceptanceEvidence.createRunDir(root);
        Files.writeString(first.resolve("run-log.txt"), "completed-run", StandardCharsets.UTF_8);
        Path second = LocalAcceptanceEvidence.createRunDir(root);
        Files.writeString(second.resolve("run-log.txt"), "interrupted-run", StandardCharsets.UTF_8);
        Assert.assertNotEquals(first, second);
        Assert.assertEquals(Files.readString(first.resolve("run-log.txt"), StandardCharsets.UTF_8),
                "completed-run");
        Assert.assertEquals(Files.readString(second.resolve("run-log.txt"), StandardCharsets.UTF_8),
                "interrupted-run");
        Assert.assertTrue(Files.isDirectory(first));
        Assert.assertTrue(Files.isDirectory(second));
    }

    private static ProcessBuilder hangingBuilder() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows
                ? new ProcessBuilder("ping", "-n", "40", "127.0.0.1")
                : new ProcessBuilder("sleep", "40");
    }
}
