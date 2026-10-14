package delivery.portal.security;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/** B06: plain-HTTP runner transport (mtls.required=false) is refused for production and for topology=split. */
public class ProductionSafetyGuardRunnerTransportTest {
    @Test
    public void productionWithoutMtlsRefused() {
        var t = new ProductionSafetyGuard.RunnerTransport(true, "single", false);
        assertEquals(ProductionSafetyGuard.runnerTransportViolations(t).size(), 1);
        var e = expectThrows(IllegalStateException.class, () -> ProductionSafetyGuard.requireSafeRunnerTransport(t));
        assertTrue(e.getMessage().startsWith("INSECURE_RUNNER_TRANSPORT"));
    }

    @Test
    public void splitTopologyWithoutMtlsRefusedEvenInDevelopment() {
        var t = new ProductionSafetyGuard.RunnerTransport(false, " Split ", false);
        assertTrue(ProductionSafetyGuard.runnerTransportViolations(t).get(0).contains("keel.topology=split"));
        assertEquals(ProductionSafetyGuard.runnerTransportViolations(
                new ProductionSafetyGuard.RunnerTransport(true, "split", false)).size(), 2);
    }

    @Test
    public void phase0aDefaultsAndMtlsAreAccepted() {
        ProductionSafetyGuard.requireSafeRunnerTransport(new ProductionSafetyGuard.RunnerTransport(false, "single", false));
        ProductionSafetyGuard.requireSafeRunnerTransport(new ProductionSafetyGuard.RunnerTransport(true, "split", true));
        ProductionSafetyGuard.requireSafeRunnerTransport(null);
    }
}
