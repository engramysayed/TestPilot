package delivery.portal.api;

import delivery.portal.PortalApplication;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** When delivery.mobile.enabled=false, mobile REST returns 404. */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-m107-off;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=update",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-mobile-m107-off",
        "delivery.work-dir=./target/test-delivery-work-mobile-m107-off",
        "delivery.mobile.enabled=false",
        "delivery.mobile.live-tests=false"
})
@AutoConfigureMockMvc
public class MobileAppDeviceFlagOffApiTest extends AbstractTestNGSpringContextTests {

    @Autowired MockMvc mockMvc;

    @Test
    public void mobileApiReturns404WhenFlagOff() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flag Off\",\"baseUrl\":\"https://ex.test/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
        mockMvc.perform(get("/api/projects/" + projectId + "/mobile/app")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/" + projectId + "/mobile/devices")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/projects/" + projectId + "/mobile/apps")
                        .file(new MockMultipartFile(
                                "file", "x.apk", "application/octet-stream", "x".getBytes()))
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void androidCreateRejectedWhenMobileDisabled() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Mobile\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isBadRequest());
    }
}
