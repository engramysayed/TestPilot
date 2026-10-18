package delivery.portal.service;

import delivery.excel.ExcelTcReader;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class GeneratedWorkbookApplyReviewTest {

    private GeneratedWorkbookService service() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot("./target/test-delivery-store-apply-review");
        return new GeneratedWorkbookService(props);
    }

    @Test
    public void applyReviewedCases_upsertsAndKeepsOthers() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-apply-review";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "A", "", "1. Open a", "1. A is shown", "P1", "", "", "", ""),
                new ManualTestCase("TC_02", "B", "", "1. Open b", "1. B is shown", "P1", "", "", "", "")
        ), "GENERATE", "unit-test");
        ManualTestCase edited = new ManualTestCase("TC_01", "A", "", "1. Open a",
                "1. A is accepted", "P1", "", "", "", "");
        service.applyReviewedCases(projectId, List.of(edited), "editor@example.com", "https://example.test");
        List<ManualTestCase> all = new ExcelTcReader().read(service.requireExcel(projectId));
        Assert.assertEquals(all.size(), 2);
        Assert.assertTrue(all.stream().anyMatch(tc ->
                "TC_01".equals(tc.tcId()) && tc.expectedResult().contains("accepted")));
        Assert.assertTrue(all.stream().anyMatch(tc -> "TC_02".equals(tc.tcId())));
    }
}
