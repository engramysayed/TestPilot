package delivery.mobile.run;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MobileRunnerTokenTest {

    @Test
    public void savedTokenKeepsItsTenant() {
        MobileRunnerSupervisor.Saved saved = MobileRunnerSupervisor.parseSaved("tp_run_abc\ntenant-a\n");
        assertEquals(saved.token(), "tp_run_abc");
        assertEquals(saved.tenant(), "tenant-a");
        assertFalse(MobileRunnerSupervisor.needsEnrollment(saved.token(), saved.tenant(), "tenant-a"));
        assertTrue(MobileRunnerSupervisor.needsEnrollment(saved.token(), saved.tenant(), "tenant-b"));
        assertTrue(MobileRunnerSupervisor.needsEnrollment("", "tenant-a", "tenant-a"));
    }

    @Test
    public void builtRunnerStartsWithoutMaven() throws Exception {
        Path root = Files.createTempDirectory("runner-cmd");
        Path classes = root.resolve("mobile-runner/target/classes/delivery/mobile/runner/agent");
        Files.createDirectories(classes);
        Files.writeString(classes.resolve("MobileRunnerAgent.class"), "x");
        Files.writeString(root.resolve("mobile-runner/target/runner.classpath"), "lib.jar");
        String command = MobileRunnerSupervisor.launchCommand(root, true, ";");
        assertTrue(command.startsWith("java -cp \""), command);
        assertTrue(command.contains("MobileRunnerAgent"), command);
        assertFalse(command.contains("test-compile"), command);
    }

    @Test
    public void junkFileDoesNotCountAsAToken() {
        MobileRunnerSupervisor.Saved saved = MobileRunnerSupervisor.parseSaved("not-a-token\n");
        assertEquals(saved.token(), "");
        assertTrue(MobileRunnerSupervisor.needsEnrollment(saved.token(), saved.tenant(), "tenant-a"));
    }
}
