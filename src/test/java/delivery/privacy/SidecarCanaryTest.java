package delivery.privacy;

import delivery.heal.CursorHealClient;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SidecarCanaryTest {

    private static final String CANARY = "SECRET_CANARY_PASSWORD";

    @Test
    public void cursorSidecarStdinDoesNotContainPasswordCanary() throws Exception {
        Path capture = Files.createTempFile("sidecar-canary", ".json");
        Path script = Path.of("tools/cursor-heal/capture-stdin.mjs");
        Assert.assertTrue(Files.isRegularFile(script), "capture sidecar helper missing");
        String command = "node " + script.toAbsolutePath() + " " + capture.toAbsolutePath();
        String prevAllow = System.getProperty("delivery.provider.allowlist");
        String prevKey = System.getProperty("CURSOR_API_KEY");
        System.setProperty("delivery.provider.allowlist", "cursor");
        System.setProperty("CURSOR_API_KEY", "test-key-not-real");
        try {
            CursorHealClient client = new CursorHealClient(true, command, 20);
            String html = "<form><input type=\"password\" name=\"p\" value=\"" + CANARY + "\"></form>";
            client.pickCandidateId(
                    "type password " + CANARY,
                    "password=" + CANARY,
                    "id\tpassword",
                    html,
                    null,
                    List.of());
            Assert.assertTrue(Files.isRegularFile(capture) && Files.size(capture) > 0,
                    "sidecar must receive stdin");
            String outbound = Files.readString(capture, StandardCharsets.UTF_8);
            Assert.assertFalse(outbound.contains(CANARY), outbound);
        } finally {
            restore("delivery.provider.allowlist", prevAllow);
            restore("CURSOR_API_KEY", prevKey);
        }
    }

    private static void restore(String key, String prev) {
        if (prev == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prev);
        }
    }
}
