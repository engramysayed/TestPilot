package delivery.portal.service;

import delivery.excel.ExcelTcReader;
import delivery.excel.GenerateQualityGate;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeneratedWorkbookCaseUpdateTest {

    private GeneratedWorkbookService service() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot("./target/test-delivery-store-case-update");
        return new GeneratedWorkbookService(props);
    }

    @Test
    public void updateCaseFields_changesTitleAndCsv() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-case-update";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Original title", "", "1. Open login", "1. OK",
                        "P1", "tag1", "", "", "AUTOMATE")
        ), "GENERATE", "unit-test");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", "Updated title");
        fields.put("steps", "1. Open login\n2. Click submit");
        fields.put("expectedResult", "1. Page loads\n2. Form submits");
        fields.put("preconditions", "User logged out");
        fields.put("priority", "P2");
        fields.put("tags", "smoke");
        fields.put("visualAssertion", "Button visible");
        fields.put("testData", "user@test.com");
        fields.put("keelPath", "EXECUTE");

        Map<String, Object> updated = service.updateCaseFields(projectId, "TC_01", fields, "https://example.test");

        Assert.assertEquals(updated.get("tcCount"), 1);
        @SuppressWarnings("unchecked")
        Map<String, Integer> counts = (Map<String, Integer>) updated.get("keelPathCounts");
        Assert.assertEquals(counts.get("EXECUTE"), Integer.valueOf(1));
        Assert.assertEquals(counts.get("AUTOMATE"), Integer.valueOf(0));

        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) updated.get("row");
        Assert.assertEquals(row.get("title"), "Updated title");
        Assert.assertEquals(row.get("keelPath"), "EXECUTE");

        String csv = String.valueOf(updated.get("csv"));
        Assert.assertTrue(csv.contains("Updated title"));
        Assert.assertTrue(csv.contains("EXECUTE"));

        List<ManualTestCase> read = new ExcelTcReader().read(service.requireExcel(projectId));
        Assert.assertEquals(read.get(0).title(), "Updated title");
        Assert.assertEquals(read.get(0).keelPath(), "EXECUTE");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void updateCaseFields_rejectsQualityGateFailure() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-case-gate";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Good case", "", "1. Open login", "1. OK",
                        "P1", "", "", "", "AUTOMATE")
        ), "GENERATE", "unit-test");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("steps", "1. Open login\n2. Enter in the Phone field\n3. Enter in the Password field");
        fields.put("expectedResult", "Error message 'Incorrect email or phone number' is shown");

        try {
            service.updateCaseFields(projectId, "TC_01", fields, null);
            Assert.fail("Expected quality gate failure");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(GenerateQualityGate.isQualityGateFailure(e), e.getMessage());
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void updateCaseFields_unknownTcId() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-case-missing";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Case", "", "1. Click", "ok", "P1", "", "", "", "AUTOMATE")
        ), "GENERATE", "unit-test");
        service.updateCaseFields(projectId, "TC_99", Map.of("title", "Nope"), "https://example.test");
    }

    @Test
    public void updateCaseFields_persistsCallBefore() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-call-before-ok";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Login", "ok", "P1", "", "", "", "AUTOMATE"),
                new ManualTestCase("TC_06", "Feature", "", "1. Do thing", "ok", "P1", "", "", "", "AUTOMATE")
        ), "GENERATE", "unit-test");

        Map<String, Object> updated = service.updateCaseFields(
                projectId, "TC_06", Map.of("callBefore", "TC_01"), "https://example.test");

        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) updated.get("row");
        Assert.assertEquals(row.get("callBefore"), "TC_01");

        List<ManualTestCase> read = new ExcelTcReader().read(service.requireExcel(projectId));
        ManualTestCase tc06 = read.stream().filter(tc -> "TC_06".equals(tc.tcId())).findFirst().orElseThrow();
        Assert.assertEquals(tc06.callBefore(), "TC_01");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void updateCaseFields_rejectsSelfCallBefore() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-call-before-self";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_06", "Feature", "", "1. Do thing", "ok", "P1", "", "", "", "AUTOMATE")
        ), "GENERATE", "unit-test");

        try {
            service.updateCaseFields(projectId, "TC_06", Map.of("callBefore", "TC_06"), "https://example.test");
            Assert.fail("Expected self-reference rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().startsWith("CALL_BEFORE_SELF:"), e.getMessage());
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void updateCaseFields_rejectsUnknownCallBefore() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-call-before-unknown";
        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_06", "Feature", "", "1. Do thing", "ok", "P1", "", "", "", "AUTOMATE")
        ), "GENERATE", "unit-test");

        try {
            service.updateCaseFields(projectId, "TC_06", Map.of("callBefore", "TC_99"), "https://example.test");
            Assert.fail("Expected unknown callBefore rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().startsWith("UNKNOWN_CALL_BEFORE:"), e.getMessage());
            throw e;
        }
    }
}
