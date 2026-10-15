package delivery.portal.service.credits;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.hunt.HuntRequest;
import delivery.portal.model.JobRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BillableQuantityTest {

    @Test
    public void workbookChargesRunnableCasesOnly() throws Exception {
        Path excel = Files.createTempFile("bill-qty-", ".xlsx");
        ManualTcExcelWriter.write(excel, List.of(
                new ManualTestCase("TC_1", "Pay", "", "Open the shop", "The shop opens", "P2", "", "", "", "", ""),
                new ManualTestCase("TC_2", "Review", "", "Read the page", "A person checks it", "P2", "", "", "", "MANUAL", ""),
                new ManualTestCase("TC_3", "Cancel", "", "Open the shop", "The shop opens", "P2", "", "", "", "EXECUTE", "")
        ));
        Assert.assertEquals(BillableQuantity.workbookCases(excel), 2);
        JobRecord job = new JobRecord("job_qty", "prj", 1L, "NEW", excel, "https://example.com", "", "", false,
                JobRecord.JobKind.CONVERT);
        Assert.assertEquals(BillableQuantity.count(job), 2);
    }

    @Test
    public void huntChargesAllowedActions() throws Exception {
        HuntRequest hunt = new HuntRequest();
        hunt.setIterationCeiling(2);
        hunt.setCycleCeiling(3);
        hunt.setActionCapPerCycle(4);
        Path request = Files.createTempFile("bill-hunt-", ".json");
        new ObjectMapper().writeValue(request.toFile(), hunt);
        Assert.assertEquals(BillableQuantity.huntActions(request), 24);
        JobRecord job = new JobRecord("hunt_qty", "prj", 1L, "HUNT", request, "https://example.com", "", "", false,
                JobRecord.JobKind.HUNT);
        Assert.assertEquals(BillableQuantity.count(job), 24);
        Assert.assertEquals(BillableQuantity.breakdown(CreditsBillingKind.HUNT, 24, 15), "24 allowed actions × 15");
    }

    @Test
    public void unreadableWorkbookStaysOneUnit() throws Exception {
        Path excel = Files.createTempFile("bill-empty-", ".xlsx");
        Assert.assertEquals(BillableQuantity.workbookCases(excel), 1);
    }
}
