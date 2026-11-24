package delivery.mobile.run;

import delivery.portal.service.PortalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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
    private final ObjectProvider<PortalStore> portal;
    private final boolean autostart;
    private final int port;
    private final Path tokenFile;
    private volatile Process process;
    private volatile long lastAttemptMs;
    private volatile long lastEnrollMs;
    private volatile String lastError = "";

    public MobileRunnerSupervisor(MobileRunOrchestrator runs,
                                  ObjectProvider<PortalStore> portal,
                                  @Value("${delivery.mobile.runner.autostart:false}") boolean autostart,
                                  @Value("${server.port:8080}") int port,
                                  @Value("${delivery.store-root:./delivery-store}") String storeRoot) {
        this.runs = runs;
        this.portal = portal;
        this.autostart = autostart;
        this.port = port;
        this.tokenFile = Path.of(storeRoot).resolve("mobile-runner.token");
    }

    /**
     * When no environment token is set, enroll a runner for this project and save the token
     * under the store root. One saved token is one tenant. A different tenant replaces it.
     */
    public synchronized void ensureForProject(String projectId, long actorUserId, String tenantId) {
        String env = envToken();
        if (env.isBlank() && autostart && projectId != null && !projectId.isBlank() && actorUserId > 0) {
            Saved saved = readSaved(tokenFile);
            if (needsEnrollment(saved.token(), saved.tenant(), tenantId)) {
                enroll(projectId, actorUserId, tenantId == null ? "" : tenantId);
            }
        }
        ensureStarted();
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
            return "Mobile runner is not running. Open this project again so Keel can enroll one.";
        }
        return "Mobile runner is not running. Press Start and Keel will start it.";
    }

    /** Start the runner if it is down, then wait until it polls. Used when the user presses Start. */
    public void startForRun() {
        if (runs.runnerOnline()) {
            return;
        }
        synchronized (this) {
            if (runs.runnerOnline()) {
                return;
            }
            if (process == null || !process.isAlive()) {
                lastAttemptMs = 0;
                spawn();
            }
        }
        if (token().isBlank()) {
            throw new IllegalStateException(line());
        }
        if (!lastError.isBlank() && (process == null || !process.isAlive())) {
            throw new IllegalStateException(line());
        }
        long deadline = System.currentTimeMillis() + 180_000L;
        while (System.currentTimeMillis() < deadline) {
            if (runs.runnerOnline()) {
                return;
            }
            if (process != null && !process.isAlive()) {
                throw new IllegalStateException(line() + " See delivery-store/mobile-runner.log.");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while the mobile runner was starting.");
            }
        }
        throw new IllegalStateException("Mobile runner is still starting. See delivery-store/mobile-runner.log.");
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
        spawn();
    }

    private void spawn() {
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
            boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            String maven = win ? "mvn.cmd" : "mvn";
            String sep = win ? ";" : ":";
            String command = maven + " -pl mobile-runner -am -DskipTests -q test-compile"
                    + " dependency:build-classpath -DincludeScope=runtime"
                    + " -Dmdep.outputFile=mobile-runner/target/runner.classpath"
                    + " && java -cp \"mobile-runner/target/classes" + sep
                    + "mobile-runner/target/runner.classpath\""
                    + " delivery.mobile.runner.agent.MobileRunnerAgent";
            ProcessBuilder pb = win
                    ? new ProcessBuilder("cmd.exe", "/c", command)
                    : new ProcessBuilder("sh", "-c", command);
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

    private void enroll(String projectId, long actorUserId, String tenantId) {
        if (System.currentTimeMillis() - lastEnrollMs < 30_000) {
            return;
        }
        lastEnrollMs = System.currentTimeMillis();
        PortalStore store = portal.getIfAvailable();
        if (store == null) {
            lastError = "The portal store is not available to enroll a runner.";
            return;
        }
        try {
            var created = store.enrollWorkspaceRunner(projectId, actorUserId, "Keel mobile runner");
            Path parent = tokenFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String body = created.token().trim() + "\n" + tenantId + "\n";
            Files.writeString(tokenFile, body, StandardCharsets.UTF_8);
            if (process != null && process.isAlive()) {
                process.destroy();
                process = null;
            }
            lastAttemptMs = 0;
            lastError = "";
        } catch (Exception e) {
            lastError = e.getMessage() == null ? "could not enroll a runner" : e.getMessage();
            log.warn("Could not enroll a mobile runner: {}", lastError);
        }
    }

    private String token() {
        String env = envToken();
        if (!env.isBlank()) {
            return env;
        }
        return readSaved(tokenFile).token();
    }

    private static String envToken() {
        String token = System.getenv("KEEL_RUNNER_TOKEN");
        return token == null ? "" : token.trim();
    }

    static Saved readSaved(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) {
                return new Saved("", "");
            }
            return parseSaved(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new Saved("", "");
        }
    }

    static Saved parseSaved(String text) {
        if (text == null || text.isBlank()) {
            return new Saved("", "");
        }
        String[] lines = text.split("\\R", 3);
        String token = lines[0].trim();
        String tenant = lines.length > 1 ? lines[1].trim() : "";
        if (!token.startsWith("tp_run_")) {
            return new Saved("", "");
        }
        return new Saved(token, tenant);
    }

    static boolean needsEnrollment(String savedToken, String savedTenant, String wantedTenant) {
        if (savedToken == null || savedToken.isBlank()) {
            return true;
        }
        if (wantedTenant == null || wantedTenant.isBlank()) {
            return false;
        }
        return savedTenant == null || !wantedTenant.equals(savedTenant);
    }

    record Saved(String token, String tenant) {
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
