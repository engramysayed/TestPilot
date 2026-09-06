package delivery.portal.service;

import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class GeneratedWorkbookDeleteCasesTest {

    private GeneratedWorkbookService service() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot("./target/test-delivery-store-case-delete");
        return new GeneratedWorkbookService(props);
    }

    @Test
    public void deleteCases_removesSelectedRows() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-case-delete";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "One", "", "1. Open", "1. OK", "P1", "", "", "", "AUTOMATE"),
                new ManualTestCase("TC_02", "Two", "", "1. Open", "1. OK", "P1", "", "", "", "EXECUTE")
        ), "GENERATE", "unit-test");

        Map<String, Object> out = service.deleteCases(projectId, List.of("TC_01"));
        Assert.assertEquals(out.get("removedCount"), 1);
        Assert.assertEquals(out.get("tcCount"), 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cases = (List<Map<String, Object>>) out.get("cases");
        Assert.assertEquals(cases.size(), 1);
        Assert.assertEquals(cases.get(0).get("tcId"), "TC_02");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void deleteCases_refusesLastCase() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-case-delete-last";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Only", "", "1. Open", "1. OK", "P1", "", "", "", "AUTOMATE")
        ), "GENERATE", "unit-test");
        service.deleteCases(projectId, List.of("TC_01"));
    }
}
