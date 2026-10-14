package delivery.portal.api;

import delivery.identity.TenantId;
import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceRole;
import delivery.mobile.MobilePortalStore;
import delivery.mobile.model.MobileAppRevisionRecord;
import delivery.mobile.model.MobileDeviceRecord;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-m107-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=update",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-mobile-m107-api",
        "delivery.work-dir=./target/test-delivery-work-mobile-m107-api",
        "delivery.mobile.enabled=true",
        "delivery.mobile.live-tests=false"
})
@AutoConfigureMockMvc
@Import(MobileApiApkTestSupport.ApkBackendConfig.class)
public class MobileAppDeviceApiTest extends AbstractTestNGSpringContextTests {

    @Autowired MockMvc mockMvc;
    @Autowired PortalStore store;
    @Autowired MobilePortalStore mobileStore;
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
                        .content("{\"name\":\"Android API\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JSONObject json = new JSONObject(created.getResponse().getContentAsString());
        Assert.assertEquals(json.getString("platform"), "ANDROID");
        return json.getString("projectId");
    }

    @Test
    public void ownerCanSaveAppAndPatchActivitiesForApkAndInstalled() throws Exception {
        String projectId = createAndroid();
        MvcResult putApk = mockMvc.perform(put("/api/projects/" + projectId + "/mobile/app")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"APK","packageName":"com.example.app",
                                 "versionCode":12,"versionName":"1.2.0",
                                 "launchActivity":"com.example.app.MainActivity",
                                 "waitActivity":"com.example.app.HomeActivity",
                                 "sha256":"abc123deadbeef"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject app = new JSONObject(putApk.getResponse().getContentAsString()).getJSONObject("app");
        Assert.assertEquals(app.getString("source"), "APK");
        Assert.assertEquals(app.getString("launchActivity"), "com.example.app.MainActivity");
        Assert.assertEquals(app.getString("waitActivity"), "com.example.app.HomeActivity");
        Assert.assertFalse(putApk.getResponse().getContentAsString().contains("password"));
        Assert.assertFalse(putApk.getResponse().getContentAsString().contains("serialFingerprint"));

        mockMvc.perform(patch("/api/projects/" + projectId + "/mobile/app")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"launchActivity\":\"com.example.app.Other\",\"waitActivity\":\"com.example.app.Ready\"}"))
                .andExpect(status().isOk());

        MvcResult putInstalled = mockMvc.perform(put("/api/projects/" + projectId + "/mobile/app")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"INSTALLED","packageName":"com.example.installed",
                                 "launchActivity":"com.example.installed.Launch",
                                 "waitActivity":"com.example.installed.Wait",
                                 "verification":"OBSERVED"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Assert.assertEquals(new JSONObject(putInstalled.getResponse().getContentAsString())
                .getJSONObject("app").getString("source"), "INSTALLED");
    }

    @Test
    public void memberCanReadButCannotMutateAndNeverSeesFullSerial() throws Exception {
        String projectId = createAndroid();
        var project = store.getOwnedProject(projectId, adminId()).orElseThrow();
        TenantId tenant = TenantId.parse(project.getTenantId());
        WorkspaceDirectory.open(Path.of("./target/test-delivery-store-mobile-m107-api"))
                .addMember(tenant, memberId(), WorkspaceRole.MEMBER);

        mobileStore.registerAppRevision(project.getTenantId(), projectId, revision("APK"));
        MobileDeviceRecord device = mobileStore.registerDevice(
                project.getTenantId(), "host_1",
                "FULL_SERIAL_MUST_NOT_LEAK_xyz", "ab12", "Pixel Lab", "Pixel 7", 34, "arm64-v8a");

        MvcResult getApp = mockMvc.perform(get("/api/projects/" + projectId + "/mobile/app")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        Assert.assertFalse(getApp.getResponse().getContentAsString().contains("FULL_SERIAL"));
        Assert.assertFalse(getApp.getResponse().getContentAsString().contains("serialFingerprint"));
        Assert.assertTrue(getApp.getResponse().getContentAsString().contains("\"canEdit\":false")
                        || getApp.getResponse().getContentAsString().contains("\"canEdit\": false"));

        mockMvc.perform(put("/api/projects/" + projectId + "/mobile/app")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"APK\",\"packageName\":\"x\",\"launchActivity\":\"a\",\"waitActivity\":\"b\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/projects/" + projectId + "/mobile/app")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"launchActivity\":\"evil\"}"))
                .andExpect(status().isForbidden());

        MvcResult devices = mockMvc.perform(get("/api/projects/" + projectId + "/mobile/devices")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        String body = devices.getResponse().getContentAsString();
        Assert.assertFalse(body.contains("FULL_SERIAL_MUST_NOT_LEAK"));
        Assert.assertFalse(body.contains("serialFingerprint"));
        Assert.assertTrue(body.contains("ab12") || body.contains(device.getSerialShort()),
                "serialShort should be present for UI");
        Assert.assertTrue(body.contains("Pixel Lab"));
        Assert.assertTrue(body.contains("READY") || body.contains("AUTHORIZED"));
        JSONArray arr = new JSONObject(body).getJSONArray("devices");
        Assert.assertTrue(arr.length() >= 1);
        JSONObject row = arr.getJSONObject(0);
        Assert.assertFalse(row.has("serialFingerprint"));
        Assert.assertTrue(row.has("serialShort"));
        Assert.assertTrue(row.has("displayName"));
        Assert.assertTrue(row.has("readiness"));
        Assert.assertTrue(row.has("authorizedState"));
        Assert.assertTrue(row.has("busy"));
    }

    private static MobileAppRevisionRecord revision(String source) {
        MobileAppRevisionRecord r = new MobileAppRevisionRecord();
        r.setSource(source);
        r.setPackageName("com.example.app");
        r.setLaunchActivity("com.example.app.Main");
        r.setWaitActivity("com.example.app.Home");
        return r;
    }
}
