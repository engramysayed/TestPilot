package delivery.portal.service;

import delivery.excel.GenerateQualityGate;
import delivery.excel.GeneratedTcCsvParser;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.ProjectRecord;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TcGenerateServiceSaveComparedGateTest {

    private static final String PROJECT_ID = "fb-login-neg";
    private static final Long OWNER_USER_ID = 1L;
    private static final String BASE_URL = "https://www.facebook.com/";

    private TrackingWorkbooks workbooks;
    private TcGenerateService service;

    @BeforeMethod
    public void setUp() {
        workbooks = new TrackingWorkbooks();
        TcImportService tcImport = new TcImportService(stubStore(facebookProject()), workbooks);
        service = new TcGenerateService(
                enabledProps(),
                stubStore(facebookProject()),
                workbooks,
                new GenerateModelService(new DeliveryPortalProperties()),
                tcImport);
    }

    @Test
    public void saveCompared_badPhoneFieldCsv_throwsQualityGate() {
        try {
            service.saveCompared(
                    PROJECT_ID,
                    OWNER_USER_ID,
                    badPhoneFieldCsv(),
                    "gemma4:e2b",
                    "Compare side notes.");
            Assert.fail("expected quality gate failure");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(GenerateQualityGate.isQualityGateFailure(e), e.getMessage());
            Assert.assertTrue(e.getMessage().toLowerCase().contains("email or phone"), e.getMessage());
        } catch (Exception e) {
            Assert.fail("expected IllegalArgumentException, got " + e);
        }
        Assert.assertEquals(workbooks.saveCalls.get(), 0);
    }

    @Test
    public void saveCompared_goodCsv_runsGateAndSaves() throws Exception {
        Map<String, Object> result = service.saveCompared(
                PROJECT_ID,
                OWNER_USER_ID,
                goodCsv(),
                "gemma4:e2b",
                "Model A smoke.");

        Assert.assertEquals(workbooks.saveCalls.get(), 1);
        Assert.assertEquals(workbooks.lastSource.get(), "GENERATE_COMPARE");
        Assert.assertEquals(workbooks.lastModel.get(), "gemma4:e2b");
        Assert.assertEquals(result.get("projectId"), PROJECT_ID);
        Assert.assertEquals(result.get("model"), "gemma4:e2b");
        Assert.assertEquals(result.get("coverageNotes"), "Model A smoke.");
        Assert.assertNotNull(result.get("rows"));
        Assert.assertNotNull(result.get("csv"));
        Assert.assertNotNull(result.get("counts"));
        Assert.assertNotNull(result.get("keelPathCounts"));
    }

    private static String badPhoneFieldCsv() {
        ManualTestCase bad = new ManualTestCase(
                "TC_03",
                "Invalid password login with valid-looking phone number",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Enter in the Phone field
                3. Enter in the Password field
                4. Click the Log in button
                5. Confirm the message 'Incorrect email or phone number' is visible""",
                """
                1. Login page is shown
                2. Phone number is accepted
                3. Password is accepted
                4. Submit is clicked
                5. Error message 'Incorrect email or phone number' is shown""",
                "P1",
                "negative",
                "",
                """
                5551234567
                WrongPass123!

                """,
                "EXECUTE");
        return GeneratedTcCsvParser.toCsv(List.of(bad));
    }

    private static String goodCsv() {
        ManualTestCase good = new ManualTestCase(
                "TC_01",
                "Login",
                "",
                "1. Open login",
                "Shown",
                "",
                "",
                "",
                "",
                "AUTOMATE");
        return GeneratedTcCsvParser.toCsv(List.of(good));
    }

    private static DeliveryPortalProperties enabledProps() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setGenerateEnabled(true);
        return props;
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

    static final class TrackingWorkbooks extends GeneratedWorkbookService {
        final AtomicInteger saveCalls = new AtomicInteger();
        final AtomicReference<String> lastSource = new AtomicReference<>();
        final AtomicReference<String> lastModel = new AtomicReference<>();

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
            lastModel.set(model);
        }
    }
}
