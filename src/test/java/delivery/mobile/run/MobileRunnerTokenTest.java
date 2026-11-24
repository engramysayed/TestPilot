package delivery.mobile.run;

import org.testng.annotations.Test;

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
    public void junkFileDoesNotCountAsAToken() {
        MobileRunnerSupervisor.Saved saved = MobileRunnerSupervisor.parseSaved("not-a-token\n");
        assertEquals(saved.token(), "");
        assertTrue(MobileRunnerSupervisor.needsEnrollment(saved.token(), saved.tenant(), "tenant-a"));
    }
}
