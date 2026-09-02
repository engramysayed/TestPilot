package delivery.portal.api;



import delivery.portal.PortalApplication;

import delivery.portal.worker.GenerateBatchWorker;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.http.MediaType;

import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;

import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.MvcResult;

import org.testng.annotations.Test;



import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.doNothing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



@SpringBootTest(classes = PortalApplication.class, properties = {

        "spring.datasource.url=jdbc:h2:mem:generateasyncapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",

        "spring.jpa.hibernate.ddl-auto=create-drop",

        "delivery.admin-email=admin@testpilot.local",

        "delivery.admin-password=ChangeMeAdmin1!",

        "delivery.dry-run=true",

        "delivery.store-root=./target/test-delivery-store-generateasyncapi",

        "delivery.work-dir=./target/test-delivery-work-generateasyncapi"

})

@AutoConfigureMockMvc

public class GenerateAsyncApiTest extends AbstractTestNGSpringContextTests {



    @Autowired

    private MockMvc mockMvc;



    @MockBean

    private GenerateBatchWorker worker;



    @Test

    public void createAsyncJob_enqueuesGenerateBatch() throws Exception {

        doNothing().when(worker).submit(anyString());

        String projectId = createProject();



        mockMvc.perform(post("/api/projects/" + projectId + "/generate-async")

                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))

                        .header("X-Keel-Requested-With", "Keel")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content("""

                                {

                                  "stories": "As a user I want to log in so that I can access my account.",

                                  "options": { "model": "qwen2.5:latest", "reviewPass": false }

                                }

                                """))

                .andExpect(status().isAccepted())

                .andExpect(jsonPath("$.jobId").isNotEmpty())

                .andExpect(jsonPath("$.status").value("QUEUED"))

                .andExpect(jsonPath("$.statusUrl").isNotEmpty());

    }



    @Test

    public void createAsyncJob_rejectsEmptyStories() throws Exception {

        String projectId = createProject();



        mockMvc.perform(post("/api/projects/" + projectId + "/generate-async")

                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))

                        .header("X-Keel-Requested-With", "Keel")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content("{\"stories\": \"  \"}"))

                .andExpect(status().isBadRequest())

                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

    }



    private String createProject() throws Exception {

        MvcResult res = mockMvc.perform(post("/api/projects")

                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))

                        .header("X-Keel-Requested-With", "Keel")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content("{\"name\":\"Async Generate\"}"))

                .andExpect(status().isCreated())

                .andReturn();

        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");

    }

}


