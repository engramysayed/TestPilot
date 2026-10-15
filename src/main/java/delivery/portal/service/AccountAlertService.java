package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.alerts.AttentionAlertSender;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AccountAlertService {
    private static final Logger log = LogManager.getLogger(AccountAlertService.class);

    private final PortalUserRepository users;
    private final DeliveryPortalProperties props;
    private final org.springframework.core.env.Environment env;
    private final AttentionAlertSender sender;
    private final AttentionAlertSender.Transport transport;

    @Autowired
    public AccountAlertService(
            PortalUserRepository users,
            DeliveryPortalProperties props,
            org.springframework.core.env.Environment env,
            ObjectProvider<JavaMailSender> mail
    ) {
        this(users, props, env, new AttentionAlertSender(), new HttpAlertTransport(mail));
    }

    AccountAlertService(
            PortalUserRepository users,
            DeliveryPortalProperties props,
            org.springframework.core.env.Environment env,
            AttentionAlertSender sender,
            AttentionAlertSender.Transport transport
    ) {
        this.users = users;
        this.props = props;
        this.env = env;
        this.sender = sender;
        this.transport = transport;
    }

    public Map<String, Object> view(Long userId) {
        AttentionAlertSender.Prefs prefs = load(userId);
        AttentionAlertSender.Config config = config();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("telegram", channel(prefs.telegramOn(), prefs.telegramChat(), sender.readinessReason("telegram", config)));
        out.put("email", channel(prefs.emailOn(), prefs.emailTo(), sender.readinessReason("email", config)));
        out.put("slack", channel(prefs.slackOn(), prefs.slackWebhook(), ""));
        out.put("accountEmail", user(userId).getEmail());
        return out;
    }

    @Transactional
    public Map<String, Object> save(Long userId, Map<String, Object> body) {
        AttentionAlertSender.Prefs prefs = AttentionAlertSender.Prefs.fromRequest(body);
        AttentionAlertSender.Result result = sender.sendEach(
                prefs, AttentionAlertSender.CONFIRMATION, config(), transport);
        if (!result.ok()) {
            throw new IllegalArgumentException(String.join(" ", result.errors()));
        }
        PortalUser user = user(userId);
        user.setAlertPrefs(prefs.toJson());
        users.save(user);
        String message = prefs.anyOn()
                ? "Saved. A test message was sent to " + String.join(" and ", result.sent()) + "."
                : "Saved. Alerts are off.";
        return Map.of("status", "ok", "message", message);
    }

    /** Called after a job reaches a state that needs attention. Failures are logged, not thrown. */
    @Async("webhookExecutor")
    public void notifyAttention(JobRecord job, String previousStatus) {
        if (job == null || job.getOwnerUserId() == null || job.getStatus() == null) {
            return;
        }
        if (!AttentionAlertSender.newlyNeedsAttention(previousStatus, job.getStatus().name())) {
            return;
        }
        try {
            AttentionAlertSender.Prefs prefs = load(job.getOwnerUserId());
            if (!prefs.anyOn()) {
                return;
            }
            String detail = job.getMessage();
            String text = AttentionAlertSender.attentionText(
                    props.getPublicBaseUrl(),
                    job.getJobKind() == null ? "" : job.getJobKind().name(),
                    job.getStatus().name(),
                    job.getProjectId(),
                    job.getJobId(),
                    detail);
            AttentionAlertSender.Result result = sender.sendEach(prefs, text, config(), transport);
            if (!result.sent().isEmpty()) {
                log.info("Attention alert sent for {} via {}", job.getJobId(), result.sent());
            }
            if (!result.errors().isEmpty()) {
                log.warn("Attention alert incomplete for {}: {}", job.getJobId(), result.errors());
            }
        } catch (Exception e) {
            log.warn("Attention alert skipped for {}: {}", job.getJobId(), e.getMessage());
        }
    }

    private AttentionAlertSender.Prefs load(Long userId) {
        return AttentionAlertSender.Prefs.parse(user(userId).getAlertPrefs());
    }

    private PortalUser user(Long userId) {
        return users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private AttentionAlertSender.Config config() {
        String token = env.getProperty("keel.alerts.telegram.bot-token", "");
        if (token == null || token.isBlank()) {
            token = env.getProperty("KEEL_TELEGRAM_BOT_TOKEN", "");
        }
        boolean mail = props.isMailEnabled();
        return new AttentionAlertSender.Config(token, mail, props.getMailFrom());
    }

    private static Map<String, Object> channel(boolean on, String target, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("on", on);
        row.put("target", target == null ? "" : target);
        row.put("ready", reason == null || reason.isBlank());
        row.put("reason", reason == null ? "" : reason);
        return row;
    }

    static final class HttpAlertTransport implements AttentionAlertSender.Transport {
        private final ObjectProvider<JavaMailSender> mail;
        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpAlertTransport(ObjectProvider<JavaMailSender> mail) {
            this.mail = mail;
        }

        @Override
        public int postJson(String url, String json) throws Exception {
            HttpResponse<String> response = send(url, json);
            return response.statusCode();
        }

        @Override
        public String postBody(String url, String json) throws Exception {
            HttpResponse<String> response = send(url, json);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "{\"ok\":false}";
            }
            return response.body();
        }

        @Override
        public void sendMail(String from, String to, String subject, String text) {
            JavaMailSender sender = mail == null ? null : mail.getIfAvailable();
            if (sender == null) {
                throw new IllegalArgumentException(
                        "Email is not set up on this server. Set spring.mail.host.");
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
        }

        private HttpResponse<String> send(String url, String json) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
