package delivery.portal.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real Email channel. Sends alerts via Spring's {@link JavaMailSender}.
 *
 * <p>Enabled by {@code keel.alerts.channels.email.enabled=true}. SMTP must be configured
 * ({@code delivery.mail-enabled=true} and the usual {@code spring.mail.*} properties). Recipients come
 * from {@code keel.alerts.channels.email.recipients} (comma-separated) or the
 * {@code KEEL_ALERT_EMAIL_RECIPIENTS} env var. Sender from {@code delivery.mail-from} (reused).
 */
@Component
public class EmailAlertChannel implements AlertChannel {
    private static final Logger LOG = LoggerFactory.getLogger(EmailAlertChannel.class);

    private final boolean enabled;
    private final Alert.Severity minSeverity;
    private final List<String> recipients;
    private final String from;
    private final ObjectProvider<JavaMailSender> mail;
    private final boolean deliverySmtpEnabled;

    public EmailAlertChannel(Environment env, ObjectProvider<JavaMailSender> mail) {
        this.enabled = Boolean.parseBoolean(env.getProperty("keel.alerts.channels.email.enabled", "false"));
        this.minSeverity = Alert.Severity.valueOf(env.getProperty(
                "keel.alerts.channels.email.min-severity", "critical").trim().toUpperCase(Locale.ROOT));
        this.recipients = AlertConfigs.parseList(AlertConfigs.firstNonBlank(
                env.getProperty("keel.alerts.channels.email.recipients"),
                env.getProperty("KEEL_ALERT_EMAIL_RECIPIENTS")));
        this.from = AlertConfigs.firstNonBlank(
                env.getProperty("delivery.mail-from"),
                env.getProperty("keel.alerts.channels.email.from"),
                "noreply@localhost");
        this.deliverySmtpEnabled = Boolean.parseBoolean(env.getProperty("delivery.mail-enabled", "false"));
        this.mail = mail;
    }

    @Override public String id() { return "email"; }
    @Override public boolean enabled() { return enabled; }
    @Override public Alert.Severity minSeverity() { return minSeverity; }

    @Override
    public void send(Alert alert) {
        if (!deliverySmtpEnabled) {
            throw new IllegalStateException(
                    "Email channel enabled but delivery.mail-enabled=false — set it and configure spring.mail.*");
        }
        if (recipients.isEmpty()) {
            throw new IllegalStateException(
                    "Email channel enabled but no recipients (keel.alerts.channels.email.recipients)");
        }
        JavaMailSender sender = mail == null ? null : mail.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException(
                    "Email channel enabled but JavaMailSender is not available — configure spring.mail.host");
        }
        String subject = "[Keel " + alert.severity() + "] " + alert.ruleId() + " — " + alert.subject();
        String body = "Rule: " + alert.ruleId() + "\n"
                + "Subject: " + alert.subject() + "\n"
                + "Severity: " + alert.severity() + "\n"
                + "Kind: " + alert.kind() + "\n"
                + "At: " + alert.at() + "\n\n"
                + alert.message();

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(recipients.toArray(new String[0]));
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
        LOG.info("ALERT_EMAIL: sent rule={} to={} severity={}", alert.ruleId(), recipients, alert.severity());
    }

    public List<String> recipients() { return List.copyOf(recipients); }
    public String from() { return from; }
    public boolean smtpEnabled() { return deliverySmtpEnabled; }

    public List<String> readinessIssues() {
        List<String> out = new ArrayList<>();
        if (!enabled) out.add("disabled");
        if (!deliverySmtpEnabled) out.add("delivery.mail-enabled=false");
        if (recipients.isEmpty()) out.add("missing keel.alerts.channels.email.recipients");
        return out;
    }


}
