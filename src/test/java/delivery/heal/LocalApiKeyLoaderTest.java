package delivery.heal;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class LocalApiKeyLoaderTest {

    @Test
    public void loadsBatKeysIntoSystemPropertiesWithoutOverwritingEnv() throws Exception {
        Path dir = Files.createTempDirectory("tp-keys");
        Path bat = dir.resolve("api-keys.local.bat");
        Files.writeString(bat, """
                REM local keys
                set "CURSOR_API_KEY=crsr_unit_test_only"
                set "AGENTROUTER_API_KEY=sk-unit-test-only"
                set "CURSOR_API_KEY_PLACEHOLDER=PASTE_CURSOR_KEY_HERE"
                """);

        System.clearProperty("CURSOR_API_KEY");
        System.clearProperty("AGENTROUTER_API_KEY");
        int loaded = LocalApiKeyLoader.loadIntoSystemProperties(dir);

        Assert.assertTrue(loaded >= 1);
        Assert.assertEquals(System.getProperty("CURSOR_API_KEY"), "crsr_unit_test_only");
        Assert.assertEquals(System.getProperty("AGENTROUTER_API_KEY"), "sk-unit-test-only");
        System.clearProperty("CURSOR_API_KEY");
        System.clearProperty("AGENTROUTER_API_KEY");
    }

    @Test
    public void skipsPlaceholdersAndMissingFiles() {
        Path missing = Path.of("this-dir-does-not-exist-tp-keys");
        Assert.assertEquals(LocalApiKeyLoader.loadIntoSystemProperties(missing), 0);
        Assert.assertEquals(LocalApiKeyLoader.loadIntoSystemProperties(null), 0);
    }

    @Test
    public void doesNotOverwriteAnExistingSystemProperty() throws Exception {
        Path dir = Files.createTempDirectory("tp-keys-keep");
        Files.writeString(dir.resolve("api-keys.local.bat"),
                "set \"CURSOR_API_KEY=crsr_should_not_win\"\n");
        System.setProperty("CURSOR_API_KEY", "already-set");
        LocalApiKeyLoader.loadIntoSystemProperties(dir);
        Assert.assertEquals(System.getProperty("CURSOR_API_KEY"), "already-set");
        System.clearProperty("CURSOR_API_KEY");
    }
}
