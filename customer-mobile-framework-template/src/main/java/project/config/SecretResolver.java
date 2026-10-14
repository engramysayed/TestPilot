package project.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Resolve secrets from environment ({@code KEEL_SECRET_<KEY>}) or an excluded local secrets file.
 * Never copies secrets into System.getProperties() or command-line arguments.
 * Never embeds plaintext into generated Java or Allure step args.
 */
public final class SecretResolver {

    private SecretResolver() {}

    public static String resolve(String logicalKey) {
        if (logicalKey == null || logicalKey.isBlank()) {
            throw new IllegalArgumentException("secret logicalKey required");
        }
        String envKey = "KEEL_SECRET_" + logicalKey.trim().toUpperCase(Locale.ROOT).replace('.', '_');
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String fileProp = System.getenv("KEEL_MOBILE_SECRETS_FILE");
        if (fileProp == null || fileProp.isBlank()) {
            fileProp = System.getProperty("mobile.secretsFile", "");
        }
        if (fileProp != null && !fileProp.isBlank()) {
            Path path = Path.of(fileProp);
            if (Files.isRegularFile(path)) {
                Properties p = new Properties();
                try (var in = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                    p.load(in);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read secrets file", e);
                }
                String v = p.getProperty(logicalKey);
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        throw new IllegalStateException(
                "Secret not found for ref '" + logicalKey + "'. Set env " + envKey
                        + " or mobile.secretsFile (gitignored; never shipped in ZIP).");
    }
}
