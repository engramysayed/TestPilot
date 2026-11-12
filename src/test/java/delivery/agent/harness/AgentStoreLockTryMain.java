package delivery.agent.harness;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Helper JVM that attempts {@link AgentStoreLock} and writes ACQUIRED or TIMEOUT. */
public final class AgentStoreLockTryMain {
    private AgentStoreLockTryMain() {
    }

    public static void main(String[] args) throws Exception {
        Path taskDir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        long timeoutMs = Long.parseLong(args[2]);
        String result;
        try (AgentStoreLock ignored = AgentStoreLock.acquire(taskDir, Duration.ofMillis(timeoutMs))) {
            result = "ACQUIRED";
        } catch (AgentHarnessException e) {
            result = e.getMessage() != null && e.getMessage().contains("store lock timeout")
                    ? "TIMEOUT"
                    : "ERROR:" + e.getMessage();
        }
        Files.writeString(out, result, StandardCharsets.UTF_8);
    }
}
