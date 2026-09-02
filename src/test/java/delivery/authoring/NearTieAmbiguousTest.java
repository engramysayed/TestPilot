package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/** Near-tie ±1 honesty gate and AMBIGUOUS fallback when LLM cannot pick. */
public class NearTieAmbiguousTest {

    private static final List<DomCandidate> TWIN_SETTINGS_BUTTONS = List.of(
            new DomCandidate("c1", "id", "settings-primary", "button", "Settings"),
            new DomCandidate("c2", "id", "settings-advanced", "button", "Settings")
    );

    private static final String TWIN_SETTINGS_HTML = """
            <html><body>
              <button id="settings-primary">Settings</button>
              <button id="settings-advanced">Settings</button>
            </body></html>
            """;

    private static List<DomCandidate> twinSettingsCandidates() {
        return DomCandidateExtractor.extract(TWIN_SETTINGS_HTML);
    }

    @Test
    public void nearTieDifferentControls_returnsAmbiguous() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Settings");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                intent, "TC", TWIN_SETTINGS_BUTTONS, List.of());
        Assert.assertFalse(r.ok(), "near-tie must not silently pick a winner");
        Assert.assertTrue(r.rejectReason().startsWith("AMBIGUOUS:CLICK:"),
                r.rejectReason());
        Assert.assertTrue(r.rejectReason().contains("c1") && r.rejectReason().contains("c2"),
                r.rejectReason());
        Assert.assertTrue(r.steps().isEmpty());
    }

    @Test
    public void clearWinner_notAmbiguous() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "id", "save-changes", "button", "Save Changes"),
                new DomCandidate("c2", "id", "cancel", "button", "Cancel")
        );
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Save Changes");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).locatorValue(), "save-changes");
    }

    @Test
    public void fingerprintTwins_notAmbiguous() {
        List<DomCandidate> twins = List.of(
                new DomCandidate("c1", "css", "#save-btn", "button", "Save"),
                new DomCandidate("c2", "xpath", "//button[@id='save-btn']", "button", "Save")
        );
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", twins, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertFalse(r.rejectReason().startsWith("AMBIGUOUS:"));
    }

    @Test
    public void preferOnTie_resolvesWhenIdProvided() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Settings");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                intent, "TC", TWIN_SETTINGS_BUTTONS, List.of("c2"));
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).locatorValue(), "settings-advanced");
    }

    @Test
    public void extractedTwinSettingsButtons_areAmbiguous() {
        List<DomCandidate> candidates = twinSettingsCandidates();
        Assert.assertTrue(candidates.size() >= 2, candidates.toString());
        ManualTestCase tc = new ManualTestCase(
                "TC", "Settings", "", "1. Click Settings", "Settings open", "P1", "");
        StepIntentBinder.BindResult bind = StepIntentBinder.bind(tc, candidates);
        Assert.assertFalse(bind.ok(), bind.rejectReason());
        Assert.assertTrue(bind.rejectReason().startsWith("AMBIGUOUS:"), bind.rejectReason());
    }

    @Test
    public void ambiguousLlmFailure_doesNotBindFirstId() throws Exception {
        ManualTestCase tc = new ManualTestCase(
                "TC", "Settings", "", "1. Click Settings", "Settings open", "P1", "");
        StepIntentBinder.BindResult bind = StepIntentBinder.bind(tc, TWIN_SETTINGS_BUTTONS);
        Assert.assertFalse(bind.ok());
        Assert.assertTrue(bind.rejectReason().startsWith("AMBIGUOUS:"));

        LocalLlmClient failing = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                return "{\"candidateId\":\"\"}";
            }

            @Override
            public String completeJson(String system, String user, byte[] png) {
                return completeJson(system, user);
            }
        };
        AuthoringService service = new AuthoringService(failing, new LocatorValidator());
        List<ProvenStep> steps = service.author(tc, TWIN_SETTINGS_HTML, null);

        Assert.assertEquals(steps.size(), 1);
        Assert.assertFalse(steps.get(0).validated(), "must not invent a bound step");
        Assert.assertTrue(steps.get(0).rationale().startsWith("AMBIGUOUS:"),
                steps.get(0).rationale());
        Assert.assertTrue(steps.get(0).locatorValue().isBlank(),
                "must leave unbound, not bindPreferring first id");
    }

    @Test
    public void ambiguousLlmPicksValidId_stillBinds() throws Exception {
        ManualTestCase tc = new ManualTestCase(
                "TC", "Settings", "", "1. Click Settings", "Settings open", "P1", "");
        List<DomCandidate> candidates = twinSettingsCandidates();
        String altId = candidates.stream()
                .filter(c -> "settings-advanced".equals(c.value()))
                .map(DomCandidate::id)
                .findFirst()
                .orElseThrow();
        LocalLlmClient picker = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                return "{\"candidateId\":\"" + altId + "\"}";
            }

            @Override
            public String completeJson(String system, String user, byte[] png) {
                return completeJson(system, user);
            }
        };
        AuthoringService service = new AuthoringService(picker, new LocatorValidator());
        List<ProvenStep> steps = service.author(tc, TWIN_SETTINGS_HTML, null);

        Assert.assertEquals(steps.size(), 1);
        Assert.assertTrue(steps.get(0).validated(), steps.get(0).rationale());
        Assert.assertEquals(steps.get(0).locatorValue(), "settings-advanced");
    }
}
