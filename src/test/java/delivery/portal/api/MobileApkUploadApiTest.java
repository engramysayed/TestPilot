package delivery.portal.api;

import delivery.identity.TenantId;
import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceRole;
import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-m107-upload;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=update",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-mobile-m107-upload",
        "delivery.work-dir=./target/test-delivery-work-mobile-m107-upload",
        "delivery.mobile.enabled=true",
        "delivery.mobile.live-tests=false",
        "delivery.mobile.upload-max-bytes=4096"
})
@AutoConfigureMockMvc
@Import(MobileApiApkTestSupport.ApkBackendConfig.class)
public class MobileApkUploadApiTest extends AbstractTestNGSpringContextTests {

    @Autowired MockMvc mockMvc;
    @Autowired PortalStore store;
    @Autowired PortalUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeMethod
    public void ensureMember() {
        MobileApiApkTestSupport.MODE.set(MobileApiApkTestSupport.Mode.READY);
        if (!users.existsByEmailIgnoreCase("member@testpilot.local")) {
            PortalUser u = new PortalUser();
            u.setEmail("member@testpilot.local");
            u.setPasswordHash(encoder.encode("MemberPass1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    private long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }

    private long memberId() {
        return users.findByEmailIgnoreCase("member@testpilot.local").orElseThrow().getId();
    }

    private String createAndroid() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Upload App\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void ownerUploadReadyReturnsRevisionWithFingerprints() throws Exception {
        String projectId = createAndroid();
        MockMultipartFile apk = MobileApiApkTestSupport.validApkMultipart("demo.apk");
        MvcResult res = mockMvc.perform(multipart("/api/projects/" + projectId + "/mobile/apps")
                        .file(apk)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject body = new JSONObject(res.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("status"), "READY");
        Assert.assertTrue(body.has("sha256"));
        JSONObject rev = body.getJSONObject("revision");
        Assert.assertEquals(rev.getString("source"), "APK");
        Assert.assertTrue(rev.has("sha256"));
        Assert.assertTrue(rev.has("certificateDigest"));
        Assert.assertTrue(rev.getString("apkBlobRef").startsWith("mobile/apps/"));
        Assert.assertFalse(rev.getString("apkBlobRef").contains("/home/"));
        Assert.assertFalse(res.getResponse().getContentAsString().contains("serialFingerprint"));
        Assert.assertEquals(rev.getString("launchActivity"), "com.example.app.MainActivity");
        Assert.assertEquals(rev.getString("waitActivity"), "com.example.app.MainActivity");
    }

    @Test
    public void activitySelectionRequiredThenConfirm() throws Exception {
        String projectId = createAndroid();
        MobileApiApkTestSupport.MODE.set(MobileApiApkTestSupport.Mode.MULTI);
        MockMultipartFile apk = MobileApiApkTestSupport.validApkMultipart("multi-launcher-demo.apk");
        MvcResult first = mockMvc.perform(multipart("/api/projects/" + projectId + "/mobile/apps")
                        .file(apk)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        JSONObject body = new JSONObject(first.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("status"), "ACTIVITY_SELECTION_REQUIRED");
        Assert.assertEquals(body.getString("errorCode"), "ACTIVITY_REQUIRED");
        Assert.assertTrue(body.getString("message").toLowerCase().contains("choose")
                || body.getString("message").toLowerCase().contains("screen"));
        JSONArray candidates = body.getJSONArray("launcherCandidates");
        Assert.assertTrue(candidates.length() >= 2);
        String uploadId = body.getString("uploadId");
        Assert.assertTrue(uploadId.startsWith("upl_"));

        String launch = candidates.getString(0);
        MvcResult confirm = mockMvc.perform(multipart("/api/projects/" + projectId + "/mobile/apps")
                        .param("uploadId", uploadId)
                        .param("selectedLaunchActivity", launch)
                        .param("waitActivity", launch + "Wait")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject ready = new JSONObject(confirm.getResponse().getContentAsString());
        Assert.assertEquals(ready.getString("status"), "READY");
        Assert.assertEquals(ready.getJSONObject("revision").getString("launchActivity"), launch);
        Assert.assertEquals(ready.getJSONObject("revision").getString("waitActivity"), launch + "Wait");
    }

    @Test
    public void plainErrorCodesAndMemberForbidden() throws Exception {
        String projectId = createAndroid();
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "big.apk", "application/vnd.android.package-archive",
                new byte[8192]);
        MvcResult oversizedRes = mockMvc.perform(multipart("/api/projects/" + projectId + "/mobile/apps")
                        .file(oversized)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        JSONObject err = new JSONObject(oversizedRes.getResponse().getContentAsString());
        Assert.assertEquals(err.getString("errorCode"), "OVERSIZED");
        Assert.assertTrue(err.getString("message").toLowerCase().contains("too large"));

        MockMultipartFile unsafe = MobileApiApkTestSupport.validApkMultipart("ok.apk");
        // MultipartFile with traversal-like original filename
        MockMultipartFile traversal = new MockMultipartFile(
                "file", "../evil.apk", "application/vnd.android.package-archive",
                unsafe.getBytes());
        MvcResult unsafeRes = mockMvc.perform(multipart("/api/projects/" + projectId + "/mobile/apps")
                        .file(traversal)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        Assert.assertEquals(new JSONObject(unsafeRes.getResponse().getContentAsString())
                .getString("errorCode"), "UNSAFE_NAME");

        MockMultipartFile corrupt = MobileApiApkTestSupport.corruptApkMultipart("bad.apk");
        MvcResult corruptRes = mockMvc.perform(multipart("/api/projects/" + projectId + "/mobile/apps")
                        .file(corrupt)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        Assert.assertEquals(new JSONObject(corruptRes.getResponse().getContentAsString())
                .getString("errorCode"), "APK_INVALID");

        var project = store.getOwnedProject(projectId, adminId()).orElseThrow();
        WorkspaceDirectory.open(Path.of("./target/test-delivery-store-mobile-m107-upload"))
                .addMember(TenantId.parse(project.getTenantId()), memberId(), WorkspaceRole.MEMBER);

        mockMvc.perform(multipart("/api/projects/" + projectId + "/mobile/apps")
                        .file(MobileApiApkTestSupport.validApkMultipart("m.apk"))
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/projects/" + projectId + "/mobile/app")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
    }
}
