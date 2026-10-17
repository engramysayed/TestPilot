package delivery.heal;

import java.nio.file.Path;

/**
 * Resolves heal/LLM API keys for Automation Engine readers.
 *
 * <p>Order (env wins, matching Backend LLM Settings publish semantics):
 * <ol>
 *   <li>Environment variable</li>
 *   <li>System property — portal LLM Settings publish layer ({@code CURSOR_API_KEY},
 *       {@code AGENTROUTER_API_KEY}, {@code OLLAMA_API_KEY})</li>
 *   <li>DEV-only {@link LocalApiKeyLoader} last resort (api-keys.local.*); loader itself
 *       never overwrites env/prop and skips placeholders</li>
 * </ol>
 *
 * <p>Never logs secret values.
 */
public final class ApiKeySource {
    public static final String CURSOR_API_KEY = "CURSOR_API_KEY";
    public static final String AGENTROUTER_API_KEY = "AGENTROUTER_API_KEY";
    public static final String OLLAMA_API_KEY = "OLLAMA_API_KEY";

    private ApiKeySource() {
    }

    /**
     * Resolve a key name. Returns trimmed non-blank value or {@code ""}.
     * Never returns null.
     */
    public static String resolve(String keyName) {
        return resolve(keyName, null);
    }

    /**
     * Same order as {@link #resolve(String)}. When {@code localDevProjectRoot} is non-null,
     * the DEV file layer loads from that root via {@link LocalApiKeyLoader#loadIntoSystemProperties};
     * otherwise {@link LocalApiKeyLoader#loadFromWorkingTree()} walks from {@code user.dir}.
     * Package-visible overload is for unit tests.
     */
    static String resolve(String keyName, Path localDevProjectRoot) {
        if (keyName == null || keyName.isBlank()) {
            return "";
        }
        String env = System.getenv(keyName);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty(keyName);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        if (localDevProjectRoot != null) {
            LocalApiKeyLoader.loadIntoSystemProperties(localDevProjectRoot);
        } else {
            LocalApiKeyLoader.loadFromWorkingTree();
        }
        prop = System.getProperty(keyName);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        return "";
    }

    /** True when {@link #resolve(String)} yields a non-blank value. */
    public static boolean isPresent(String keyName) {
        return !resolve(keyName).isBlank();
    }
}