package delivery.heal;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolution order: env (if set in process) → System property → DEV LocalApiKeyLoader.
 * Uses unique key names so real CURSOR_/AGENTROUTER_/OLLAMA_ env vars do not interfere.
 * Never asserts or logs secret values from the ambient environment.
 */
public class ApiKeySourceTest {

    @Test
    public void resolve_returnsEmptyForNullOrBlankName() {
        Assert.assertEquals(ApiKeySource.resolve(null), "");
        Assert.assertEquals(ApiKeySource.resolve("  "), "");
    }

    @Test
    public void resolve_prefersSystemPropertyOverLocalDevFile() throws Exception {
        String key = "KEEL_UNIT_API_KEY_SRC_PROP";
        if (System.getenv(key) != null && !System.getenv(key).isBlank()) {
            Assert.assertTrue(true, "skip: ambient env set for synthetic key");
            return;
        }
        Path dir = Files.createTempDirectory("api-key-src-prop");
        Files.writeString(dir.resolve("api-keys.local.bat"),
                "set \"" + key + "=from-dev-file\"\n");
        String previous = System.getProperty(key);
        System.setProperty(key, "from-portal-prop");
        try {
            Assert.assertEquals(ApiKeySource.resolve(key, dir), "from-portal-prop");
        } finally {
            restoreProp(key, previous);
        }
    }

    @Test
    public void resolve_usesLocalDevFileWhenPropBlank() throws Exception {
        String key = "KEEL_UNIT_API_KEY_SRC_FILE";
        if (System.getenv(key) != null && !System.getenv(key).isBlank()) {
            return;
        }
        Path dir = Files.createTempDirectory("api-key-src-file");
        Files.writeString(dir.resolve("api-keys.local.bat"),
                "set \"" + key + "=from-dev-file-only\"\n");
        String previous = System.getProperty(key);
        System.clearProperty(key);
        try {
            Assert.assertEquals(ApiKeySource.resolve(key, dir), "from-dev-file-only");
        } finally {
            restoreProp(key, previous);
            System.clearProperty(key);
        }
    }

    @Test
    public void resolve_skipsPlaceholderInLocalDevFile() throws Exception {
        String key = "KEEL_UNIT_API_KEY_SRC_PH";
        if (System.getenv(key) != null && !System.getenv(key).isBlank()) {
            return;
        }
        Path dir = Files.createTempDirectory("api-key-src-ph");
        Files.writeString(dir.resolve("api-keys.local.bat"),
                "set \"" + key + "=PASTE_YOUR_KEY_HERE\"\n");
        String previous = System.getProperty(key);
        System.clearProperty(key);
        try {
            Assert.assertEquals(ApiKeySource.resolve(key, dir), "");
        } finally {
            restoreProp(key, previous);
        }
    }

    @Test
    public void isPresent_falseWhenUnresolved() {
        String key = "KEEL_UNIT_API_KEY_SRC_MISSING_ZZZ";
        if (System.getenv(key) != null && !System.getenv(key).isBlank()) {
            return;
        }
        System.clearProperty(key);
        Assert.assertFalse(ApiKeySource.isPresent(key));
    }

    @Test
    public void knownKeyConstants_matchPublishNames() {
        Assert.assertEquals(ApiKeySource.CURSOR_API_KEY, "CURSOR_API_KEY");
        Assert.assertEquals(ApiKeySource.AGENTROUTER_API_KEY, "AGENTROUTER_API_KEY");
        Assert.assertEquals(ApiKeySource.OLLAMA_API_KEY, "OLLAMA_API_KEY");
    }

    private static void restoreProp(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
}