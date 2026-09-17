package delivery.portal.ci;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ReleaseCiContractTest {

    @Test
    public void pomForksSharedAndDedicatedInstallDrills() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        Assert.assertTrue(pom.contains("<id>shared-install-drill</id>"),
                "pom must declare a shared-install-drill Surefire execution");
        Assert.assertTrue(pom.contains("<id>dedicated-install-drill</id>"),
                "pom must declare a dedicated-install-drill Surefire execution");
        Assert.assertTrue(pom.contains("delivery.install.drill"),
                "pom must pass delivery.install.drill into each drill JVM");
        Assert.assertTrue(pom.contains("SharedInstallDrillTest"),
                "pom must name SharedInstallDrillTest");
        Assert.assertTrue(pom.contains("DedicatedInstallDrillTest"),
                "pom must name DedicatedInstallDrillTest");
    }

    @Test
    public void rootWorkflowRunsDeterministicTestsWithoutLiveProviders() throws Exception {
        Path workflow = Path.of(".github/workflows/release.yml");
        Assert.assertTrue(Files.isRegularFile(workflow), "root release CI workflow is required");
        String text = Files.readString(workflow);
        Assert.assertTrue(text.contains("-Pdeterministic") || text.contains("id: deterministic"),
                "CI must use the deterministic Maven profile");
        Assert.assertTrue(text.contains("LiveSmoke"), "CI must exclude live smoke tests by name");
        Assert.assertTrue(text.toLowerCase().contains("sha256"), "CI must record artifact checksums");
        Assert.assertTrue(text.contains("customer-framework-template"),
                "CI must compile the customer framework template");
        Assert.assertTrue(text.contains("delivery.ops.SharedInstallDrillTest"),
                "CI must run the shared install drill in its own Maven JVM");
        Assert.assertTrue(text.contains("delivery.ops.DedicatedInstallDrillTest"),
                "CI must run the dedicated install drill in its own Maven JVM");
        Assert.assertFalse(text.contains("CURSOR_API_KEY:"),
                "deterministic CI must not inject a real provider key");
    }
}
