package delivery.agent.validation.automate;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class FrameworkRepairReconcilerResilienceTest {

    @Test
    public void repeatedReconcile_isIdempotent() throws Exception {
        var layout = FrameworkRepairWorkspaceReconcilerTest.scaffoldLayoutPublic();
        Path page = layout.agentRoot().resolve("src/main/java/project/pages/Login_Page.java");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "package project.pages;\npublic class Login_Page {}\n");
        var reconciler = new FrameworkRepairWorkspaceReconciler();
        FrameworkRepairWorkspaceReconciler.Manifest first = reconciler.reconcile(
                layout.workDir(), layout.projectRoot(), layout.parentJobId(), layout.taskId(),
                layout.repairRoot(), layout.agentRoot());
        FrameworkRepairWorkspaceReconciler.Manifest second = reconciler.reconcile(
                layout.workDir(), layout.projectRoot(), layout.parentJobId(), layout.taskId(),
                layout.repairRoot(), layout.agentRoot());
        Assert.assertFalse(first.copied().isEmpty());
        Assert.assertTrue(second.copied().isEmpty(), "second pass should not recopy unchanged files");
        Assert.assertEquals(
                Files.readString(layout.repairRoot().resolve("src/main/java/project/pages/Login_Page.java")),
                Files.readString(page));
    }

    @Test
    public void partialCopyFailure_canLeaveCandidateModified() throws Exception {
        var layout = FrameworkRepairWorkspaceReconcilerTest.scaffoldLayoutPublic();
        String rel = "src/main/java/project/pages/Login_Page.java";
        Files.createDirectories(layout.agentRoot().resolve("src/main/java/project/pages"));
        Files.writeString(layout.agentRoot().resolve(rel), "new-content");
        Path dest = layout.repairRoot().resolve(rel);
        Files.createDirectories(dest.getParent());
        Files.writeString(dest, "old-content");
        boolean writableBefore = dest.toFile().canWrite();
        Assert.assertTrue(writableBefore);
        dest.toFile().setWritable(false);
        try {
            try {
                new FrameworkRepairWorkspaceReconciler().reconcile(
                        layout.workDir(), layout.projectRoot(), layout.parentJobId(), layout.taskId(),
                        layout.repairRoot(), layout.agentRoot());
                Assert.fail("expected IOException when destination is not writable");
            } catch (IOException | FrameworkRepairReconciliationException expected) {
                Assert.assertEquals(Files.readString(dest), "old-content",
                        "failed copy should not replace read-only destination; no rollback stage exists");
            }
        } finally {
            dest.toFile().setWritable(true);
        }
    }

    @Test
    public void staleWorkspace_wrongExpectedPath_failClosed() throws Exception {
        var layout = FrameworkRepairWorkspaceReconcilerTest.scaffoldLayoutPublic();
        Path stale = Files.createTempDirectory("stale-ws");
        try {
            Assert.expectThrows(
                    FrameworkRepairReconciliationException.class,
                    () -> new FrameworkRepairWorkspaceReconciler().reconcile(
                            layout.workDir(), layout.projectRoot(), layout.parentJobId(), layout.taskId(),
                            layout.repairRoot(), stale));
        } finally {
            deleteRec(stale);
        }
    }

    @Test
    public void stagingFailure_restoresRepairCandidate() throws Exception {
        var layout = FrameworkRepairWorkspaceReconcilerTest.scaffoldLayoutPublic();
        String rel = "src/main/java/project/pages/Login_Page.java";
        Path agentPage = layout.agentRoot().resolve(rel);
        Files.createDirectories(agentPage.getParent());
        Files.writeString(agentPage, "new");
        Path repairPage = layout.repairRoot().resolve(rel);
        Files.createDirectories(repairPage.getParent());
        Files.writeString(repairPage, "old-content");
        String before = Files.readString(repairPage);
        repairPage.toFile().setReadOnly();
        try {
            try {
                new FrameworkRepairWorkspaceReconciler().reconcile(
                        layout.workDir(), layout.projectRoot(), layout.parentJobId(), layout.taskId(),
                        layout.repairRoot(), layout.agentRoot());
            } catch (Exception expected) {
                Assert.assertEquals(Files.readString(repairPage), before, "apply failure must restore backup");
            }
        } finally {
            repairPage.toFile().setWritable(true);
        }
    }

    @Test
    public void exclusionManifest_listsUnexpectedAgentFiles() throws Exception {
        var layout = FrameworkRepairWorkspaceReconcilerTest.scaffoldLayoutPublic();
        Path evil = layout.agentRoot().resolve("README-Agent.txt");
        Files.writeString(evil, "notes");
        var manifest = new FrameworkRepairWorkspaceReconciler().reconcile(
                layout.workDir(), layout.projectRoot(), layout.parentJobId(), layout.taskId(),
                layout.repairRoot(), layout.agentRoot());
        Assert.assertTrue(manifest.excluded().contains("README-Agent.txt"));
        Assert.assertFalse(Files.exists(layout.repairRoot().resolve("README-Agent.txt")));
    }

    private static void deleteRec(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }
}