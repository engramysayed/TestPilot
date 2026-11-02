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
}
