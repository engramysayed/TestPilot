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
        "spring.datasource.url=jdbc:h2:mem:generatemvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-generatemvc",
        "delivery.work-dir=./target/test-delivery-work-generatemvc"
})
@AutoConfigureMockMvc
public class GenerateMvcTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";
    private static final String PROMPT_URL = "/prompts/keel-tc-generate-from-stories-to-json.txt";

    @Autowired
    private MockMvc mockMvc;

    private String fetchGenerateBody() throws Exception {
        return mockMvc.perform(get("/generate").with(httpBasic(AUTH_USER, AUTH_PASS)))
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
    public void generate_rendersForAuthenticatedUser() throws Exception {
        fetchGenerateBody();
    }

    @Test
    public void generate_hasOllamaWorkflowControls() throws Exception {
        String body = fetchGenerateBody();
        String main = mainShell(body);

        Assert.assertTrue(main.contains("id=\"stories-input\""), "stories textarea missing");
        Assert.assertTrue(main.contains("id=\"generate-btn\""), "generate button missing");
        Assert.assertFalse(main.contains("Generate now (wait here)"), "blocking sync generate removed");
        Assert.assertFalse(main.contains("id=\"generate-async-btn\""), "duplicate async button removed");
        Assert.assertTrue(main.contains(">Generate<") || main.contains("Generate</button>"), "Generate CTA missing");
        Assert.assertTrue(body.contains("/generate-async"), "Generate must use background async API");
        Assert.assertFalse(body.contains("/generate-tcs"), "page must not call blocking generate-tcs");
        Assert.assertTrue(main.contains("All in one"), "pipeline CTA missing");
        Assert.assertTrue(main.contains("btn-all-in-one"), "All in one button style missing");
        Assert.assertTrue(main.contains("second coverage pass"), "All in one tooltip/hint missing");
        Assert.assertTrue(main.contains("id=\"pipeline-status\""), "pipeline status panel missing");
        Assert.assertTrue(main.contains("keelpath-help"), "KeelPath help missing");
        Assert.assertTrue(main.contains("id=\"download-csv-btn\""), "download CSV button missing");
        Assert.assertTrue(main.contains("id=\"keel-filter\""), "KeelPath filter missing");
        Assert.assertTrue(body.contains("keel-path-select"), "editable KeelPath select missing");
        Assert.assertTrue(body.contains("generated-workbook/rows"), "must PUT keel path updates");
        Assert.assertTrue(body.contains("function previewCounts("), "must prefer keelPathCounts for chips");
        Assert.assertTrue(body.contains("lastResult.csv = body.csv"), "must sync csv after KeelPath edit");
        Assert.assertTrue(main.contains("id=\"generate-copy-btn\""), "copy button missing");
        Assert.assertTrue(main.contains("id=\"generate-model-select\""), "generate model select missing");
        Assert.assertTrue(main.contains("id=\"bulk-queue-btn\""), "bulk queue button missing");
        Assert.assertTrue(main.contains("keel-user-stories-bulk-template.csv"), "bulk template link missing");
    }

    @Test
    public void generate_wiresModelSelectIntoGenerateRequest() throws Exception {
        String body = fetchGenerateBody();
        Assert.assertTrue(body.contains("id=\"generate-model-select\""), "model select missing");
        Assert.assertTrue(body.contains("/api/generate/models"), "must load model list API");
        Assert.assertTrue(body.contains("function selectedGenerateModel("), "selectedGenerateModel helper missing");
        Assert.assertTrue(body.contains("model: selectedGenerateModel()"),
                "generate must send selected model in options");
        Assert.assertTrue(body.contains("form.append('model', model)"),
                "bulk generate must send selected model");
        Assert.assertTrue(body.contains("id=\"generate-loading\""), "generate loading panel missing");
    }

    @Test
    public void generate_hasModelCompareControls() throws Exception {
        String body = fetchGenerateBody();
        Assert.assertTrue(body.contains("id=\"compare-btn\""), "compare button missing");
        Assert.assertTrue(body.contains("id=\"compare-model-b-select\""), "compare model B select missing");
        Assert.assertTrue(body.contains("/generate/compare-async"), "compare must use background async API");
        Assert.assertFalse(body.contains("/generate/compare'"), "page must not call blocking compare API");
        Assert.assertTrue(body.contains("/generate/save"), "must POST save for chosen compare side");
        Assert.assertTrue(body.contains("id=\"compare-results\""), "compare results panel missing");
        Assert.assertTrue(body.contains("function runCompare("), "runCompare helper missing");
        Assert.assertTrue(body.contains("function loadCompareFromJob("), "loadCompareFromJob helper missing");
        Assert.assertTrue(body.contains("function saveCompareSide("), "saveCompareSide helper missing");
        Assert.assertTrue(body.contains("compareResults.hidden = true"), "save must close compare panel");
        Assert.assertTrue(body.contains("class=\"compare-summary\""), "compare count summary styling missing");
        Assert.assertTrue(body.contains("function renderComparePreview("), "compare short preview helper missing");
        Assert.assertTrue(body.contains("id=\"compare-preview-a\""), "compare preview A container missing");
        Assert.assertTrue(body.contains("renderComparePreview(comparePreviewA"), "showCompareResult must render preview A");
        Assert.assertTrue(body.contains("compare-tc-card"), "compare cards should show readable TC details");
        Assert.assertTrue(body.contains("compare-tc-body"), "compare cards should show steps/expected text");
    }

    @Test
    public void generate_definesSetGenerateStatus() throws Exception {
        String body = fetchGenerateBody();
        Assert.assertTrue(body.contains("function setGenerateStatus("),
                "page must define setGenerateStatus for Ollama feedback");
    }

    @Test
    public void generate_noComingSoonAsPrimaryContent() throws Exception {
        String main = mainShell(fetchGenerateBody());

        Assert.assertFalse(main.contains("coming-soon-panel"), "generate should not use coming-soon panel");
        Assert.assertFalse(main.contains("coming-soon-badge"), "generate should not show coming-soon badge");
        Assert.assertFalse(main.contains(">Coming soon<"), "generate primary content must not say Coming soon");
    }

    @Test
    public void generate_referencesPromptFetchUrl() throws Exception {
        String body = fetchGenerateBody();

        Assert.assertTrue(body.contains("const PROMPT_URL = '" + PROMPT_URL + "'"),
                "page must fetch generate prompt from " + PROMPT_URL);
        Assert.assertTrue(body.contains("fetch(PROMPT_URL"), "page must load prompt via fetch");
    }

    @Test
    public void generate_hasPasteImportControls() throws Exception {
        String body = fetchGenerateBody();
        String main = mainShell(body);

        Assert.assertTrue(main.contains("id=\"import-raw\""), "import textarea missing");
        Assert.assertTrue(main.contains("id=\"import-btn\""), "import button missing");
        Assert.assertTrue(body.contains("/generate/import"), "must POST to generate/import API");
        Assert.assertTrue(body.contains("Import into project"), "import button label missing");
        Assert.assertTrue(body.contains("function runImport("), "runImport helper missing");
        Assert.assertTrue(body.contains("format: 'auto'"), "import must send format auto");
        Assert.assertTrue(body.contains("showResult(body)"), "import success must reuse showResult");
        Assert.assertTrue(body.contains("Workbook saved — use Automate/Execute with generated workbook"),
                "import success status message missing");
        Assert.assertTrue(main.contains("Paste from external AI"), "visible import section heading missing");
        Assert.assertTrue(main.contains("Import into project</strong> above"),
                "copy-prompt steps must reference Import into project");
        Assert.assertFalse(main.contains("into Excel, then upload via Automate"),
                "copy-prompt must not be Excel-only path");
    }

    @Test
    public void generate_hasAuthoringReviewControlsAndWiring() throws Exception {
        String body = fetchGenerateBody();
        String main = mainShell(body);

        Assert.assertTrue(main.contains("id=\"authoring-review-banner\""), "review banner missing");
        Assert.assertTrue(main.contains("Review with AI"), "Review with AI copy missing");
        Assert.assertTrue(main.contains("id=\"generate-authoring-review\""), "authoring review panel missing");
        Assert.assertTrue(main.contains("id=\"review-requirements-notes\""), "requirements notes missing");
        Assert.assertTrue(main.contains("id=\"authoring-review-provider\""), "provider dropdown missing");
        Assert.assertTrue(main.contains("id=\"authoring-review-model\""), "Ollama review model select missing");
        Assert.assertTrue(main.contains("id=\"authoring-review-model-row\""), "Ollama model row missing");
        Assert.assertTrue(main.contains("id=\"authoring-review-start\""), "start review button missing");
        Assert.assertTrue(body.contains("function syncAuthoringReviewProviderUi("),
                "provider dropdown must reveal the Ollama model picker");
        Assert.assertTrue(body.contains("Ollama model must be specified")
                        || body.contains("Choose an Ollama model"),
                "start review must require an Ollama model");
        Assert.assertTrue(main.contains("id=\"authoring-review-accept\""), "accept proposal button missing");
        Assert.assertTrue(main.contains("id=\"authoring-review-discard\""), "discard proposal button missing");
        Assert.assertTrue(main.contains("id=\"authoring-review-tc-count\""), "proposal TC count missing");
        Assert.assertTrue(main.contains("id=\"authoring-review-cases\""), "proposed TCs must render as a readable list");
        Assert.assertTrue(body.contains("keel.authoringReview.provider"), "review provider preference missing");
        Assert.assertTrue(body.contains("/generate/authoring-review"), "authoring review API wiring missing");
        Assert.assertTrue(body.contains("function setAuthoringReviewUnlocked("),
                "review must lock until generate/import");
        Assert.assertTrue(body.contains("authoringReviewStart.disabled = !unlocked"),
                "Start review must stay disabled until a workbook exists");
        Assert.assertTrue(body.contains("authoringReviewBanner.hidden = true"),
                "successful showResult/project change must clear review banner");
        Assert.assertTrue(body.contains("if (!projectId || !hasGeneratedWorkbook)"),
                "start review must use workbook availability");
        Assert.assertTrue(body.contains("Generate or import a workbook first"),
                "banner/start must refuse review before generate");
        Assert.assertTrue(body.contains("authoringReviewBanner.hidden = true"),
                "review banner clear behavior missing");
        Assert.assertTrue(body.contains("format: 'csv'"), "accept must import proposed CSV");
        Assert.assertTrue(body.contains("function mergeAuthoringReviewCoverageNotes("),
                "accept must merge authoring review coverage notes");
        Assert.assertTrue(body.contains("### Authoring review"),
                "merged review coverage heading missing");
        Assert.assertTrue(body.contains("coverageNotesSaved = await saveCoverageNotes()"),
                "accept must detect coverage-note save failures");
        Assert.assertTrue(body.contains("The workbook was accepted, but coverage notes could not be saved"),
                "partial accept failure must be visible");
        Assert.assertTrue(body.contains("previewOk"), "accept must honor preview quality status");
        Assert.assertTrue(body.contains("gateErrors"), "review must display quality gate errors");
    }

    @Test
    public void generate_hasEditableTcPreviewModal() throws Exception {
        String body = fetchGenerateBody();
        String main = mainShell(body);

        Assert.assertTrue(body.contains("id=\"tc-edit-modal\""), "TC edit modal missing");
        Assert.assertTrue(body.contains("id=\"tc-edit-save\""), "TC edit save button missing");
        Assert.assertTrue(body.contains("id=\"tc-edit-cancel\""), "TC edit cancel button missing");
        Assert.assertTrue(body.contains("id=\"tc-edit-steps\""), "TC edit steps field missing");
        Assert.assertTrue(body.contains("id=\"tc-edit-testdata\""), "TC edit testData field missing");
        Assert.assertTrue(body.contains("id=\"coverage-notes\""), "coverage notes element missing");
        Assert.assertTrue(body.contains("function openTcEditor("), "openTcEditor helper missing");
        Assert.assertTrue(body.contains("function saveTcEditor("), "saveTcEditor helper missing");
        Assert.assertTrue(body.contains("generated-workbook/cases/"), "must PUT case field updates");
        Assert.assertTrue(body.contains("preview-row-clickable"), "preview rows must be clickable");
        Assert.assertTrue(body.contains("stopPropagation"), "KeelPath select must stop row click");
        Assert.assertTrue(main.contains("Coverage notes"), "coverage notes panel heading missing");
    }
}
