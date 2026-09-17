package delivery.ops;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolated local shared installation. Not a second physical host and not kernel isolation.
 */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:p5shared;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.install.mode=shared",
        "delivery.install.private-cidrs=10.0.0.0/8",
        "delivery.store-root=./target/p5-shared-install/store",
        "delivery.work-dir=./target/p5-shared-install/work"
})
@AutoConfigureMockMvc
public class SharedInstallDrillTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Environment env;
    @Autowired
    private DeliveryPortalProperties props;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @org.testng.annotations.BeforeClass(alwaysRun = true)
    public void requireOwnJvm() {
        Phase5InstallSupport.requireExclusiveJvm("shared");
    }

    @BeforeMethod
    public void bindThisInstall() {
        Phase5InstallSupport.bindWorkerPolicy(env);
        Phase5InstallSupport.ensureBob(users, encoder);
    }

    @Test
    public void healthAndWorkerPolicyStaySharedDespitePoisonCidrs() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installMode").value("shared"));
        mockMvc.perform(get("/api/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeWritable").value(true));
        Phase5InstallSupport.assertSharedPolicyIgnoresCidrs();
    }

    @Test
    public void backupRestoreAndTenantBoundaryOnThisInstallStore() throws Exception {
        String aliceId = Phase5InstallSupport.createProject(
                mockMvc, Phase5InstallSupport.ADMIN, Phase5InstallSupport.ADMIN_PW,
                "Alice Shared", "https://same.example.com/shop");
        String bobId = Phase5InstallSupport.createProject(
                mockMvc, Phase5InstallSupport.BOB, Phase5InstallSupport.BOB_PW,
                "Bob Shared", "https://same.example.com/shop");
        mockMvc.perform(get("/api/projects/" + aliceId + "/artifacts")
                        .with(httpBasic(Phase5InstallSupport.BOB, Phase5InstallSupport.BOB_PW)))
                .andExpect(status().isNotFound());
        Phase5InstallSupport.backupRestoreTenantFiles(
                store, Path.of(props.getStoreRoot()), aliceId, bobId,
                Files.createTempDirectory("p5-shared-bak"));
    }

    @Test
    public void generateImportThenExecuteAutomateDownloadReplayCompile() throws Exception {
        String projectId = Phase5InstallSupport.createProject(
                mockMvc, Phase5InstallSupport.ADMIN, Phase5InstallSupport.ADMIN_PW,
                "Shared Flow", "https://example.com/");
        String raw = """
                {"testCases":[{"tcId":"TC_01","title":"Open example home","preconditions":"",
                "steps":"1. Open https://example.com/\\n2. Confirm the heading Home is visible",
                "expectedResult":"Home is visible","priority":"P1","tags":"install-drill",
                "visualAssertion":"","testData":"","keelPath":"AUTOMATE"}]}
                """.replace("\n", "");
        mockMvc.perform(post("/api/projects/" + projectId + "/generate/import")
                        .with(httpBasic(Phase5InstallSupport.ADMIN, Phase5InstallSupport.ADMIN_PW))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"json\",\"raw\":" + JSONObject.quote(raw) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray());

        MvcResult exec = mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .param("useGenerated", "true")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic(Phase5InstallSupport.ADMIN, Phase5InstallSupport.ADMIN_PW)))
                .andExpect(status().isAccepted())
                .andReturn();
        String execId = new JSONObject(exec.getResponse().getContentAsString()).getString("jobId");
        Assert.assertEquals(Phase5InstallSupport.waitStatus(
                mockMvc, "/api/execute-runs/" + execId,
                Phase5InstallSupport.ADMIN, Phase5InstallSupport.ADMIN_PW), "COMPLETED");

        MvcResult job = mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .param("useGenerated", "true")
                        .param("mode", "NEW")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic(Phase5InstallSupport.ADMIN, Phase5InstallSupport.ADMIN_PW)))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = new JSONObject(job.getResponse().getContentAsString()).getString("jobId");
        Assert.assertEquals(Phase5InstallSupport.waitStatus(
                mockMvc, "/api/jobs/" + jobId,
                Phase5InstallSupport.ADMIN, Phase5InstallSupport.ADMIN_PW), "COMPLETED");

        mockMvc.perform(get("/api/jobs/" + jobId + "/download")
                        .with(httpBasic(Phase5InstallSupport.ADMIN, Phase5InstallSupport.ADMIN_PW)))
                .andExpect(status().isOk());
        Path zip = store.getJob(jobId).orElseThrow().getZipPath();
        Assert.assertTrue(Files.isRegularFile(zip));
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Assert.assertNotNull(zf.getEntry("src/main/java/project/drivers/WebDriverFactory.java"));
        }
        Path unzip = Files.createTempDirectory("p5-shared-replay");
        try (var in = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            java.util.zip.ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                Path dest = unzip.resolve(e.getName()).normalize();
                if (!dest.startsWith(unzip)) {
                    throw new IllegalStateException(e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(in, dest);
                }
            }
        }
        String mvn = mavenCommand();
        Process compile = new ProcessBuilder(mvn, "-q", "-f", unzip.resolve("pom.xml").toString(),
                "test-compile")
                .directory(unzip.toFile())
                .inheritIO()
                .start();
        Assert.assertEquals(compile.waitFor(), 0, "downloaded ZIP test-compile failed via " + mvn);
    }

    private static String mavenCommand() {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        String name = win ? "mvn.cmd" : "mvn";
        String home = System.getProperty("maven.home", "");
        if (!home.isBlank()) {
            Path bin = Path.of(home, "bin", name);
            if (Files.isRegularFile(bin)) {
                return bin.toString();
            }
        }
        return name;
    }
}
