package delivery.portal.service;

import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeneratedWorkbookAddCaseTest {

    private GeneratedWorkbookService service() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot("./target/test-delivery-store-add-case");
        return new GeneratedWorkbookService(props);
    }

    @Test
    public void nextTcId_empty_isTc1() {
        Assert.assertEquals(GeneratedWorkbookService.nextTcId(List.of()), "TC_1");
    }

    @Test
    public void nextTcId_padded_keepsWidth() {
        Assert.assertEquals(GeneratedWorkbookService.nextTcId(List.of("TC_01", "TC_02")), "TC_03");
    }

    @Test
    public void addCaseFields_appendsNextId() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-add-case-" + System.nanoTime();
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "1. Shown", "P1", "", "", "", "")
        ), "GENERATE", "unit-test");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", "Manual case");
        fields.put("steps", "1. Open home");
        fields.put("expectedResult", "1. Home is shown");
        fields.put("keelPath", "");
        Map<String, Object> out = service.addCaseFields(projectId, fields, "https://example.test", "editor@example.com");
        Assert.assertEquals(out.get("tcCount"), 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) out.get("row");
        Assert.assertEquals(row.get("tcId"), "TC_02");
        Assert.assertEquals(row.get("title"), "Manual case");
        Assert.assertEquals(out.get("source"), "CASE_EDIT");
    }

    @Test
    public void addCaseFields_emptyLibrary_createsWorkbook() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-add-empty-" + System.nanoTime();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", "First");
        fields.put("steps", "1. Open home");
        fields.put("expectedResult", "1. Home is shown");
        Map<String, Object> out = service.addCaseFields(projectId, fields, "https://example.test", "editor@example.com");
        Assert.assertEquals(out.get("tcCount"), 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) out.get("row");
        Assert.assertEquals(row.get("tcId"), "TC_1");
    }
}
