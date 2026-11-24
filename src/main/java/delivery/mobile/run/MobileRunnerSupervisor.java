package delivery.mobile.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Starts the mobile runner process when a token is configured, and says whether it is up.
 * The runner stays a separate process. The portal does not put Appium on its own classpath.
 */
@Service
public class MobileRunnerSupervisor {

    private static final Logger log = LoggerFactory.getLogger(MobileRunnerSupervisor.class);

    private final MobileRunOrchestrator runs;
    private final boolean autostart;
    private final int port;
    private volatile Process process;
    private volatile long lastAttemptMs;
    private volatile String lastError = "";

    public MobileRunnerSupervisor(MobileRunOrchestrator runs,
                                  @Value("${delivery.mobile.runner.autostart:false}") boolean autostart,
                                  @Value("${server.port:8080}") int port) {
        this.runs = runs;
        this.autostart = autostart;
        this.port = port;
    }

    public String line() {
        if (runs.runnerOnline()) {
            return "Mobile runner is running.";
        }
        if (process != null && process.isAlive()) {
            return "Mobile runner is starting.";
        }
        if (!lastError.isBlank()) {
            return "Mobile runner did not start. " + lastError;
        }
        if (token().isBlank()) {
            return "Mobile runner is not running. Enroll a runner and set KEEL_RUNNER_TOKEN, then reload.";
        }
        return "Mobile runner is not running.";
    }

    public synchronized void ensureStarted() {
        if (!autostart || runs.runnerOnline()) {
            return;
        }
        if (process != null && process.isAlive()) {
            return;
        }
        if (System.currentTimeMillis() - lastAttemptMs < 30_000) {
            return;
        }
        String token = token();
        if (token.isBlank()) {
            return;
        }
        Path root = repoRoot();
        if (root == null) {
            lastError = "mobile-runner was not found next to the portal.";
            return;
        }
        lastAttemptMs = System.currentTimeMillis();
        lastError = "";
        try {
            Path logFile = Path.of("delivery-store", "mobile-runner.log");
            Files.createDirectories(logFile.getParent());
            String maven = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    ? "mvn.cmd" : "mvn";
            ProcessBuilder pb = new ProcessBuilder(
                    maven, "-q", "-pl", "mobile-runner", "-am", "exec:java",
                    "-Dexec.skip=false",
                    "-Dexec.mainClass=delivery.mobile.runner.agent.MobileRunnerAgent");
            pb.directory(root.toFile());
            pb.environment().put("KEEL_RUNNER_TOKEN", token);
            String portal = System.getenv("KEEL_PORTAL_URL");
            pb.environment().put("KEEL_PORTAL_URL",
                    portal == null || portal.isBlank() ? "http://127.0.0.1:" + port : portal);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            process = pb.start();
        } catch (Exception e) {
            lastError = e.getMessage() == null ? "could not start" : e.getMessage();
            log.warn("Could not start the mobile runner: {}", lastError);
        }
    }

    private static String token() {
        String token = System.getenv("KEEL_RUNNER_TOKEN");
        return token == null ? "" : token.trim();
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("mobile-runner").resolve("pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
