package delivery.portal.service;

import delivery.excel.GenerateQualityGate;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.ProjectRecord;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import delivery.portal.persistence.JobRepository;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TcImportServiceTest {

    private static final String PROJECT_ID = "fb-login-neg";
    private static final Long OWNER_USER_ID = 1L;
    private static final String BASE_URL = "https://www.facebook.com/";
    private static final String GOLDEN_JSON = "generate/facebook-login-negative-golden.json";

    private TrackingWorkbooks workbooks;
    private TcImportService service;

    @BeforeMethod
    public void setUp() {
        workbooks = new TrackingWorkbooks();
        service = new TcImportService(stubStore(facebookProject()), workbooks);
    }

    @Test
    public void importRaw_jsonGolden_savesAndReturnsRows() throws Exception {
        Map<String, Object> result = service.importRaw(
                PROJECT_ID, OWNER_USER_ID, readGoldenJson(), "auto", "PASTE_IMPORT");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        Assert.assertNotNull(rows);
        Assert.assertTrue(rows.size() >= 8, "expected at least 8 rows, got " + rows.size());
        Assert.assertEquals(result.get("projectId"), PROJECT_ID);
        Assert.assertNotNull(result.get("csv"));
        Assert.assertNotNull(result.get("counts"));
        Assert.assertNotNull(result.get("keelPathCounts"));
        Assert.assertEquals(workbooks.saveCalls.get(), 1);
        Assert.assertEquals(workbooks.lastSource.get(), "PASTE_IMPORT");
        Assert.assertTrue(workbooks.lastCases.get().size() >= 8);
    }

    @Test
    public void importRaw_jsonGolden_includesCoverageNotes() throws Exception {
        Map<String, Object> result = service.importRaw(
                PROJECT_ID, OWNER_USER_ID, readGoldenJson(), "auto", "PASTE_IMPORT");

        Object notes = result.get("coverageNotes");
        Assert.assertNotNull(notes);
        Assert.assertTrue(notes.toString().contains("missing credentials"),
                "expected coverage notes from golden JSON, got: " + notes);
    }

    @Test
    public void importRaw_badPhoneField_throwsQualityGate() {
        try {
            service.importRaw(PROJECT_ID, OWNER_USER_ID, badPhoneFieldJson(), "auto", "PASTE_IMPORT");
            Assert.fail("expected quality gate failure");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(GenerateQualityGate.isQualityGateFailure(e), e.getMessage());
            Assert.assertTrue(e.getMessage().toLowerCase().contains("email or phone"), e.getMessage());
        } catch (Exception e) {
            Assert.fail("expected IllegalArgumentException, got " + e);
        }
        Assert.assertEquals(workbooks.saveCalls.get(), 0);
    }

    private static PortalStore stubStore(ProjectRecord project) {
        ProjectEntity entity = new ProjectEntity();
        entity.setProjectId(project.getProjectId());
        entity.setOwnerUserId(project.getOwnerUserId());
        entity.setBaseUrl(project.getBaseUrl());
        entity.setName(project.getName());

        ProjectRepository projectRepository = mock(ProjectRepository.class);
        JobRepository jobRepository = mock(JobRepository.class);
        when(projectRepository.findByProjectId(project.getProjectId())).thenReturn(Optional.of(entity));

        return new PortalStore(new DeliveryPortalProperties(), projectRepository, jobRepository);
    }

    private static ProjectRecord facebookProject() {
        ProjectRecord project = new ProjectRecord(PROJECT_ID, "Facebook Login Negative", OWNER_USER_ID, 1);
        project.setBaseUrl(BASE_URL);
        return project;
    }

    private static String readGoldenJson() throws Exception {
        try (InputStream in = TcImportServiceTest.class.getClassLoader().getResourceAsStream(GOLDEN_JSON)) {
            Assert.assertNotNull(in, "Missing test resource: " + GOLDEN_JSON);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String badPhoneFieldJson() {
        return """
                {
                  "testCases": [{
                    "tcId": "TC_03",
                    "title": "Invalid password login with valid-looking phone number",
                    "preconditions": "No login required.",
                    "steps": "1. Open the Login Page at https://www.facebook.com/\\n2. Enter in the Phone field\\n3. Enter in the Password field\\n4. Click the Log in button\\n5. Confirm the message 'Incorrect email or phone number' is visible",
                    "expectedResult": "1. Login page is shown\\n2. Phone number is accepted\\n3. Password is accepted\\n4. Submit is clicked\\n5. Error message 'Incorrect email or phone number' is shown",
                    "priority": "P1",
                    "tags": "negative",
                    "visualAssertion": "",
                    "testData": "5551234567\\nWrongPass123!\\n\\n",
                    "keelPath": "EXECUTE"
                  }],
                  "coverageNotes": ""
                }
                """;
    }

    static final class TrackingWorkbooks extends GeneratedWorkbookService {
        final AtomicInteger saveCalls = new AtomicInteger();
        final AtomicReference<String> lastSource = new AtomicReference<>();
        final AtomicReference<List<ManualTestCase>> lastCases = new AtomicReference<>();

        TrackingWorkbooks() {
            super(new DeliveryPortalProperties());
        }

        @Override
        public void saveFromCases(
                String projectId,
                List<ManualTestCase> cases,
                String source,
                String sourceRef,
                String model
        ) {
            saveCalls.incrementAndGet();
            lastSource.set(source);
            lastCases.set(cases);
        }
    }
}
