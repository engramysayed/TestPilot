package delivery.portal.alerts;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** B12 stub for the Email channel (not built yet; AL-1). Off unless keel.alerts.channels.email.enabled=true; then it only logs. */
@Component
public class EmailAlertChannel extends StubAlertChannel {
    public EmailAlertChannel(Environment env) {
        super("email", env);
    }
}
