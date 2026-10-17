package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;

import java.util.List;

/** Confirm/expected lines are classified before CandidateScorer. */
public class OutcomeKindTest {

    @DataProvider(name = "classifyTable")
    public Object[][] classifyTable() {
        return new Object[][] {
                {"Confirm Username is accepted.", OutcomeKind.Kind.FIELD_ACCEPTED},
                {"Confirm the OTP field is visible", OutcomeKind.Kind.FIELD_VISIBLE},
                {"Login page is shown", OutcomeKind.Kind.PAGE_SHOWN},
                {"Submit is clicked", OutcomeKind.Kind.ACTION_ECHO},
                {"Verify OTP button is clicked", OutcomeKind.Kind.ACTION_ECHO},
                {"Dashboard is shown with 'Welcome!'", OutcomeKind.Kind.QUOTED_TEXT},
                {"The Support section expands and sub-menu items are shown.", OutcomeKind.Kind.EXPANDED},
                {"Confirm the Logout button is visible", OutcomeKind.Kind.CONTROL_VISIBLE},
                {"Confirm OTP is visible", OutcomeKind.Kind.FIELD_VISIBLE},
                {"Confirm Username is visible", OutcomeKind.Kind.FIELD_VISIBLE},
                {"Confirm Logout is visible", OutcomeKind.Kind.CONTROL_VISIBLE},
                {"Confirm Submit is enabled", OutcomeKind.Kind.ENABLED},
                {"Confirm Submit is disabled", OutcomeKind.Kind.DISABLED},
                {"error toast is shown", OutcomeKind.Kind.TOAST},
                {"success message appears", OutcomeKind.Kind.TOAST},
                {"checkbox 1 is unchecked", OutcomeKind.Kind.UNCHECKED},
                {"Confirm Option 2 is the selected value", OutcomeKind.Kind.SELECTED},
        };
    }

    @Test(dataProvider = "classifyTable")
    public void classify_table(String text, OutcomeKind.Kind expected) {
        OutcomeKind.Outcome o = OutcomeKind.classify(text);
        Assert.assertEquals(o.kind(), expected, text + " -> " + o);
        if (expected == OutcomeKind.Kind.QUOTED_TEXT) {
            Assert.assertEquals(o.expected(), "Welcome!");
            Assert.assertEquals(o.assertionType(), "textContains");
        }
        if (expected == OutcomeKind.Kind.PAGE_SHOWN) {
            Assert.assertEquals(o.assertionType(), "urlContains");
            Assert.assertEquals(o.expected(), "login");
        }
        if (expected == OutcomeKind.Kind.ACTION_ECHO) {
            Assert.assertEquals(o.assertionType(), "visible");
        }
        if (expected == OutcomeKind.Kind.FIELD_ACCEPTED) {
            Assert.assertTrue(o.namedControl().toLowerCase().contains("username"), o.namedControl());
            Assert.assertEquals(o.assertionType(), "visible");
        }
        if (expected == OutcomeKind.Kind.SELECTED) {
            Assert.assertEquals(o.assertionType(), "selected");
        }
        if (expected == OutcomeKind.Kind.UNCHECKED) {
            Assert.assertEquals(o.assertionType(), "unchecked");
        }
        if (expected == OutcomeKind.Kind.ENABLED) {
            Assert.assertEquals(o.assertionType(), "enabled");
        }
        if (expected == OutcomeKind.Kind.DISABLED) {
            Assert.assertEquals(o.assertionType(), "disabled");
        }
        if (expected == OutcomeKind.Kind.TOAST) {
            Assert.assertEquals(o.assertionType(), "textContains");
            Assert.assertFalse(o.expected().isBlank(), text + " toast token");
        }
        if (expected == OutcomeKind.Kind.FIELD_VISIBLE || expected == OutcomeKind.Kind.CONTROL_VISIBLE) {
            Assert.assertFalse(o.namedControl().isBlank(), text + " namedControl");
        }
    }

    @Test
    public void quotesWinOverPageShown() {
        OutcomeKind.Outcome o = OutcomeKind.classify("Dashboard is shown with 'Welcome!'");
        Assert.assertEquals(o.kind(), OutcomeKind.Kind.QUOTED_TEXT);
        Assert.assertEquals(o.expected(), "Welcome!");
    }

    @Test
    public void loginPageShown_bindsUrlContains_notAmbiguous() {
        String html = """
                <html><body>
                  <h1>Login</h1>
                  <button id="submit">Submit</button>
                  <button id="cancel">Cancel</button>
                  <a href="/help">Help</a>
                </body></html>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(HtmlSlimmer.slim(html, 80000));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Login page is shown");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertFalse(r.rejectReason().startsWith("AMBIGUOUS:"), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "urlContains");
        Assert.assertEquals(r.steps().get(0).assertionExpected(), "login");
        Assert.assertEquals(r.steps().get(0).action(), "assert");
    }

    @Test
    public void submitIsClicked_bindsEcho_notAmbiguous() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "id", "submit", "button", "Submit"),
                new DomCandidate("c2", "id", "cancel", "button", "Cancel")
        );
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Submit is clicked");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertFalse(r.rejectReason().startsWith("AMBIGUOUS:"), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "visible");
        Assert.assertTrue(r.steps().get(0).validated());
        Assert.assertTrue(r.steps().get(0).rationale().contains("echo"), r.steps().get(0).rationale());
    }

    @Test
    public void enabledMapsToEnabledAssertType_notVisible() {
        OutcomeKind.Outcome o = OutcomeKind.classify("Confirm the Submit button is enabled");
        Assert.assertEquals(o.kind(), OutcomeKind.Kind.ENABLED);
        Assert.assertEquals(o.assertionType(), "enabled");
        Assert.assertFalse(o.namedControl().isBlank(), o.namedControl());
    }

    @Test
    public void namelessUnknown_hasNoTextContainsToken() {
        OutcomeKind.Outcome o = OutcomeKind.classify("Confirm it is shown");
        Assert.assertEquals(o.kind(), OutcomeKind.Kind.UNKNOWN);
        Assert.assertTrue(o.expected().isBlank(), o.expected());
        Assert.assertEquals(o.assertionType(), "visible");
    }

    @Test
    public void distinctiveUnknown_keepsTextContains() {
        OutcomeKind.Outcome o = OutcomeKind.classify("Confirm WelcomeBannerXYZ is shown");
        // NAMED_VISIBLE may catch this as CONTROL_VISIBLE; either way not weak UNKNOWN textContains on stop-word.
        Assert.assertNotEquals(o.kind(), OutcomeKind.Kind.UNKNOWN);
    }

    @Test
    public void unquotedErrorToast_bindsTextContainsError_notAmbiguous() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "id", "submit", "button", "Submit"),
                new DomCandidate("c2", "id", "cancel", "button", "Cancel")
        );
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "error toast is shown");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertFalse(r.rejectReason().startsWith("AMBIGUOUS:"), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "textContains");
        Assert.assertEquals(r.steps().get(0).assertionExpected(), "error");
    }

    @Test
    public void namelessUnknown_bindsSkipEcho_notAmbiguous() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "id", "a", "button", "Alpha"),
                new DomCandidate("c2", "id", "b", "button", "Beta")
        );
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm it is shown");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertFalse(r.rejectReason().startsWith("AMBIGUOUS:"), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).locatorStrategy(), "css");
        Assert.assertEquals(r.steps().get(0).locatorValue(), "html");
        Assert.assertTrue(r.steps().get(0).rationale().contains("unknown-echo"),
                r.steps().get(0).rationale());
    }
}
