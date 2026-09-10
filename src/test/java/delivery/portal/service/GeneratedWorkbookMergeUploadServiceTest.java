package delivery.portal.service;

import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class GeneratedWorkbookMergeUploadServiceTest {

    private GeneratedWorkbookService service() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot("./target/test-delivery-store-lib-merge");
        return new GeneratedWorkbookService(props);
    }

    private static ManualTestCase tc(String id, String title) {
        return new ManualTestCase(id, title, "", "1. Open page", "Page opens", "P1", "smoke", "", "", "AUTOMATE");
    }

    @Test
    public void mergeUploadFile_replacesSameIdAndKeepsOthers() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-lib-merge";
        service.saveFromCases(projectId, List.of(
                tc("TC_01", "Old login"),
                tc("TC_02", "Keep me")
        ), "GENERATE", "unit-test");

        Path upload = Path.of("target/test-lib-merge-upload.xlsx");
        Files.createDirectories(upload.getParent());
        ManualTcExcelWriter.write(upload, List.of(
                tc("TC_01", "New login"),
                tc("TC_03", "Create user")
        ));

        Map<String, Object> out = service.mergeUploadFile(
                projectId, "cases.xlsx", Files.readAllBytes(upload), "https://example.test");

        Assert.assertEquals(out.get("replacedCount"), 1);
        Assert.assertEquals(out.get("addedCount"), 1);
        Assert.assertEquals(out.get("tcCount"), 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cases = (List<Map<String, Object>>) out.get("cases");
        Assert.assertEquals(cases.get(0).get("title"), "New login");
        Assert.assertEquals(cases.get(1).get("tcId"), "TC_02");
        Assert.assertEquals(cases.get(2).get("tcId"), "TC_03");
    }
}
