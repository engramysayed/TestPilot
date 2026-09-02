package delivery.portal.service;

import delivery.excel.ExcelTcReader;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class GeneratedWorkbookKeelPathUpdateTest {

    private GeneratedWorkbookService service() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot("./target/test-delivery-store-keelpath-update");
        return new GeneratedWorkbookService(props);
    }

    @Test
    public void updateKeelPaths_changesDescribeCounts() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-keel-update";

        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Automate case", "", "1. Click", "ok", "P1", "", "", "", "AUTOMATE"),
                new ManualTestCase("TC_02", "Execute case", "", "1. Open", "ok", "P1", "", "", "", "EXECUTE")
        ), "GENERATE", "unit-test");

        @SuppressWarnings("unchecked")
        Map<String, Integer> before = (Map<String, Integer>) service.describe(projectId).orElseThrow()
                .get("keelPathCounts");
        Assert.assertEquals(before.get("AUTOMATE"), Integer.valueOf(1));
        Assert.assertEquals(before.get("EXECUTE"), Integer.valueOf(1));

        Map<String, Object> updated = service.updateKeelPaths(projectId, Map.of("TC_01", "MANUAL"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> after = (Map<String, Integer>) updated.get("keelPathCounts");
        Assert.assertEquals(after.get("AUTOMATE"), Integer.valueOf(0));
        Assert.assertEquals(after.get("MANUAL"), Integer.valueOf(1));
        Assert.assertEquals(after.get("EXECUTE"), Integer.valueOf(1));

        List<ManualTestCase> read = new ExcelTcReader().read(service.requireExcel(projectId));
        Assert.assertEquals(read.get(0).keelPath(), "MANUAL");
        Assert.assertEquals(read.get(1).keelPath(), "EXECUTE");

        String csv = String.valueOf(updated.get("csv"));
        Assert.assertTrue(csv.contains("TC_01"), "csv should include updated row");
        Assert.assertTrue(csv.contains("MANUAL"), "csv should reflect new KeelPath");
        Assert.assertFalse(csv.matches("(?s).*TC_01.*AUTOMATE.*"), "csv should not keep old KeelPath for TC_01");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void updateKeelPaths_rejectsUnknownKeelPath() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-keel-bad-path";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Case", "", "1. Click", "ok", "P1", "", "", "", "AUTOMATE")
        ), "GENERATE", "unit-test");
        service.updateKeelPaths(projectId, Map.of("TC_01", "NOT_A_PATH"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void updateKeelPaths_withoutWorkbook_throws() throws Exception {
        service().updateKeelPaths("missing-project", Map.of("TC_01", "MANUAL"));
    }

    @Test
    public void updateKeelPaths_preservesOtherMetaFields() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-keel-meta";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Case", "", "1. Click", "ok", "P1", "", "", "", "AUTOMATE")
        ), "GENERATE", "keep-ref", "gemma4:e2b");

        service.updateKeelPaths(projectId, Map.of("TC_01", "EXECUTE"));

        Map<String, Object> meta = service.describe(projectId).orElseThrow();
        Assert.assertEquals(meta.get("source"), "GENERATE");
        Assert.assertEquals(meta.get("sourceRef"), "keep-ref");
        Assert.assertEquals(meta.get("model"), "gemma4:e2b");
    }
}
