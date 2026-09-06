package delivery.heal;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class CursorHealClientTest {

    @Test
    public void parseCandidateIdFromStdoutJson() {
        Assert.assertEquals(CursorHealClient.parseCandidateId("{\"candidateId\":\"c3\"}"), "c3");
        Assert.assertEquals(
                CursorHealClient.parseCandidateId("log line\n{\"candidateId\":\"c7\"}\n"),
                "c7");
        Assert.assertEquals(
                CursorHealClient.parseCandidateId(
                        "Thought: the visible control matches.\nAction: {\"candidateId\":\"c9\"}"),
                "c9");
        Assert.assertEquals(CursorHealClient.parseCandidateId("no json"), "");
    }

    @Test
    public void skippedWithoutApiKeyReturnsBlank() {
        CursorHealClient client = new CursorHealClient(true, "node -e \"process.exit(0)\"", 2);
        // No CURSOR_API_KEY in typical unit env → blank
        String id = client.pickCandidateId("click", "fail", "c1|css|#x|btn|X", "<html/>", null);
        Assert.assertEquals(id, "");
    }

    @Test
    public void invoke_timesOutWhileStdoutRemainsOpen() throws Exception {
        Path script = Files.createTempFile("cursor-heal-timeout", ".mjs");
        Files.writeString(script, """
                process.stdin.resume();
                process.stdin.on("end", () => setTimeout(() => {}, 3000));
                """);
        String previous = System.getProperty("CURSOR_API_KEY");
        System.setProperty("CURSOR_API_KEY", "test-key");
        try {
            CursorHealClient client = new CursorHealClient(
                    true, "node \"" + script.toAbsolutePath() + "\"", 1);
            long started = System.nanoTime();

            String id = client.pickCandidateId(
                    "click", "fail", "c1|css|#x|btn|X", "<html/>", null);

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            Assert.assertEquals(id, "");
            Assert.assertTrue(elapsedMs < 2500,
                    "timeout was applied only after stdout EOF; elapsed=" + elapsedMs + "ms");
        } finally {
            if (previous == null) {
                System.clearProperty("CURSOR_API_KEY");
            } else {
                System.setProperty("CURSOR_API_KEY", previous);
            }
            Files.deleteIfExists(script);
        }
    }
}
