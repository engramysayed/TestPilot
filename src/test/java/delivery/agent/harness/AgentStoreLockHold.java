package delivery.agent.harness;

import java.nio.file.Files;
import java.nio.file.Path;

/** Helper JVM that holds {@link AgentStoreLock} until interrupted or timeout. */
public final class AgentStoreLockHold {
    private AgentStoreLockHold() {
    }

    public static void main(String[] args) throws Exception {
        Path taskDir = Path.of(args[0]);
        Path flag = Path.of(args[1]);
        long holdMs = Long.parseLong(args[2]);
        try (AgentStoreLock lock = AgentStoreLock.acquire(taskDir, java.time.Duration.ofSeconds(5))) {
            Files.writeString(flag, "held");
            Thread.sleep(holdMs);
        }
    }
}
