package delivery.agent.validation.automate;

import delivery.agent.harness.AgentPolicy;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.harness.AgentWorkspacePaths;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class FrameworkRepairWorkspaceReconcilerTest {

    @Test
    public void createsMissingPage_inRepairCandidate() throws Exception {
        var layout = scaffoldLayout();
        Path page = layout.agentRoot().resolve("src/main/java/project/pages/Login_Page.java");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "package project.pages;\npublic class Login_Page {}\n");
        assert !Files.exists(layout.repairRoot().resolve("src/main/java/project/pages/Login_Page.java"));

        var manifest = reconciler().reconcile(
                layout.workDir(),
                layout.projectRoot(),
                layout.parentJobId(),
                layout.taskId(),
                layout.repairRoot(),
                layout.agentRoot());

        Assert.assertTrue(manifest.copied().stream().anyMatch(p -> p.endsWith("Login_Page.java")));
        Assert.assertTrue(Files.exists(layout.repairRoot().resolve("src/main/java/project/pages/Login_Page.java")));
    }

    @Test
    public void modifiesExistingPage() throws Exception {
        var layout = scaffoldLayout();
        Path repairPage = layout.repairRoot().resolve("src/main/java/project/pages/Login_Page.java");
        Files.createDirectories(repairPage.getParent());
        Files.writeString(repairPage, "old");
        Path agentPage = layout.agentRoot().resolve("src/main/java/project/pages/Login_Page.java");
        Files.createDirectories(agentPage.getParent());
        Files.writeString(agentPage, "new");

        reconciler().reconcile(
                layout.workDir(),
                layout.projectRoot(),
                layout.parentJobId(),
                layout.taskId(),
                layout.repairRoot(),
                layout.agentRoot());

        Assert.assertEquals(Files.readString(repairPage), "new");
    }

    @Test
    public void deletesPermittedFile_whenRemovedFromAgentWorkspace() throws Exception {
        var layout = scaffoldLayout();
        String rel = "src/main/java/project/pages/Stale_Page.java";
        Path repairFile = layout.repairRoot().resolve(rel);
        Files.createDirectories(repairFile.getParent());
        Files.writeString(repairFile, "stale");

        var manifest = reconciler().reconcile(
                layout.workDir(),
                layout.projectRoot(),
                layout.parentJobId(),
                layout.taskId(),
                layout.repairRoot(),
                layout.agentRoot());

        Assert.assertFalse(Files.exists(repairFile));
        Assert.assertTrue(manifest.deleted().contains(rel));
    }

    @Test
    public void unexpectedFiles_excludedFromRepairCandidate() throws Exception {
        var layout = scaffoldLayout();
        Path evil = layout.agentRoot().resolve("src/main/java/project/evil/Evil.java");
        Files.createDirectories(evil.getParent());
        Files.writeString(evil, "evil");

        var manifest = reconciler().reconcile(
                layout.workDir(),
                layout.projectRoot(),
                layout.parentJobId(),
                layout.taskId(),
                layout.repairRoot(),
                layout.agentRoot());

        Assert.assertTrue(manifest.excluded().stream().anyMatch(p -> p.contains("evil")));
        Assert.assertFalse(Files.exists(layout.repairRoot().resolve("src/main/java/project/evil/Evil.java")));
    }

    @Test
    public void workspaceIdentityMismatch_failClosed() throws Exception {
        var layout = scaffoldLayout();
        Path wrong = Files.createTempDirectory("wrong-ws");
        try {
            Assert.expectThrows(
                    FrameworkRepairReconciliationException.class,
                    () -> reconciler().reconcile(
                            layout.workDir(),
                            layout.projectRoot(),
                            layout.parentJobId(),
                            layout.taskId(),
                            layout.repairRoot(),
                            wrong));
        } finally {
            deleteRecursively(wrong);
        }
    }

    @Test
    public void nonCompletedTask_rejected() throws Exception {
        var layout = scaffoldLayout();
        AgentRunStore store = new AgentRunStore();
        saveTask(store, layout, AgentTaskStatus.FAILED);
        try {
            reconciler(store).reconcile(
                    layout.workDir(),
                    layout.projectRoot(),
                    layout.parentJobId(),
                    layout.taskId(),
                    layout.repairRoot(),
                    layout.agentRoot());
            Assert.fail("expected reconciliation failure");
        } catch (FrameworkRepairReconciliationException e) {
            Assert.assertTrue(e.getMessage().contains("COMPLETED"));
        }
    }

    @Test
    public void fakeProviderTask_rejected() throws Exception {
        var layout = scaffoldLayout();
        AgentRunStore store = new AgentRunStore();
        saveTask(store, layout, AgentTaskStatus.COMPLETED, AgentProviderId.FAKE);
        Assert.expectThrows(
                FrameworkRepairReconciliationException.class,
                () -> reconciler(store).reconcile(
                        layout.workDir(),
                        layout.projectRoot(),
                        layout.parentJobId(),
                        layout.taskId(),
                        layout.repairRoot(),
                        layout.agentRoot()));
    }

    @Test
    public void repairCandidatePathMismatch_failClosed() throws Exception {
        var layout = scaffoldLayout();
        Path foreign = layout.workDir().resolve("not-repair-candidate");
        Files.createDirectories(foreign);
        Assert.expectThrows(
                FrameworkRepairReconciliationException.class,
                () -> reconciler().reconcile(
                        layout.workDir(),
                        layout.projectRoot(),
                        layout.parentJobId(),
                        layout.taskId(),
                        foreign,
                        layout.agentRoot()));
    }

    @Test
    public void symlinkInAgentWorkspace_rejected() throws Exception {
        var layout = scaffoldLayout();
        Path link = layout.agentRoot().resolve("src/main/java/project/pages/Link_Page.java");
        Path target = layout.agentRoot().resolve("target-secret.txt");
        Files.writeString(target, "secret");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            throw new org.testng.SkipException("symlink not supported: " + e.getMessage());
        }
        Assert.expectThrows(
                Exception.class,
                () -> reconciler().reconcile(
                        layout.workDir(),
                        layout.projectRoot(),
                        layout.parentJobId(),
                        layout.taskId(),
                        layout.repairRoot(),
                        layout.agentRoot()));
    }

    @Test
    public void permittedPrefix_helper() {
        Assert.assertTrue(FrameworkRepairWorkspaceReconciler.isPermittedRelative(
                "src/main/java/project/pages/Foo.java"));
        Assert.assertFalse(FrameworkRepairWorkspaceReconciler.isPermittedRelative("../pages/x.java"));
        Assert.assertFalse(FrameworkRepairWorkspaceReconciler.isPermittedRelative("pom.xml"));
    }

    private static FrameworkRepairWorkspaceReconciler reconciler() {
        return new FrameworkRepairWorkspaceReconciler();
    }

    private static FrameworkRepairWorkspaceReconciler reconciler(AgentRunStore store) {
        return new FrameworkRepairWorkspaceReconciler(store);
    }

    static Layout scaffoldLayoutPublic() throws Exception {
        return scaffoldLayout();
    }

    private static Layout scaffoldLayout() throws Exception {
        Path workDir = Files.createTempDirectory("reconcile-");
        Path project = workDir.resolve("project");
        Path pages = project.resolve("src/main/java/project/pages");
        Files.createDirectories(pages);
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        FrameworkAcceptanceWorkspace.snapshotOriginal(workDir, project);
        Path repairRoot = FrameworkAcceptanceWorkspace.createRepairCandidate(workDir, project);
        String parentJobId = "job-reconcile";
        String taskId = "task-1";
        Path projectRoot = workDir;
        Path taskDir = projectRoot.resolve("agent-runs").resolve(parentJobId).resolve(taskId);
        Path agentRoot = AgentWorkspacePaths.isolatedWorkspace(taskDir);
        Files.createDirectories(agentRoot);
        Files.writeString(agentRoot.resolve("pom.xml"), "<project/>");
        AgentRunStore store = new AgentRunStore();
        saveTask(store, new Layout(workDir, projectRoot, parentJobId, taskId, repairRoot, agentRoot),
                AgentTaskStatus.COMPLETED, AgentProviderId.CURSOR);
        return new Layout(workDir, projectRoot, parentJobId, taskId, repairRoot, agentRoot);
    }

    private static void saveTask(
            AgentRunStore store, Layout layout, AgentTaskStatus status) throws Exception {
        saveTask(store, layout, status, AgentProviderId.CURSOR);
    }

    private static void saveTask(
            AgentRunStore store, Layout layout, AgentTaskStatus status, AgentProviderId provider)
            throws Exception {
        AgentTask task = new AgentTask(
                layout.taskId(),
                layout.parentJobId(),
                "proj",
                AgentTaskType.FRAMEWORK_ADAPTATION_V1,
                status,
                "repair",
                List.of(),
                layout.agentRoot().toString(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                "agent",
                "run",
                1,
                AgentPolicy.disabled(),
                "result.json",
                provider,
                "",
                "",
                3);
        store.saveTask(layout.projectRoot(), task);
    }

    private static void deleteRecursively(Path root) throws Exception {
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

    record Layout(
            Path workDir, Path projectRoot, String parentJobId, String taskId, Path repairRoot, Path agentRoot) {}
}