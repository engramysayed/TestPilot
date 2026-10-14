package delivery.portal.alerts;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** B12 stub for the Slack channel (not built yet; AL-1). Off unless keel.alerts.channels.slack.enabled=true; then it only logs. */
@Component
public class SlackAlertChannel extends StubAlertChannel {
    public SlackAlertChannel(Environment env) {
        super("slack", env);
    }
}
