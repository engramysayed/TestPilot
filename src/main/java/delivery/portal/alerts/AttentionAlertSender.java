package delivery.portal.alerts;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sends an account alert to the channels that user turned on.
 * Engine dead-man stubs stay separate and off.
 */
public final class AttentionAlertSender {
    public static final String CONFIRMATION =
            "Keel alerts are on for this account. You will get a message when a run fails or finishes with a block.";

    public record Prefs(
            boolean telegramOn,
            String telegramChat,
            boolean emailOn,
            String emailTo
    ) {
        public static final Prefs OFF = new Prefs(false, "", false, "");

        public boolean anyOn() {
            return telegramOn || emailOn;
        }

        public String toJson() {
            JSONObject o = new JSONObject();
            o.put("telegramOn", telegramOn);
            o.put("telegramChat", telegramChat == null ? "" : telegramChat);
            o.put("emailOn", emailOn);
            o.put("emailTo", emailTo == null ? "" : emailTo);
            return o.toString();
        }

        public static Prefs parse(String json) {
            if (json == null || json.isBlank()) {
                return OFF;
            }
            JSONObject o = new JSONObject(json);
            return new Prefs(
                    o.optBoolean("telegramOn"),
                    o.optString("telegramChat", "").trim(),
                    o.optBoolean("emailOn"),
                    o.optString("emailTo", "").trim());
        }

        public static Prefs fromRequest(Map<String, Object> body) {
            if (body == null) {
                return OFF;
            }
            return new Prefs(
                    flag(body.get("telegram")),
                    text(body.get("telegram")),
                    flag(body.get("email")),
                    text(body.get("email")));
        }

        private static boolean flag(Object channel) {
            if (!(channel instanceof Map<?, ?> map)) {
                return false;
            }
            Object on = map.get("on");
            return Boolean.TRUE.equals(on) || "true".equalsIgnoreCase(String.valueOf(on));
        }

        private static String text(Object channel) {
            if (!(channel instanceof Map<?, ?> map) || map.get("target") == null) {
                return "";
            }
            return String.valueOf(map.get("target")).trim();
        }
    }

    public record Config(String botToken, boolean mailEnabled, String mailFrom) {
        public String token() {
            return botToken == null ? "" : botToken.trim();
        }
    }

    public record Result(List<String> sent, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public interface Transport {
        String postBody(String url, String json) throws Exception;

        void sendMail(String from, String to, String subject, String text) throws Exception;
    }

    public static boolean newlyNeedsAttention(String previousStatus, String status) {
        if (status == null || status.equals(previousStatus)) {
            return false;
        }
        return "FAILED".equals(status) || "COMPLETED_WITH_BLOCK".equals(status);
    }

    public static String attentionText(String baseUrl, String kind, String status, String projectId, String jobId, String detail) {
        String what = switch (kind == null ? "" : kind) {
            case "EXECUTE" -> "Execute";
            case "CONVERT" -> "Automate";
            case "HUNT" -> "Bug Hunter";
            case "GENERATE" -> "Generate";
            default -> "This";
        };
        String outcome = "FAILED".equals(status) ? "failed" : "finished with a block";
        StringBuilder sb = new StringBuilder();
        sb.append(what).append(" run ").append(outcome).append(".\n");
        sb.append("Project ").append(projectId == null ? "" : projectId);
        if (jobId != null && !jobId.isBlank()) {
            sb.append(" · ").append(jobId);
        }
        sb.append('\n');
        if (safeDetail(detail)) {
            sb.append(detail.strip()).append('\n');
        }
        String root = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (jobId != null && !jobId.isBlank()) {
            sb.append(root).append("/status?jobId=").append(jobId);
        }
        return sb.toString().strip();
    }

    public Result sendEach(Prefs prefs, String text, Config config, Transport transport) {
        List<String> sent = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (prefs == null || !prefs.anyOn()) {
            return new Result(sent, errors);
        }
        String body = text == null ? "" : text;
        if (body.length() > 3500) {
            body = body.substring(0, 3500);
        }
        if (prefs.telegramOn()) {
            try {
                sendTelegram(prefs.telegramChat(), body, config, transport);
                sent.add("Telegram");
            } catch (Exception e) {
                errors.add(e.getMessage() == null ? "Telegram failed" : e.getMessage());
            }
        }
        if (prefs.emailOn()) {
            try {
                sendEmail(prefs.emailTo(), body, config, transport);
                sent.add("Email");
            } catch (Exception e) {
                errors.add(e.getMessage() == null ? "Email failed" : e.getMessage());
            }
        }
        return new Result(sent, errors);
    }

    public String readinessReason(String channel, Config config) {
        if ("telegram".equals(channel)) {
            return config != null && !config.token().isBlank()
                    ? ""
                    : "The server has no Telegram bot token (keel.alerts.telegram.bot-token).";
        }
        if ("email".equals(channel)) {
            return config != null && config.mailEnabled()
                    ? ""
                    : "Mail is turned off on this server (delivery.mail-enabled).";
        }
        return "";
    }

    private static void sendTelegram(String chat, String text, Config config, Transport transport) throws Exception {
        if (config == null || config.token().isBlank()) {
            throw new IllegalArgumentException(
                    "Telegram is not set up on this server. Set keel.alerts.telegram.bot-token.");
        }
        if (!config.token().matches("[0-9]+:[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("The Telegram bot token on this server is not valid.");
        }
        if (chat == null || !chat.matches("(@[A-Za-z0-9_]{4,}|-?\\d{1,20})")) {
            throw new IllegalArgumentException("Telegram needs a chat id or an @group name.");
        }
        String url = "https://api.telegram.org/bot" + config.token() + "/sendMessage";
        JSONObject payload = new JSONObject();
        payload.put("chat_id", chat);
        payload.put("text", text);
        String response;
        try {
            response = transport.postBody(url, payload.toString());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Telegram could not be reached.");
        }
        if (response == null || !response.contains("\"ok\":true") && !response.contains("\"ok\": true")) {
            throw new IllegalArgumentException("Telegram did not accept the message" + telegramWhy(response));
        }
    }

    private static void sendEmail(String to, String text, Config config, Transport transport) throws Exception {
        if (config == null || !config.mailEnabled()) {
            throw new IllegalArgumentException(
                    "Email is not set up on this server. Turn on delivery.mail-enabled and set spring.mail.host.");
        }
        if (to == null || !to.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Email needs a valid address.");
        }
        String from = config.mailFrom() == null || config.mailFrom().isBlank()
                ? "noreply@localhost"
                : config.mailFrom().trim();
        String subject = text.lines().findFirst().orElse("Keel alert");
        if (subject.length() > 120) {
            subject = subject.substring(0, 120);
        }
        transport.sendMail(from, to, subject, text);
    }

    private static boolean safeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return false;
        }
        String lower = detail.toLowerCase(Locale.ROOT);
        return !lower.contains("password") && !lower.contains("secret") && !lower.contains("token");
    }

    private static String telegramWhy(String response) {
        if (response == null || response.isBlank()) {
            return ".";
        }
        try {
            String description = new JSONObject(response).optString("description", "");
            if (description.isBlank() || description.toLowerCase(Locale.ROOT).contains("bot")) {
                return ".";
            }
            if (description.length() > 120) {
                description = description.substring(0, 120);
            }
            return ": " + description;
        } catch (Exception e) {
            return ".";
        }
    }
}
