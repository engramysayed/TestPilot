package delivery.job;

import delivery.portal.alerts.Alert;
import delivery.portal.alerts.AlertChannel;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SuiteAlertNotifierTest {

    @AfterMethod
    public void clear() {
        SuiteRunContext.clear();
        SuiteAlertNotifier.clearChannels();
        SuiteAlertNotifier.registerMemberAlerts(null);
    }

    @Test
    public void membersGetAlertEvenWhenUnboundAndSuiteFloorApplies() {
        List<String> seen = new ArrayList<>();
        SuiteAlertNotifier.registerMemberAlerts((projectId, alert) -> seen.add(projectId + ":" + alert.severity()));
        SuiteRunContext.activate(new SuiteRunContext("p1", "s1", null, null, "schedule", null, null));
        SuiteAlertNotifier.notifyOutcome(49, 1, 0, "");
        Assert.assertEquals(seen, List.of("p1:WARNING"), "unbound suite still reaches members");

        seen.clear();
        SuiteRunContext.activate(new SuiteRunContext("p1", "s1", null, null, "schedule", null, "CRITICAL", null));
        SuiteAlertNotifier.notifyOutcome(49, 1, 0, "");
        Assert.assertTrue(seen.isEmpty(), "suite floor CRITICAL drops a WARNING");
        SuiteAlertNotifier.notifyOutcome(0, 2, 0, "");
        Assert.assertEquals(seen, List.of("p1:CRITICAL"));
    }

    @Test
    public void boundChannelAndMembersBothFireOnOutcomeAndError() {
        AtomicInteger channelSends = new AtomicInteger();
        List<String> members = new ArrayList<>();
        SuiteAlertNotifier.registerChannels(List.of(new AlertChannel() {
            @Override public String id() { return "stub"; }
            @Override public boolean enabled() { return true; }
            @Override public Alert.Severity minSeverity() { return Alert.Severity.WARNING; }
            @Override public void send(Alert alert) { channelSends.incrementAndGet(); }
        }));
        SuiteAlertNotifier.registerMemberAlerts((projectId, alert) -> members.add(projectId + ":" + alert.ruleId()));
        SuiteRunContext ctx = new SuiteRunContext("p2", "s2", null, null, "schedule", "stub", null);
        SuiteRunContext.activate(ctx);
        SuiteAlertNotifier.notifyOutcome(0, 1, 0, "all failed");
        Assert.assertEquals(channelSends.get(), 1, "bound channel still receives the alert");
        Assert.assertEquals(members, List.of("p2:SUITE_RUN_FAILED"), "members also get the bound alert");

        members.clear();
        SuiteAlertNotifier.notifyError(ctx, SuiteAlertNotifier.RULE_SUITE_RUN_ERROR, "boom");
        Assert.assertEquals(members, List.of("p2:SUITE_RUN_ERROR"), "notifyError also reaches members");
    }

    @Test
    public void unboundLogsOnlyNoThrow() {
        SuiteRunContext.activate(new SuiteRunContext("p", "s1", null, null, "schedule", null, null));
        SuiteAlertNotifier.notifyOutcome(0, 2, 0, "all failed");
    }

    @Test
    public void boundChannelReceivesFail() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        List<Alert> seen = new ArrayList<>();
        SuiteAlertNotifier.registerChannels(List.of(new AlertChannel() {
            @Override public String id() { return "stub"; }
            @Override public boolean enabled() { return true; }
            @Override public Alert.Severity minSeverity() { return Alert.Severity.WARNING; }
            @Override public void send(Alert alert) { sends.incrementAndGet(); seen.add(alert); }
        }));
        SuiteRunContext.activate(new SuiteRunContext("p", "s1", null, null, "schedule", "stub", null));
        SuiteAlertNotifier.notifyOutcome(0, 3, 1, "boom");
        Assert.assertTrue(sends.get() >= 1);
        Assert.assertTrue(seen.stream().anyMatch(a -> SuiteAlertNotifier.RULE_SUITE_RUN_FAILED.equals(a.ruleId())));
        Assert.assertTrue(seen.stream().anyMatch(a -> SuiteAlertNotifier.RULE_SUITE_RUN_FLAKY.equals(a.ruleId())));
    }

    @Test
    public void partialFailureAlertsAsWarning() {
        List<Alert> seen = new ArrayList<>();
        SuiteAlertNotifier.registerChannels(List.of(new AlertChannel() {
            @Override public String id() { return "stub"; }
            @Override public boolean enabled() { return true; }
            @Override public Alert.Severity minSeverity() { return Alert.Severity.WARNING; }
            @Override public void send(Alert alert) { seen.add(alert); }
        }));
        SuiteRunContext.activate(new SuiteRunContext("p", "regression", null, null, "schedule", "stub", null));
        SuiteAlertNotifier.notifyOutcome(49, 1, 0, "");
        Assert.assertEquals(seen.size(), 1, "1 of 50 failing must alert");
        Assert.assertEquals(seen.get(0).severity(), Alert.Severity.WARNING);
        seen.clear();
        SuiteAlertNotifier.notifyOutcome(50, 0, 0, "");
        Assert.assertTrue(seen.isEmpty(), "green run is quiet");
    }

    @Test
    public void contextSurvivesThreadHopViaFile() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("suite-ctx-");
        SuiteRunContextFiles.write(dir, new SuiteRunContext("p", "s9", null, List.of("TC_1"), "monitor", "slack", "prod"));
        SuiteRunContext back = SuiteRunContextFiles.read(dir).orElseThrow();
        Assert.assertEquals(back.suiteId(), "s9");
        Assert.assertEquals(back.alertChannelId(), "slack");
        Assert.assertTrue(back.isMonitor());
        Assert.assertEquals(back.monitorTarget(), "prod");
    }
}
