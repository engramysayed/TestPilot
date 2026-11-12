package delivery.store;

import delivery.identity.PublicationLock;
import delivery.job.ArtifactIntegrityException;
import delivery.job.PublicationGuard;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjectStorePublicationGuardTest {

    @Test
    public void defaultGuard_beforeLockedPublishNoOp() throws Exception {
        Path root = Files.createTempDirectory("pub-guard");
        ProjectStore store = new ProjectStore(root);
        Path framework = Files.createTempDirectory("fw");
        Files.writeString(framework.resolve("README.md"), "x");
        Path zip = Files.createTempFile("z", ".zip");
        Files.writeString(zip, "bytes");
        var saved = store.saveVersion("p1", framework, zip, null, PublicationGuard.cancellable(() -> false));
        Assert.assertEquals(saved.version(), 1);
    }

    @Test(expectedExceptions = ArtifactIntegrityException.class)
    public void integrityMismatch_abortsPublish() throws Exception {
        Path root = Files.createTempDirectory("pub-int");
        ProjectStore store = new ProjectStore(root);
        Path framework = Files.createTempDirectory("fw2");
        Path zip = Files.createTempFile("z2", ".zip");
        Files.writeString(zip, "v1");
        String fp = delivery.agent.harness.AgentArtifactFingerprints.sha256Hex(zip);
        Files.writeString(zip, "v2 mutated");
        PublicationGuard guard = new PublicationGuard() {
            public void beforeLockedPublish(Path frameworkDir, Path zipFile) {
            }

            public Optional<String> expectedZipFingerprint() {
                return Optional.of(fp);
            }

            public <T> T publish(Callable<T> action) throws Exception {
                return action.call();
            }
        };
        store.saveVersion("p2", framework, zip, null, guard);
    }

    @Test
    public void slowBeforeLockedPublish_runsOutsideProjectPublishLock() throws Exception {
        Path root = Files.createTempDirectory("pub-slow");
        Path lock = root.resolve("slow/.publish.lock");
        Files.createDirectories(lock.getParent());
        AtomicBoolean hookRan = new AtomicBoolean();
        Thread holder = new Thread(() -> {
            try {
                PublicationLock.call(lock, () -> {
                    Thread.sleep(4000);
                    return null;
                });
            } catch (Exception ignored) {
            }
        });
        holder.start();
        Thread.sleep(200);
        ProjectStore store = new ProjectStore(root);
        Path framework = Files.createTempDirectory("fw-slow");
        Path zip = Files.createTempFile("zs", ".zip");
        Files.writeString(zip, "z");
        PublicationGuard guard = new PublicationGuard() {
            public void beforeLockedPublish(Path frameworkDir, Path zipFile) throws Exception {
                Thread.sleep(500);
                hookRan.set(true);
            }

            public <T> T publish(Callable<T> action) throws Exception {
                return action.call();
            }
        };
        long start = System.currentTimeMillis();
        store.saveVersion("slow", framework, zip, null, guard);
        long elapsed = System.currentTimeMillis() - start;
        Assert.assertTrue(hookRan.get());
        Assert.assertTrue(elapsed >= 500, "hook should have run before acquiring lock");
        holder.join(8000);
    }
}
