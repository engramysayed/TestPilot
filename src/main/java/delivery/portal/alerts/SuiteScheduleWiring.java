package delivery.portal.alerts;

import delivery.job.SuiteAlertNotifier;
import delivery.portal.service.AccountAlertService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.SuiteDiskStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup wiring for scheduled suites:
 * <ul>
 *   <li>Registers the portal's {@link AlertChannel} beans with {@link SuiteAlertNotifier}. Without this the notifier's
 *       channel list was empty in the portal, so every suite alert was logged only ("channel not registered").</li>
 *   <li>Sends each suite alert to every project member's saved account alert settings too.</li>
 *   <li>Applies {@code delivery.suites.schedule-zone} (default zone for cron schedules without their own timezone) and
 *       {@code delivery.suites.misfire-grace-minutes} (skip slots missed while Keel was down for longer than this).</li>
 * </ul>
 */
@Component
public class SuiteScheduleWiring {
    private static final Logger log = LoggerFactory.getLogger(SuiteScheduleWiring.class);

    private final List<AlertChannel> channels;
    private final String zone;
    private final long misfireGraceMinutes;
    private final PortalStore store;
    private final AccountAlertService accountAlerts;

    public SuiteScheduleWiring(
            List<AlertChannel> channels,
            PortalStore store,
            AccountAlertService accountAlerts,
            @Value("${delivery.suites.schedule-zone:Africa/Cairo}") String zone,
            @Value("${delivery.suites.misfire-grace-minutes:60}") long misfireGraceMinutes
    ) {
        this.channels = channels == null ? List.of() : List.copyOf(channels);
        this.zone = zone;
        this.misfireGraceMinutes = misfireGraceMinutes;
        this.store = store;
        this.accountAlerts = accountAlerts;
    }

    @PostConstruct
    void wire() {
        SuiteAlertNotifier.registerChannels(channels);
        // Members are looked up on the calling thread; the sends run async on webhookExecutor.
        SuiteAlertNotifier.registerMemberAlerts((projectId, alert) ->
                accountAlerts.notifySuiteAlert(store.projectMemberIds(projectId), projectId, alert));
        SuiteDiskStore.configure(zone, misfireGraceMinutes);
        log.info("Suite schedules: {} alert channel(s), default zone {}, misfire grace {} min",
                channels.size(), SuiteDiskStore.defaultZone(), misfireGraceMinutes);
    }

    @PreDestroy
    void unwire() {
        // Drop the hook so a context rebuild (e.g. a @DirtiesContext test) doesn't leave an
        // old AccountAlertService wired into the static notifier.
        SuiteAlertNotifier.registerMemberAlerts(null);
    }
}
