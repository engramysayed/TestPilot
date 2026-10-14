package delivery.portal.api;

import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.GeneratedWorkbookService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B15: upload preview (dry run) + commit by token (ux-patches/BACKEND_NEEDS.md §2). */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:wbpreviewapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-wbpreview",
        "delivery.work-dir=./target/test-delivery-work-wbpreview"
})
@AutoConfigureMockMvc
public class WorkbookUploadPreviewApiTest extends AbstractTestNGSpringContextTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private GeneratedWorkbookService workbooks;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;

    @BeforeMethod
    public void secondCustomer() {
        if (!users.existsByEmailIgnoreCase("customer-wb@testpilot.local")) {
            PortalUser u = new PortalUser();
            u.setEmail("customer-wb@testpilot.local");
            u.setPasswordHash(encoder.encode("CustomerWb1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    private static ManualTestCase tc(String id, String title, String steps, String data) {
        return new ManualTestCase(id, title, "", steps, "1. OK", "P1", "", "", data, "AUTOMATE");
    }

    private static MockMultipartFile xlsx(String field, List<ManualTestCase> cases) throws Exception {
        Path tmp = Files.createTempFile("wbp", ".xlsx");
        ManualTcExcelWriter.write(tmp, cases);
        return new MockMultipartFile(field, "cases.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.readAllBytes(tmp));
    }

    @Test
    public void previewSavesNothingThenCommitSavesOnce() throws Exception {
        String id = createProject();
        workbooks.saveFromCases(id, List.of(
                tc("TC_01", "Login", "1. Open login", ""),
                tc("TC_02", "Search", "1. Search", ""),
                tc("TC_03", "Logout", "1. Logout", "")), "TEST", "unit");
        String headBefore = workbooks.headRevisionId(id).orElse(null);

        MvcResult res = mockMvc.perform(multipart("/api/projects/" + id + "/generated-workbook/preview")
                        .file(xlsx("excel", List.of(
                                tc("TC_01", "Login", "1. Open login", ""),
                                tc("TC_02", "Search", "1. Search\n2. Open result", "q=shoes"),
                                tc("TC_25", "New one", "1. Do it", ""))))
                        .with(admin()).header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added.length()").value(1))
                .andExpect(jsonPath("$.added[0].tcId").value("TC_25"))
                .andExpect(jsonPath("$.updated.length()").value(1))
                .andExpect(jsonPath("$.updated[0].tcId").value("TC_02"))
                .andExpect(jsonPath("$.updated[0].changedFields[0]").value("Steps"))
                .andExpect(jsonPath("$.updated[0].changedFields[1]").value("TestData"))
                .andExpect(jsonPath("$.unchanged").value(1))
                .andExpect(jsonPath("$.problems.length()").value(0))
                .andExpect(jsonPath("$.previewToken").exists())
                .andReturn();
        Assert.assertEquals(workbooks.headRevisionId(id).orElse(null), headBefore, "preview must not save");
        Assert.assertEquals(workbooks.readCases(id).size(), 3);
        String token = new JSONObject(res.getResponse().getContentAsString()).getString("previewToken");

        mockMvc.perform(commit(id, token, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.addedCount").value(1))
                .andExpect(jsonPath("$.replacedCount").value(2))
                // B19: counts echoed from the preview (added / updated / unchanged)
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.unchangedCount").value(1));
        Assert.assertEquals(workbooks.readCases(id).size(), 4);
        Assert.assertNotEquals(workbooks.headRevisionId(id).orElse(null), headBefore);
        // single use
        mockMvc.perform(commit(id, token, admin())).andExpect(status().isNotFound());
    }

    @Test
    public void staleTokenIsRejectedAndQualityGateStillRunsOnCommit() throws Exception {
        String id = createProject();
        workbooks.saveFromCases(id, List.of(tc("TC_01", "Login", "1. Open login", "")), "TEST", "unit");
        String token = previewToken(id, List.of(tc("TC_02", "Two", "1. Go", "")));
        workbooks.saveFromCases(id, List.of(tc("TC_01", "Login v2", "1. Open login", "")), "TEST", "unit");
        mockMvc.perform(commit(id, token, admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PREVIEW_STALE"));

        // a row that fails the gate is reported as a problem; commit is refused by the gate
        MvcResult res = mockMvc.perform(multipart("/api/projects/" + id + "/generated-workbook/preview")
                        .file(xlsx("file", List.of(tc("TC_09", "Bad", "", ""))))
                        .with(admin()).header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems[0].tcId").value("TC_09"))
                .andExpect(jsonPath("$.problems[0].row").value(2))
                .andReturn();
        String bad = new JSONObject(res.getResponse().getContentAsString()).getString("previewToken");
        mockMvc.perform(commit(id, bad, admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("QUALITY_GATE"));
    }

    @Test
    public void crossTenantIs404AndTokenIsBoundToProjectAndUser() throws Exception {
        String id = createProject();
        String other = createProject();
        String token = previewToken(id, List.of(tc("TC_01", "Login", "1. Open login", "")));
        RequestPostProcessor bob = httpBasic("customer-wb@testpilot.local", "CustomerWb1!");
        mockMvc.perform(multipart("/api/projects/" + id + "/generated-workbook/preview")
                        .file(xlsx("file", List.of(tc("TC_01", "x", "1. y", ""))))
                        .with(bob).header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
        mockMvc.perform(commit(id, token, bob)).andExpect(status().isNotFound());
        mockMvc.perform(commit(other, token, admin())).andExpect(status().isNotFound());
        // the token survived the wrong-project attempt and still works for its own project
        mockMvc.perform(commit(id, token, admin())).andExpect(status().isOk());
        mockMvc.perform(commit(id, "", admin())).andExpect(status().isBadRequest());
    }

    private String previewToken(String id, List<ManualTestCase> cases) throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/projects/" + id + "/generated-workbook/preview")
                        .file(xlsx("file", cases)).with(admin()).header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk()).andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("previewToken");
    }

    private static org.springframework.test.web.servlet.RequestBuilder commit(String id, String token, RequestPostProcessor who) {
        return post("/api/projects/" + id + "/generated-workbook/commit").with(who)
                .header("X-Keel-Requested-With", "Keel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"previewToken\":\"" + token + "\"}");
    }

    private static RequestPostProcessor admin() {
        return httpBasic("admin@testpilot.local", "ChangeMeAdmin1!");
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects").with(admin())
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Preview\",\"baseUrl\":\"https://example.test\"}"))
                .andExpect(status().isCreated()).andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
