package delivery.portal.alerts;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** B12: defaults in the real context: dead-man off, 5 minutes, three stub channels all off; heartbeats read from the store. */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:alert-wiring;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.store-root=./target/test-delivery-store-alerts",
        "delivery.work-dir=./target/test-delivery-work-alerts"
})
public class AlertWiringTest extends AbstractTestNGSpringContextTests {
    @Autowired AlertEvaluator evaluator;
    @Autowired List<AlertChannel> channels;
    @Autowired EngineHeartbeats heartbeats;

    @Test
    public void offByDefaultWithStubChannels() throws Exception {
        Assert.assertFalse(evaluator.settings().enabled(), "AL-1 dead-man is off in 0a");
        Assert.assertEquals(evaluator.settings().deadman(), Duration.ofMinutes(5));
        Assert.assertEquals(channels.stream().map(AlertChannel::id).collect(Collectors.toSet()),
                Set.of("telegram", "email"));
        Assert.assertTrue(channels.stream().noneMatch(AlertChannel::enabled));
        Assert.assertEquals(evaluator.evaluate(), List.of());
        Assert.assertNotNull(heartbeats.engines());
    }
}
