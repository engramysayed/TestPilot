package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.persistence.Invite;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class InviteMailService {
    private static final Logger log = LogManager.getLogger(InviteMailService.class);

    private final DeliveryPortalProperties props;
    private final ObjectProvider<JavaMailSender> mailSender;

    public InviteMailService(DeliveryPortalProperties props, ObjectProvider<JavaMailSender> mailSender) {
        this.props = props;
        this.mailSender = mailSender;
    }

    public boolean isConfigured() {
        return props.isMailEnabled() && mailSender.getIfAvailable() != null;
    }

    /**
     * @return true if an email was sent; false if mail is disabled (invite link still valid).
     */
    public boolean sendInvite(Invite invite) {
        String acceptUrl = props.getPublicBaseUrl().replaceAll("/$", "") + "/invite/" + invite.getToken();
        if (!props.isMailEnabled()) {
            log.info("Mail disabled — invite for {} accept URL: {}", invite.getEmail(), acceptUrl);
            return false;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("delivery.mail-enabled=true but Spring Mail is not configured (spring.mail.host)");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.getMailFrom());
            helper.setTo(invite.getEmail());
            helper.setSubject("You're invited to Keel");
            helper.setText(
                    "You have been invited to Keel.\n\n"
                            + "Open this link to create your password and join:\n"
                            + acceptUrl + "\n\n"
                            + "This invite expires at: " + invite.getExpiresAt() + "\n",
                    "<p>You have been invited to <strong>Keel</strong>.</p>"
                            + "<p><a href=\"" + acceptUrl + "\">Accept invite and create your password</a></p>"
                            + "<p>Or paste this link into your browser:<br/>" + acceptUrl + "</p>"
                            + "<p>Expires: " + invite.getExpiresAt() + "</p>"
            );
            sender.send(message);
            log.info("Invite email sent to {}", invite.getEmail());
            return true;
        } catch (MessagingException | MailException e) {
            log.error("Failed to send invite email to {}", invite.getEmail(), e);
            throw new IllegalStateException("Failed to send invite email: " + e.getMessage());
        }
    }
}
