package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:executemvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-executemvc",
        "delivery.work-dir=./target/test-delivery-work-executemvc"
})
@AutoConfigureMockMvc
public class ExecuteMvcTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;

    private String fetchExecuteBody() throws Exception {
        return mockMvc.perform(get("/execute").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String mainShell(String body) {
        int mainStart = body.indexOf("<main class=\"shell\">");
        Assert.assertTrue(mainStart >= 0, "main shell missing");
        int mainEnd = body.indexOf("</main>", mainStart);
        Assert.assertTrue(mainEnd > mainStart, "main shell unclosed");
        return body.substring(mainStart, mainEnd);
    }

    @Test
    public void execute_rendersForAuthenticatedUser() throws Exception {
        fetchExecuteBody();
    }

    @Test
    public void execute_noComingSoonPanel() throws Exception {
        String main = mainShell(fetchExecuteBody());

        Assert.assertFalse(main.contains("coming-soon-panel"), "execute should not use coming-soon panel");
        Assert.assertFalse(main.contains("coming-soon-badge"), "execute should not show coming-soon badge");
        Assert.assertFalse(main.contains(">Coming soon<"), "execute primary content must not say Coming soon");
    }

    @Test
    public void execute_hasProjectSelectAndForm() throws Exception {
        String main = mainShell(fetchExecuteBody());

        Assert.assertTrue(main.contains("id=\"project-select\""), "project picker missing");
        Assert.assertTrue(main.contains("id=\"execute-form\""), "execute form missing");
    }

    @Test
    public void execute_hasDesignCompareAndBugReportUi() throws Exception {
        String body = fetchExecuteBody();
        String main = mainShell(body);

        Assert.assertTrue(main.contains(">Design</th>"), "Design column header missing");
        Assert.assertTrue(main.contains("id=\"bug-report-actions\""), "bug report actions missing");
        Assert.assertTrue(main.contains("bug-report"), "bug report export link missing");
        Assert.assertTrue(main.contains("id=\"design-ref-form\""), "design reference upload form missing");
        Assert.assertTrue(main.contains("/design-references"), "design references API hint missing");
        Assert.assertTrue(body.contains("designCompareStatus"), "designCompareStatus handling missing");
        Assert.assertTrue(body.contains("design-compare-panel"), "design compare detail panel missing");
        Assert.assertTrue(body.contains("bug-report.csv"), "bug report CSV export missing");
    }
}
