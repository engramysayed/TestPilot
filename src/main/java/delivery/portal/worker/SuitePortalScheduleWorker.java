package delivery.portal.worker;

import delivery.portal.service.SuiteScheduleDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "delivery.suites.portal-schedule-worker.enabled", havingValue = "true")
public class SuitePortalScheduleWorker {

    private static final Logger log = LoggerFactory.getLogger(SuitePortalScheduleWorker.class);

    private final SuiteScheduleDispatchService dispatch;
    private final int withinSeconds;

    public SuitePortalScheduleWorker(
            SuiteScheduleDispatchService dispatch,
            @Value("${delivery.suites.portal-schedule-worker.within-seconds:0}") int withinSeconds
    ) {
        this.dispatch = dispatch;
        this.withinSeconds = Math.max(0, withinSeconds);
    }

    @Scheduled(fixedDelayString = "${delivery.suites.portal-schedule-worker.fixed-delay-ms:30000}")
    public void tick() {
        try {
            int n = dispatch.dispatchDue(withinSeconds);
            if (n > 0) {
                log.info("SCHEDULE_WORKER: dispatched {} suite run(s)", n);
            }
        } catch (Exception e) {
            log.warn("SCHEDULE_WORKER: tick failed: {}", e.getMessage());
        }
    }
}
