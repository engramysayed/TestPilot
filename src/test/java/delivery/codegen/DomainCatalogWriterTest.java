package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DomainCatalogWriterTest {
    @Test
    public void writesCatalogWithPageAndTestNames() throws Exception {
        Path dir = Files.createTempDirectory("domain-catalog");
        ProvenStep click = new ProvenStep("TC_1", "Inventory", "elementAction", "click",
                "id", "add-to-cart-sauce-labs-backpack", "", "", "", true, "");
        TcOutcome outcome = new TcOutcome("TC_1", "Add backpack", TcStatus.PASSED,
                List.of(click), "", null, false, List.of());
        DomainCatalogWriter.write(dir, List.of(outcome));
        String md = Files.readString(dir.resolve("docs/DOMAIN_CATALOG.md"));
        Assert.assertTrue(md.contains("Inventory_Actions"), md);
        Assert.assertTrue(md.contains("TC_1Test"), md);
        Assert.assertTrue(md.contains("TC_1"), md);
        Assert.assertTrue(md.contains("mvn clean test"), md);
    }
}
