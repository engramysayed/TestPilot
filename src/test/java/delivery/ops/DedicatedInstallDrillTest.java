package delivery.ops;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolated local dedicated installation. Not a second physical host and not kernel isolation.
 */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:p5dedicated;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.install.mode=dedicated",
        "delivery.install.private-cidrs=10.0.0.0/8",
        "delivery.store-root=./target/p5-dedicated-install/store",
        "delivery.work-dir=./target/p5-dedicated-install/work"
})
@AutoConfigureMockMvc
public class DedicatedInstallDrillTest extends AbstractTestNGSpringContextTests {

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
        Phase5InstallSupport.requireExclusiveJvm("dedicated");
    }

    @BeforeMethod
    public void bindThisInstall() {
        Phase5InstallSupport.bindWorkerPolicy(env);
        Phase5InstallSupport.ensureBob(users, encoder);
    }

    @Test
    public void healthAndWorkerPolicyHonorDedicatedCidrs() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installMode").value("dedicated"));
        mockMvc.perform(get("/api/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeWritable").value(true));
        Phase5InstallSupport.assertDedicatedPolicyAllowsCidr();
    }

    @Test
    public void backupRestoreAndTenantBoundaryOnThisInstallStore() throws Exception {
        String aliceId = Phase5InstallSupport.createProject(
                mockMvc, Phase5InstallSupport.ADMIN, Phase5InstallSupport.ADMIN_PW,
                "Alice Dedicated", "http://10.0.0.8:8080/app");
        String bobId = Phase5InstallSupport.createProject(
                mockMvc, Phase5InstallSupport.BOB, Phase5InstallSupport.BOB_PW,
                "Bob Dedicated", "http://10.0.0.8:8080/app");
        mockMvc.perform(get("/api/projects/" + aliceId + "/artifacts")
                        .with(httpBasic(Phase5InstallSupport.BOB, Phase5InstallSupport.BOB_PW)))
                .andExpect(status().isNotFound());
        Phase5InstallSupport.backupRestoreTenantFiles(
                store, Path.of(props.getStoreRoot()), aliceId, bobId,
                Files.createTempDirectory("p5-dedicated-bak"));
    }
}
