package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.ProjectRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TcGenerateServiceCompareTest {

    private static final String MODEL_A_JSON = """
            {
              "testCases": [{
                "tcId": "TC_01",
                "title": "Login",
                "preconditions": "",
                "steps": "1. Open login",
                "expectedResult": "Shown",
                "priority": "",
                "tags": "",
                "visualAssertion": "",
                "testData": "",
                "keelPath": "AUTOMATE"
              }],
              "coverageNotes": "Model A smoke."
            }
            """;

    private static final String MODEL_B_JSON = """
            {
              "testCases": [
                {
                  "tcId": "TC_01",
                  "title": "Login",
                  "preconditions": "",
                  "steps": "1. Open login",
                  "expectedResult": "Shown",
                  "priority": "",
                  "tags": "",
                  "visualAssertion": "",
                  "testData": "",
                  "keelPath": "AUTOMATE"
                },
                {
                  "tcId": "TC_02",
                  "title": "Logout",
                  "preconditions": "",
                  "steps": "1. Click logout",
                  "expectedResult": "Signed out",
                  "priority": "",
                  "tags": "",
                  "visualAssertion": "",
                  "testData": "",
                  "keelPath": "EXECUTE"
                }
              ],
              "coverageNotes": "Model B broader."
            }
            """;

    @Test
    public void compare_runsBothModelsWithoutAutoSave() throws Exception {
        AtomicInteger saveCalls = new AtomicInteger();
        CompareStubTcGenerateService service = new CompareStubTcGenerateService(saveCalls);
        ProjectRecord project = new ProjectRecord("p1", "Demo", 1L, 1);

        Map<String, Object> result = service.compare(
                project,
                "As a user I can login",
                "gemma4:e2b",
                "qwen2.5:latest"
        );

        Assert.assertEquals(service.llmCallCount, 2);
        Assert.assertEquals(saveCalls.get(), 0, "compare must not auto-save workbook");

        @SuppressWarnings("unchecked")
        Map<String, Object> sideA = (Map<String, Object>) result.get("modelA");
        @SuppressWarnings("unchecked")
        Map<String, Object> sideB = (Map<String, Object>) result.get("modelB");
        Assert.assertNotNull(sideA);
        Assert.assertNotNull(sideB);

        @SuppressWarnings("unchecked")
        Map<String, Object> countsA = (Map<String, Object>) sideA.get("counts");
        @SuppressWarnings("unchecked")
        Map<String, Object> countsB = (Map<String, Object>) sideB.get("counts");
        Assert.assertEquals(countsA.get("AUTOMATE"), 1);
        Assert.assertEquals(countsB.get("AUTOMATE"), 1);
        Assert.assertEquals(countsB.get("EXECUTE"), 1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rowsA = (List<Map<String, Object>>) sideA.get("rows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rowsB = (List<Map<String, Object>>) sideB.get("rows");
        Assert.assertEquals(rowsA.size(), 1);
        Assert.assertEquals(rowsB.size(), 2);
        Assert.assertEquals(sideA.get("model"), "gemma4:e2b");
        Assert.assertEquals(sideB.get("model"), "qwen2.5:latest");
    }

    static final class CompareStubTcGenerateService extends TcGenerateService {
        int llmCallCount;
        private final AtomicInteger saveCalls;

        CompareStubTcGenerateService(AtomicInteger saveCalls) {
            super(
                    new DeliveryPortalProperties(),
                    null,
                    new TrackingWorkbooks(saveCalls),
                    new GenerateModelService(new DeliveryPortalProperties()),
                    null);
            this.saveCalls = saveCalls;
        }

        @Override
        String callOllama(String system, String user, String model) {
            llmCallCount++;
            if ("gemma4:e2b".equals(model)) {
                return MODEL_A_JSON;
            }
            if ("qwen2.5:latest".equals(model)) {
                return MODEL_B_JSON;
            }
            return MODEL_A_JSON;
        }
    }

    static final class TrackingWorkbooks extends GeneratedWorkbookService {
        private final AtomicInteger saveCalls;

        TrackingWorkbooks(AtomicInteger saveCalls) {
            super(new DeliveryPortalProperties());
            this.saveCalls = saveCalls;
        }

        @Override
        public void saveFromCases(
                String projectId,
                List<delivery.excel.ManualTestCase> cases,
                String source,
                String sourceRef,
                String model
        ) {
            saveCalls.incrementAndGet();
        }
    }
}
