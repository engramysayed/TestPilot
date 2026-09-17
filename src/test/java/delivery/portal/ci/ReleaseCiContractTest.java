package delivery.portal.ci;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ReleaseCiContractTest {

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
        Assert.assertFalse(text.contains("CURSOR_API_KEY:"),
                "deterministic CI must not inject a real provider key");
    }
}
