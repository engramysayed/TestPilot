package delivery.portal.service;

import delivery.excel.ExcelTcReader;
import delivery.excel.GeneratedTcCsvParser;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthoringReviewServiceTest {
    private static final String PROJECT_ID = "review-project";
    private static final Long OWNER_ID = 7L;

    @Test
    public void review_ollama_returnsPreview_withoutSavingExcel() throws Exception {
        Fixture fixture = fixture(List.of(originalCase()), validReviewJson());
        byte[] before = Files.readAllBytes(fixture.workbooks.requireExcel(PROJECT_ID));

        Map<String, Object> result = fixture.service.review(
                PROJECT_ID, OWNER_ID, " OLLAMA ", "gemma4:e2b", "Keep fixes minimal", "Empty email is rejected");

        Assert.assertEquals(result.get("provider"), "ollama");
        Assert.assertEquals(result.get("model"), "gemma4:e2b");
        Assert.assertEquals(fixture.lastModel, "gemma4:e2b");
        Assert.assertEquals(result.get("tcCount"), 1);
        Assert.assertEquals(result.get("previewOk"), Boolean.TRUE);
        Assert.assertEquals(result.get("gateErrors"), List.of());
        Assert.assertEquals(result.get("coverageNotes"), "Empty-email coverage retained.");
        Assert.assertTrue(String.valueOf(result.get("csv")).contains("Leave the Email field empty"));
        Assert.assertEquals(((List<?>) result.get("findings")).size(), 1);
        List<?> cases = (List<?>) result.get("cases");
        Assert.assertEquals(cases.size(), 1);
        Assert.assertEquals(((Map<?, ?>) cases.get(0)).get("tcId"), "TC_01");
        Assert.assertTrue(String.valueOf(((Map<?, ?>) cases.get(0)).get("steps"))
                .contains("Leave the Email field empty"));
        Assert.assertEquals(Files.readAllBytes(fixture.workbooks.requireExcel(PROJECT_ID)), before);
        Assert.assertEquals(
                new ExcelTcReader().read(fixture.workbooks.requireExcel(PROJECT_ID)).get(0).steps(),
                originalCase().steps());
        Assert.assertEquals(fixture.lastProvider, "ollama");
        Assert.assertTrue(fixture.lastSystem.contains("Keel authoring reviewer"));
        Assert.assertTrue(fixture.lastUser.contains("Empty email is rejected"));
        Assert.assertTrue(fixture.lastUser.contains("Keep fixes minimal"));
    }

    @Test
    public void review_unknownProvider_fails() throws Exception {
        Fixture fixture = fixture(List.of(originalCase()), validReviewJson());

        IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.service.review(PROJECT_ID, OWNER_ID, "openai", "", "", ""));

        Assert.assertEquals(error.getMessage(), "provider must be cursor or ollama");
        Assert.assertEquals(fixture.calls, 0);
    }

    @Test
    public void review_ollamaWithoutModel_fails() throws Exception {
        Fixture fixture = fixture(List.of(originalCase()), validReviewJson());

        IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.service.review(PROJECT_ID, OWNER_ID, "ollama", "  ", "", ""));

        Assert.assertEquals(error.getMessage(), "Ollama model must be specified");
        Assert.assertEquals(fixture.calls, 0);
    }

    @Test
    public void review_inputOverCap_fails() throws Exception {
        List<ManualTestCase> cases = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            cases.add(new ManualTestCase(
                    String.format("TC_%02d", i), "Case " + i, "",
                    "1. Open the form", "1. The form is shown", "P2", "", "", "", "EXECUTE"));
        }
        Fixture fixture = fixture(cases, validReviewJson());

        IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.service.review(PROJECT_ID, OWNER_ID, "cursor", "", "", ""));

        Assert.assertEquals(error.getMessage(), "Suite exceeds 50 cases");
        Assert.assertEquals(fixture.calls, 0);
    }

    @Test
    public void review_droppedInputId_failsClosed() throws Exception {
        Fixture fixture = fixture(
                List.of(originalCase(), simpleCase("TC_02")),
                reviewJson(List.of(simpleCase("TC_01"))));

        IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.service.review(PROJECT_ID, OWNER_ID, "ollama", "gemma4:e2b", "", ""));

        Assert.assertTrue(error.getMessage().contains("missing input tcId 'TC_02'"), error.getMessage());
    }

    @Test
    public void review_duplicateInputId_failsClosed() throws Exception {
        Fixture fixture = fixture(
                List.of(originalCase()),
                reviewJson(List.of(simpleCase("TC_01"), simpleCase("TC_01"))));

        IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.service.review(PROJECT_ID, OWNER_ID, "ollama", "gemma4:e2b", "", ""));

        Assert.assertTrue(error.getMessage().contains("duplicate tcId 'TC_01'"), error.getMessage());
    }

    @Test
    public void review_outputOverCap_failsClosed() throws Exception {
        List<ManualTestCase> output = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            output.add(simpleCase(String.format("TC_%02d", i)));
        }
        Fixture fixture = fixture(List.of(originalCase()), reviewJson(output));

        IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.service.review(PROJECT_ID, OWNER_ID, "ollama", "gemma4:e2b", "", ""));

        Assert.assertTrue(error.getMessage().contains("returned more than 50 cases"), error.getMessage());
    }

    @Test
    public void review_cursorBlank_failsClosed_withoutFallback() throws Exception {
        Fixture fixture = fixture(List.of(originalCase()), " ");

        IllegalStateException error = Assert.expectThrows(
                IllegalStateException.class,
                () -> fixture.service.review(PROJECT_ID, OWNER_ID, "cursor", "", "", ""));

        Assert.assertEquals(error.getMessage(), "cursor returned empty review");
        Assert.assertEquals(fixture.calls, 1);
        Assert.assertEquals(fixture.lastProvider, "cursor");
    }

    @Test
    public void reviewCases_reviewsProspectiveRunSuite_withoutReadingSavedWorkbook() throws Exception {
        Fixture fixture = fixture(List.of(originalCase()), validReviewJson());
        ManualTestCase prospective = new ManualTestCase(
                "TC_01", "Uploaded title", "",
                "1. Enter in the Email field\n2. Click Submit",
                "1. Email is accepted\n2. Submitted",
                "P1", "", "", "", "EXECUTE");

        Map<String, Object> result = fixture.service.reviewCases(
                PROJECT_ID, OWNER_ID, List.of(prospective), "cursor", "", "", "");

        Assert.assertEquals(result.get("provider"), "cursor");
        Assert.assertEquals(result.get("tcCount"), 1);
        Assert.assertTrue(fixture.lastUser.contains("Uploaded title"),
                "review must use the prospective upload/library mix");
    }

    private static Fixture fixture(List<ManualTestCase> cases, String response) throws Exception {
        Path root = Files.createTempDirectory("authoring-review-service");
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(root.toString());
        GeneratedWorkbookService workbooks = new GeneratedWorkbookService(props);
        workbooks.saveFromCases(PROJECT_ID, cases, "TEST", "fixture");

        Fixture fixture = new Fixture(workbooks);
        AuthoringReviewService.ReviewLlmPort port = (provider, model, system, user) -> {
            fixture.calls++;
            fixture.lastProvider = provider;
            fixture.lastModel = model;
            fixture.lastSystem = system;
            fixture.lastUser = user;
            return response;
        };
        fixture.service = new AuthoringReviewService(props, store(), workbooks, port);
        return fixture;
    }

    private static PortalStore store() {
        ProjectEntity project = new ProjectEntity();
        project.setProjectId(PROJECT_ID);
        project.setOwnerUserId(OWNER_ID);
        project.setName("Review project");
        project.setBaseUrl("https://example.test");
        ProjectRepository projects = mock(ProjectRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(projects.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(project));
        when(jobs.findByProjectId(PROJECT_ID)).thenReturn(List.of());
        return new PortalStore(new DeliveryPortalProperties(), projects, jobs);
    }

    private static ManualTestCase originalCase() {
        return new ManualTestCase(
                "TC_01", "Reject empty email", "",
                "1. Enter in the Email field\n2. Click Submit",
                "1. Email remains empty\n2. Message 'Email is required' is shown",
                "P1", "negative", "", "somebody@example.test\n", "EXECUTE");
    }

    private static ManualTestCase simpleCase(String tcId) {
        return new ManualTestCase(
                tcId, "Simple case", "",
                "1. Open the form", "1. The form is shown",
                "P2", "", "", "", "EXECUTE");
    }

    private static String reviewJson(List<ManualTestCase> cases) {
        String csv = GeneratedTcCsvParser.toCsv(cases);
        List<ManualTestCase> normalized = GeneratedTcCsvParser.parse(csv);
        StringBuilder json = new StringBuilder(
                "{\"findings\":[],\"coverageNotes\":\"\",\"cases\":[");
        for (int i = 0; i < normalized.size(); i++) {
            ManualTestCase tc = normalized.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append(new org.json.JSONObject()
                    .put("tcId", tc.tcId())
                    .put("title", tc.title())
                    .put("preconditions", tc.preconditions())
                    .put("steps", tc.steps())
                    .put("expectedResult", tc.expectedResult())
                    .put("priority", tc.priority())
                    .put("tags", tc.tags())
                    .put("visualAssertion", tc.visualAssertion())
                    .put("testData", tc.testData())
                    .put("keelPath", tc.keelPath()));
        }
        return json.append("]}").toString();
    }

    private static String validReviewJson() {
        return """
                {
                  "findings": [{
                    "severity": "warning",
                    "tcId": "TC_01",
                    "message": "The empty-email step had populated TestData."
                  }],
                  "cases": [{
                    "tcId": "TC_01",
                    "title": "Reject empty email",
                    "preconditions": "",
                    "steps": "1. Leave the Email field empty\\n2. Click Submit\\n3. Confirm the message 'Email is required' is visible",
                    "expectedResult": "1. Email remains empty\\n2. Form is submitted\\n3. Message 'Email is required' is shown",
                    "priority": "P1",
                    "tags": "negative",
                    "visualAssertion": "",
                    "testData": "\\n\\n",
                    "keelPath": "EXECUTE"
                  }],
                  "coverageNotes": "Empty-email coverage retained."
                }
                """;
    }

    private static final class Fixture {
        private final GeneratedWorkbookService workbooks;
        private AuthoringReviewService service;
        private int calls;
        private String lastProvider;
        private String lastModel;
        private String lastSystem;
        private String lastUser;

        private Fixture(GeneratedWorkbookService workbooks) {
            this.workbooks = workbooks;
        }
    }
}
