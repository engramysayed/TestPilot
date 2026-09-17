package delivery.ops;

import delivery.net.InstallNetworkBridge;
import delivery.net.TargetNetworkPolicy;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
import delivery.store.StoreBackup;
import org.json.JSONObject;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class Phase5InstallSupport {
    static final String ADMIN = "admin@testpilot.local";
    static final String ADMIN_PW = "ChangeMeAdmin1!";
    static final String BOB = "customer-b@testpilot.local";
    static final String BOB_PW = "CustomerBee1!";

    private Phase5InstallSupport() {
    }

    static final String DRILL_JVM = "delivery.install.drill";

    static void requireExclusiveJvm(String mode) {
        org.testng.Assert.assertEquals(System.getProperty(DRILL_JVM), mode,
                mode + " install drill must run in its own JVM (-D" + DRILL_JVM + "=" + mode + ")");
    }

    static void bindWorkerPolicy(Environment env) {
        InstallNetworkBridge.apply(env);
    }

    static void ensureBob(PortalUserRepository users, PasswordEncoder encoder) {
        if (!users.existsByEmailIgnoreCase(BOB)) {
            PortalUser u = new PortalUser();
            u.setEmail(BOB);
            u.setPasswordHash(encoder.encode(BOB_PW));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    static String createProject(MockMvc mockMvc, String email, String password, String name, String baseUrl)
            throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(email, password))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
        mockMvc.perform(patch("/api/projects/" + id)
                        .with(httpBasic(email, password))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"" + baseUrl + "\"}"))
                .andExpect(status().isOk());
        return id;
    }

    static void backupRestoreTenantFiles(
            PortalStore store, Path storeRoot, String aliceId, String bobId, Path backupDir
    ) throws Exception {
        Files.createDirectories(storeRoot);
        Path aliceRoot = store.projectDiskRoot(aliceId);
        Path bobRoot = store.projectDiskRoot(bobId);
        Files.createDirectories(aliceRoot.resolve("versions"));
        Files.createDirectories(bobRoot.resolve("versions"));
        byte[] alice = "ALICE_INSTALL_SENTINEL".getBytes();
        byte[] bob = "BOB_INSTALL_SENTINEL".getBytes();
        Path aliceZip = aliceRoot.resolve("versions/v1.zip");
        Path bobZip = bobRoot.resolve("versions/v1.zip");
        Files.write(aliceZip, alice);
        Files.write(bobZip, bob);
        String aliceSha = sha256(alice);
        Assert.assertNotEquals(aliceRoot, bobRoot);
        Assert.assertTrue(aliceRoot.toString().contains("ws_"));
        Assert.assertTrue(bobRoot.toString().contains("ws_"));
        Path snapshot = StoreBackup.snapshot(storeRoot.toAbsolutePath().normalize(), backupDir);
        Files.deleteIfExists(aliceZip);
        Files.deleteIfExists(bobZip);
        Assert.assertFalse(Files.isRegularFile(aliceZip));
        StoreBackup.restore(snapshot, storeRoot.toAbsolutePath().normalize());
        Assert.assertEquals(Files.readAllBytes(aliceZip), alice);
        Assert.assertEquals(Files.readAllBytes(bobZip), bob);
        Assert.assertEquals(sha256(Files.readAllBytes(aliceZip)), aliceSha);
    }

    static String sha256(byte[] body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    }

    static String waitStatus(MockMvc mockMvc, String path, String email, String password) throws Exception {
        String jobStatus = "QUEUED";
        for (int i = 0; i < 80 && !"COMPLETED".equals(jobStatus) && !"FAILED".equals(jobStatus); i++) {
            Thread.sleep(250);
            MvcResult poll = mockMvc.perform(get(path)
                            .with(httpBasic(email, password)))
                    .andExpect(status().isOk())
                    .andReturn();
            jobStatus = new JSONObject(poll.getResponse().getContentAsString()).getString("status");
        }
        return jobStatus;
    }

    static void assertSharedPolicyIgnoresCidrs() {
        TargetNetworkPolicy policy = TargetNetworkPolicy.forJob("https://shop.example.com");
        Assert.assertEquals(policy.mode(), TargetNetworkPolicy.Mode.SHARED);
        Assert.assertFalse(policy.inspect("http://10.0.0.8:8080/login").allowed());
        Assert.assertTrue(policy.inspect("https://shop.example.com/checkout").allowed());
    }

    static void assertDedicatedPolicyAllowsCidr() {
        TargetNetworkPolicy policy = TargetNetworkPolicy.forJob("http://10.0.0.8:8080/app");
        Assert.assertEquals(policy.mode(), TargetNetworkPolicy.Mode.DEDICATED);
        Assert.assertTrue(policy.inspect("http://10.0.0.8:8080/login").allowed());
        Assert.assertFalse(policy.inspect("http://192.168.0.4/").allowed());
    }
}
