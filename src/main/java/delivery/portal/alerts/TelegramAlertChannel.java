package delivery.portal.alerts;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** B12 stub for the Telegram channel (not built yet; AL-1). Off unless keel.alerts.channels.telegram.enabled=true; then it only logs. */
@Component
public class TelegramAlertChannel extends StubAlertChannel {
    public TelegramAlertChannel(Environment env) {
        super("telegram", env);
    }
}
