package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1-07: project/settings/run UI for mobile app source, activities, device diagnostics.
 * Gated by delivery.mobile.enabled.
 */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-m107-mvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=update",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-mobile-m107-mvc",
        "delivery.work-dir=./target/test-delivery-work-mobile-m107-mvc",
        "delivery.mobile.enabled=true",
        "delivery.mobile.live-tests=false"
})
@AutoConfigureMockMvc
public class MobileSettingsMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired MockMvc mockMvc;
    private String createAndroidProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Phone App UI\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void settingsPageShowsMobilePanelsWhenEnabledAndAndroid() throws Exception {
        String projectId = createAndroidProject();
        String body = mockMvc.perform(get("/projects/" + projectId + "?tab=settings")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"mobile-app-panel\""), "mobile app panel missing");
        Assert.assertTrue(body.contains("App file (APK)"), "APK plain label missing");
        Assert.assertTrue(body.contains("Already installed on the phone"), "INSTALLED plain label missing");
        Assert.assertTrue(body.contains("Launch screen"), "launchActivity label missing");
        Assert.assertTrue(body.contains("Wait for this screen"), "waitActivity label missing");
        Assert.assertTrue(body.contains("id=\"mobile-devices-panel\""), "devices panel missing");
        Assert.assertTrue(body.contains("Phone name") || body.contains("Phones"), "device displayName label missing");
        Assert.assertTrue(body.contains("Device ID"), "serialShort label missing");
        Assert.assertTrue(body.contains("/mobile/app"), "app API wiring missing");
        Assert.assertTrue(body.contains("/mobile/devices"), "devices API wiring missing");
        Assert.assertTrue(body.contains("id=\"mobile-auth-profiles-panel\""), "auth profiles panel missing");
        Assert.assertTrue(body.contains("Sign-in profiles"), "auth profiles plain heading missing");
        Assert.assertTrue(body.contains("id=\"mobile-case-req-panel\""), "case requirements panel missing");
        Assert.assertTrue(body.contains("Case sign-in requirements"), "case req heading missing");
        Assert.assertTrue(body.contains("keel-mobile-auth.js"), "auth JS include missing");
        Assert.assertTrue(body.contains("keel-mobile-auth.js") || body.contains("id=\"mobile-auth-profiles-panel\""),
                "auth profiles UI wiring missing");
        Assert.assertTrue(body.contains("Clearing app data") || body.contains("clearing app data")
                        || body.contains("separate explicit"),
                "data-clear note missing");
        Assert.assertFalse(body.contains("serialFingerprint"), "must never echo full serial field name");
    }

    @Test
    public void settingsPageAuthJsExposesModesWithoutSecrets() throws Exception {
        String js = mockMvc.perform(get("/js/keel-mobile-auth.js")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(js.contains("ENSURE_LOGGED_IN"), "ENSURE_LOGGED_IN missing");
        Assert.assertTrue(js.contains("FRESH_LOGIN"), "FRESH_LOGIN missing");
        Assert.assertTrue(js.contains("LOGGED_OUT"), "LOGGED_OUT missing");
        Assert.assertTrue(js.contains("LOGIN_UNDER_TEST"), "LOGIN_UNDER_TEST missing");
        Assert.assertTrue(js.contains("/mobile/auth-profiles"), "auth-profiles path missing");
        Assert.assertTrue(js.contains("/requirements"), "case requirements path missing");
        Assert.assertTrue(js.contains("does not clear app data")
                        || js.contains("separate explicit"),
                "fresh-login vs data-clear note missing in JS");
        Assert.assertFalse(js.contains("passwordValue") || js.contains("rawPassword"),
                "JS must not reference raw password fields");
        Assert.assertFalse(js.contains("adbSerial"), "JS must not reference raw serial");
    }

    @Test
    public void projectsPageExposesPlatformSelectorWhenMobileEnabled() throws Exception {
        String body = mockMvc.perform(get("/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"create-platform-fieldset\""), "platform fieldset missing");
        Assert.assertTrue(body.contains("value=\"ANDROID\""), "ANDROID option missing");
        Assert.assertTrue(body.contains("Android app"), "Android plain label missing");
    }

    @Test
    public void executePageIncludesMobileDeviceDiagnosticsMarkup() throws Exception {
        String body = mockMvc.perform(get("/execute")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"mobile-run-devices-panel\""), "run devices panel missing");
        Assert.assertTrue(body.contains("keelLoadMobileRunDevices"), "device loader missing");
        Assert.assertTrue(body.contains("Busy — another run is using this phone")
                        || body.contains("Not authorized — unlock USB debugging on the phone"),
                "plain readiness labels missing");
        Assert.assertTrue(body.contains("id=\"mobile-results-panel\""), "mobile results panel missing");
        Assert.assertTrue(body.contains("keel-mobile-results.js"), "mobile results JS missing");
        Assert.assertTrue(body.contains("keelLoadMobileJobResult"), "mobile result loader hook missing");
        Assert.assertTrue(body.contains("Phone run results"), "plain results heading missing");
        Assert.assertTrue(body.contains("keel-mobile-results.js"), "results script include missing");
        Assert.assertFalse(body.contains("raw serial") || body.contains("adbSerial"),
                "must not mention raw serial in UI copy");
    }

    @Test
    public void executePageIncludesMobileOutcomeBadgeLabels() throws Exception {
        String body = mockMvc.perform(get("/execute")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("What ran on the phone"), "runtime identity section missing");
        Assert.assertTrue(body.contains("id=\"mobile-results-summary\""), "summary block missing");
        Assert.assertTrue(body.contains("id=\"mobile-results-runtime\""), "runtime identity block missing");
        Assert.assertTrue(body.contains("id=\"mobile-results-table\""), "cases table missing");
        Assert.assertTrue(body.contains("Phone run results"), "plain heading missing");
        // Outcome enums + serialFingerprint live in external JS (avoids Thymeleaf [(...)] inlining)
        String js = mockMvc.perform(get("/js/keel-mobile-results.js")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(js.contains("INTERRUPTED_UNCERTAIN"), "interrupted outcome key missing in JS");
        Assert.assertTrue(js.contains("EXCLUDED_SIMULATED"), "excluded outcome key missing in JS");
        Assert.assertTrue(js.contains("Blocked") && js.contains("Interrupted"),
                "plain outcome labels missing in JS");
        Assert.assertTrue(js.contains("setup could not finish")
                        || js.contains("outcome unclear"),
                "plain outcome detail missing in JS");
        Assert.assertTrue(js.contains("serialFingerprint"), "runtime uses serialFingerprint field");
        Assert.assertTrue(js.contains("/mobile-result"), "mobile-result API path missing in JS");
        Assert.assertFalse(js.contains("adbSerial") || js.contains("RAW_SERIAL"),
                "JS must not reference raw serial");
    }

    @Test
    public void webProjectSettingsHidesMobilePanelsClientSide() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Web Only\",\"platform\":\"WEB\",\"baseUrl\":\"https://ex.test/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
        String body = mockMvc.perform(get("/projects/" + projectId + "?tab=settings")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // Panels exist in DOM but hidden attribute / JS gate for non-ANDROID
        Assert.assertTrue(body.contains("data-project-platform=\"WEB\"")
                        || body.contains("data-project-platform=\"web\"")
                        || body.contains("data-project-platform=\"WEB\""),
                "WEB platform attr missing");
        Assert.assertTrue(body.contains("id=\"mobile-app-panel\"") && body.contains("hidden"),
                "mobile panel should be present but hidden for WEB");
    }

    @Test
    public void settingsPageShowsApkUploadAndLauncherPickerMarkup() throws Exception {
        String projectId = createAndroidProject();
        String body = mockMvc.perform(get("/projects/" + projectId + "?tab=settings")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"mobile-apk-upload-wrap\""), "APK upload wrap missing");
        Assert.assertTrue(body.contains("id=\"mobile-apk-file\""), "APK file input missing");
        Assert.assertTrue(body.contains("id=\"mobile-launcher-pick\""), "launcher picker missing");
        Assert.assertTrue(body.contains("/mobile/apps"), "upload API wiring missing");
        Assert.assertTrue(body.contains("File fingerprint (SHA-256)")
                        || body.contains("Signing certificate fingerprint")
                        || body.contains("showFingerprints"),
                "fingerprint labels/wiring missing");
        Assert.assertTrue(body.contains("Choose which screen should open when the app starts")
                        || body.contains("ACTIVITY_REQUIRED"),
                "plain activity-required message missing");
        Assert.assertFalse(body.contains("serialFingerprint"));
    }
}
