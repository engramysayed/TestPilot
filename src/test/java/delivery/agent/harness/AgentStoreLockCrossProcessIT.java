package delivery.agent.harness;

import delivery.agent.harness.cursor.CursorSidecarClient;
import delivery.agent.harness.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OS-level {@link AgentStoreLock} across JVM processes (not in-process threads only).
 */
public class AgentStoreLockCrossProcessIT {

    @Test
    public void secondProcessTimesOutThenAcquiresAfterKill() throws Exception {
        Path dir = Files.createTempDirectory("agent-store-lock-xproc-");
        Path taskDir = dir.resolve("task");
        Files.createDirectories(taskDir);
        Path flag = dir.resolve("held.flag");
        Path try1 = dir.resolve("try1.out");
        Path try2 = dir.resolve("try2.out");
        Path childLog = dir.resolve("child.log");
        String cp = testClasspath();

        Process child = new ProcessBuilder(
                javaBin(),
                "-cp", cp,
                AgentStoreLockHold.class.getName(),
                taskDir.toAbsolutePath().toString(),
                flag.toAbsolutePath().toString(),
                "60000")
                .redirectErrorStream(true)
                .redirectOutput(childLog.toFile())
                .start();

        long waitFlag = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!Files.isRegularFile(flag) && child.isAlive() && System.nanoTime() < waitFlag) {
            Thread.sleep(25);
        }
        Assert.assertTrue(Files.isRegularFile(flag), childLogMessage(childLog));

        int t1 = runTryMain(cp, taskDir, try1, 400);
        Assert.assertEquals(t1, 0);
        Assert.assertEquals(Files.readString(try1, StandardCharsets.UTF_8).trim(), "TIMEOUT");

        child.destroyForcibly();
        Assert.assertTrue(child.waitFor(10, TimeUnit.SECONDS));

        int t2 = runTryMain(cp, taskDir, try2, 5000);
        Assert.assertEquals(t2, 0);
        Assert.assertEquals(Files.readString(try2, StandardCharsets.UTF_8).trim(), "ACQUIRED");
    }

    @Test
    public void sidecarOpsBlockWhilePeerProcessHoldsStoreLock() throws Exception {
        Path fakeScript = Path.of("src/test/resources/cursor-agent/fake-sidecar.mjs").toAbsolutePath();
        if (!Files.isRegularFile(fakeScript)) {
            throw new org.testng.SkipException("fake-sidecar missing");
        }
        Path dir = Files.createTempDirectory("agent-store-sidecar-lock-");
        Path taskDir = dir.resolve("task");
        Files.createDirectories(taskDir.resolve("workspace"));
        Files.writeString(taskDir.resolve("workspace/ws.txt"), "x");
        Path flag = dir.resolve("held.flag");

        String prevKey = System.getProperty("CURSOR_API_KEY");
        String prevAllow = System.getProperty("delivery.provider.allowlist");
        System.setProperty("CURSOR_API_KEY", "test-key-not-for-api");
        System.setProperty("delivery.provider.allowlist", "cursor");
        String prevLockMs = System.getProperty("delivery.agent-harness.store-lock-ms");
        System.setProperty("delivery.agent-harness.store-lock-ms", "800");
        try {
            String cp = testClasspath();
            Process child = new ProcessBuilder(
                    javaBin(),
                    "-cp", cp,
                    AgentStoreLockHold.class.getName(),
                    taskDir.toAbsolutePath().toString(),
                    flag.toAbsolutePath().toString(),
                    "60000")
                    .redirectErrorStream(true)
                    .start();

            long waitFlag = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!Files.isRegularFile(flag) && child.isAlive() && System.nanoTime() < waitFlag) {
                Thread.sleep(25);
            }
            Assert.assertTrue(Files.isRegularFile(flag));

            AgentHarnessProperties props = new AgentHarnessProperties();
            props.setCursorCommand("node " + fakeScript);

            AgentTask cancelTask = new AgentTask(
                    "t1", "job1", "p1", AgentTaskType.BUG_INVESTIGATION_V1,
                    AgentTaskStatus.RUNNING, "", List.of(),
                    taskDir.resolve("workspace").toAbsolutePath().toString(),
                    Instant.now(), null, null, "agent-x", "run-y", 1,
                    AgentPolicy.disabled(), "", AgentProviderId.CURSOR, "", "", 3);

            CursorSidecarClient client = new CursorSidecarClient(props);
            AgentHarnessException blocked = null;
            try {
                client.execute(cancelTask, taskDir, fakeScript.getParent().getParent().getParent(), "cancel", () -> false);
            } catch (AgentHarnessException e) {
                blocked = e;
            } catch (Exception e) {
                blocked = new AgentHarnessException(e.getMessage(), e);
            }
            Assert.assertNotNull(blocked);
            Assert.assertTrue(
                    blocked.getMessage() != null && blocked.getMessage().contains("store lock timeout"),
                    "expected store lock timeout, got: " + blocked.getMessage());

            Path repoRoot = CursorSidecarClient.resolveRepoRoot("node " + fakeScript);
            AgentTask reconcileTask = new AgentTask(
                    "t2", "job1", "p1", AgentTaskType.BUG_INVESTIGATION_V1,
                    AgentTaskStatus.RUNNING, "", List.of(),
                    taskDir.resolve("workspace").toAbsolutePath().toString(),
                    Instant.now(), null, null, "agent-x", "run-y", 1,
                    AgentPolicy.disabled(), "", AgentProviderId.CURSOR, "", "", 3);
            AgentHarnessException reconcileBlocked = null;
            try {
                client.execute(reconcileTask, taskDir, repoRoot, "reconcile", () -> false);
            } catch (AgentHarnessException e) {
                reconcileBlocked = e;
            } catch (Exception e) {
                reconcileBlocked = new AgentHarnessException(e.getMessage(), e);
            }
            Assert.assertNotNull(reconcileBlocked);
            Assert.assertTrue(reconcileBlocked.getMessage().contains("store lock timeout"));

            child.destroyForcibly();
            Assert.assertTrue(child.waitFor(10, TimeUnit.SECONDS));

            try (var session = client.execute(
                    cancelTask, taskDir, repoRoot, "cancel", () -> false)) {
                session.resultOrThrow();
            }
        } finally {
            restoreProperty("delivery.agent-harness.store-lock-ms", prevLockMs);
            restoreProperty("CURSOR_API_KEY", prevKey);
            restoreProperty("delivery.provider.allowlist", prevAllow);
        }
    }

    private static int runTryMain(String cp, Path taskDir, Path out, long timeoutMs) throws Exception {
        Process p = new ProcessBuilder(
                javaBin(),
                "-cp", cp,
                AgentStoreLockTryMain.class.getName(),
                taskDir.toAbsolutePath().toString(),
                out.toAbsolutePath().toString(),
                Long.toString(timeoutMs))
                .redirectErrorStream(true)
                .start();
        Assert.assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        return p.exitValue();
    }

    private static String childLogMessage(Path childLog) throws Exception {
        return Files.isRegularFile(childLog) ? Files.readString(childLog) : "no child log";
    }

    private static String testClasspath() {
        String cp = System.getProperty("surefire.test.class.path");
        if (cp == null || cp.isBlank()) {
            cp = System.getProperty("java.class.path");
        }
        return cp;
    }

    private static String javaBin() {
        String home = System.getProperty("java.home");
        boolean win = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        return Path.of(home, "bin", win ? "java.exe" : "java").toString();
    }

    private static void restoreProperty(String key, String prev) {
        if (prev == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prev);
        }
    }
}
