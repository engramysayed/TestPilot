package delivery.portal.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real Telegram channel. Calls {@code https://api.telegram.org/bot<TOKEN>/sendMessage} for each chat id.
 *
 * <p>Enabled by {@code keel.alerts.channels.telegram.enabled=true}. Bot token from
 * {@code keel.alerts.telegram.bot-token} (already defined for account alerts) or {@code KEEL_TELEGRAM_BOT_TOKEN}.
 * Chat ids from {@code keel.alerts.channels.telegram.chat-ids} or {@code KEEL_TELEGRAM_CHAT_IDS}.
 */
@Component
public class TelegramAlertChannel implements AlertChannel {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramAlertChannel.class);
    private static final String API_BASE = "https://api.telegram.org";

    private final boolean enabled;
    private final Alert.Severity minSeverity;
    private final String botToken;
    private final List<String> chatIds;
    private final HttpClient http;

    public TelegramAlertChannel(Environment env) {
        this.enabled = Boolean.parseBoolean(env.getProperty("keel.alerts.channels.telegram.enabled", "false"));
        this.minSeverity = Alert.Severity.valueOf(env.getProperty(
                "keel.alerts.channels.telegram.min-severity", "warning").trim().toUpperCase(Locale.ROOT));
        String token = env.getProperty("keel.alerts.telegram.bot-token", "");
        if (token == null || token.isBlank()) {
            token = env.getProperty("KEEL_TELEGRAM_BOT_TOKEN", "");
        }
        this.botToken = token == null ? "" : token.trim();
        this.chatIds = AlertConfigs.parseList(AlertConfigs.firstNonBlank(
                env.getProperty("keel.alerts.channels.telegram.chat-ids"),
                env.getProperty("KEEL_TELEGRAM_CHAT_IDS")));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override public String id() { return "telegram"; }
    @Override public boolean enabled() { return enabled; }
    @Override public Alert.Severity minSeverity() { return minSeverity; }

    @Override
    public void send(Alert alert) throws Exception {
        if (botToken.isBlank()) {
            throw new IllegalStateException(
                    "Telegram channel enabled but keel.alerts.telegram.bot-token / KEEL_TELEGRAM_BOT_TOKEN is not set");
        }
        if (chatIds.isEmpty()) {
            throw new IllegalStateException(
                    "Telegram channel enabled but no chat ids (keel.alerts.channels.telegram.chat-ids)");
        }
        String url = API_BASE + "/bot" + botToken + "/sendMessage";
        String emoji = alert.severity() == Alert.Severity.CRITICAL ? "🚨" : "⚠️";
        String text = emoji + " *[" + alert.severity() + "]* " + alert.subject()
                + "\n_" + alert.ruleId() + "_\n" + alert.message();
        List<String> errors = new ArrayList<>();
        for (String chat : chatIds) {
            String payload = "{\"chat_id\":" + jsonString(chat)
                    + ",\"text\":" + jsonString(text)
                    + ",\"parse_mode\":\"Markdown\"}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                errors.add("chat=" + chat + " HTTP " + resp.statusCode() + " " + trim(resp.body()));
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Telegram sendMessage failed: " + String.join("; ", errors));
        }
        LOG.info("ALERT_TELEGRAM: sent rule={} chats={} severity={}", alert.ruleId(), chatIds.size(),
                alert.severity());
    }

    public List<String> chatIds() { return List.copyOf(chatIds); }
    public boolean tokenPresent() { return !botToken.isBlank(); }

    public List<String> readinessIssues() {
        List<String> out = new ArrayList<>();
        if (!enabled) out.add("disabled");
        if (botToken.isBlank()) out.add("missing keel.alerts.telegram.bot-token");
        if (chatIds.isEmpty()) out.add("missing keel.alerts.channels.telegram.chat-ids");
        return out;
    }



    private static String trim(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"', '\\' -> { sb.append('\\'); sb.append(c); }
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
