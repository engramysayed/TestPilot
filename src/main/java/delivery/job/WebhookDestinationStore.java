package delivery.job;

import delivery.identity.PublicationLock;
import delivery.portal.security.JobSecretCrypto;
import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Explicit project-level job-status webhook destination. Issue trackers are out of scope. */
public final class WebhookDestinationStore {
    public record Config(String url, String secret, boolean enabled) {
    }

    private final Path file;

    public WebhookDestinationStore(Path file) {
        this.file = file;
    }

    public Config put(String url, String secret) throws Exception {
        String normalized = requireHttpUrl(url);
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("webhook secret is required");
        }
        JSONObject row = new JSONObject();
        row.put("url", normalized);
        row.put("secretCipher", JobSecretCrypto.encrypt(secret));
        row.put("enabled", true);
        PublicationLock.call(file.resolveSibling("webhook.lock"), () -> {
            Files.createDirectories(file.getParent());
            Files.writeString(file, row.toString(2), StandardCharsets.UTF_8);
            return null;
        });
        return new Config(normalized, secret, true);
    }

    public Optional<Config> get() throws Exception {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        JSONObject row = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
        String url = row.optString("url", "");
        if (url.isBlank() || !row.optBoolean("enabled", false)) {
            return Optional.empty();
        }
        String secret = JobSecretCrypto.decrypt(row.optString("secretCipher", ""));
        if (secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Config(url, secret, true));
    }

    public JSONObject describe() throws Exception {
        JSONObject out = new JSONObject();
        if (!Files.isRegularFile(file)) {
            out.put("enabled", false);
            out.put("url", "");
            out.put("secretConfigured", false);
            return out;
        }
        JSONObject row = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
        String url = row.optString("url", "");
        boolean enabled = row.optBoolean("enabled", false) && !url.isBlank();
        out.put("enabled", enabled);
        out.put("url", url);
        out.put("secretConfigured", !row.optString("secretCipher", "").isBlank());
        return out;
    }

    public void clear() throws Exception {
        PublicationLock.call(file.resolveSibling("webhook.lock"), () -> {
            Files.deleteIfExists(file);
            return null;
        });
    }

    static String requireHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("webhook url is required");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("webhook url is invalid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("webhook url must be http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("webhook url host is required");
        }
        return uri.toString();
    }
}
