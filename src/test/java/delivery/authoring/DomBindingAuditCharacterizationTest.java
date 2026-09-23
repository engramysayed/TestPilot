package delivery.authoring;

import org.jsoup.Jsoup;
import org.testng.Assert;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;
import java.util.List;

/** Safe-behavior regressions for the DOM/binding audit. */
public class DomBindingAuditCharacterizationTest {
    @Test public void duplicateUnidentifiedButtonsCannotCollapseIntoOneUsableSelector() {
        var candidates = DomCandidateExtractor.extract("<button>Delete</button><button>Delete</button>");
        Assert.assertTrue(candidates.isEmpty(), candidates.toString());
    }
    @Test public void submitInputIsAButtonButTextInputNamedSubmitIsNot() {
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK_LOGIN, "Click Submit");
        var submit = DomCandidateExtractor.extract("<input id='go' type='submit' value='Submit'>").getFirst();
        Assert.assertTrue(StepIntentBinder.actionCompatible(intent, submit));
        var text = DomCandidateExtractor.extract("<input id='submit' type='text'>").getFirst();
        Assert.assertFalse(StepIntentBinder.actionCompatible(intent, text));
    }
    @Test public void hiddenDuplicatesDoNotMakeAHookUnique() {
        var candidates = DomCandidateExtractor.extract(HtmlSlimmer.slim(
                "<button hidden data-testid='save'>Save</button><button id='real-save' data-testid='save'>Save</button>", 80000));
        Assert.assertTrue(candidates.stream().anyMatch(c -> c.strategy().equals("id") && c.value().equals("real-save")));
        Assert.assertFalse(candidates.stream().anyMatch(c -> c.strategy().equals("data-testid")));
    }
    @Test public void explicitReeditMayUsePreviouslyFilledField() {
        Assert.assertFalse(StepIntentBinder.spendsMustAvoidPriorFills(new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD, "Change the email again")));
    }
    @Test public void referencedAccessibleNameTakesPrecedence() {
        var doc = Jsoup.parse("<span id='name'>Checkout</span><button aria-label='Old name' aria-labelledby='name'></button>");
        Assert.assertEquals(AccessibleName.of(doc.selectFirst("button")), "Checkout");
    }
    @Test public void repeatedHooksKeepDistinctControlsUsingUniqueIds() {
        var cs = DomCandidateExtractor.extract("<button id='first' data-testid='remove'>Remove apple</button>"
                + "<button id='second' data-testid='remove'>Remove orange</button>");
        Assert.assertEquals(cs.size(), 2);
        Assert.assertEquals(cs.get(0).value(), "first");
        Assert.assertEquals(cs.get(1).value(), "second");
    }

    @Test public void preferredHookCssPassesValidator() {
        var c = DomCandidateExtractor.extract("<button data-cy='checkout'>Checkout</button>",
                List.of("data-cy")).get(0);
        Assert.assertEquals(c.value(), "button[data-cy='checkout']");
        Assert.assertTrue(new LocatorValidator().validate(
                new LocatorCandidate(c.strategy(), c.value(), c.tag(), c.label())).valid());
    }

    @Test public void hiddenCheckboxIsExcludedFromOrdinalPass() {
        var cs = DomCandidateExtractor.extract("<div hidden><input type='checkbox'>Secret choice</div>");
        Assert.assertTrue(cs.isEmpty());
    }

    @Test public void slimHtmlKeepsEachControlOnce() {
        String html = "<div id='wrap'><button id='save'>Save</button></div><p>" + "x".repeat(600) + "</p>";
        String slim = HtmlSlimmer.slim(html, 180);
        Assert.assertEquals(Jsoup.parse(slim).select("#save").size(), 1);
        Assert.assertTrue(DomCandidateExtractor.extract(slim).stream()
                .anyMatch(c -> c.strategy().equals("id") && c.value().equals("save")));
    }

    @Test public void hiddenRemovalPreservesSourceOrdinal() {
        String html = "<div hidden><input type='checkbox'></div><input type='checkbox'>Accept terms";
        var cs = DomCandidateExtractor.extract(HtmlSlimmer.slim(html, 80000));
        Assert.assertTrue(cs.stream().anyMatch(c -> c.value().equals("(//input)[2]")));
        Assert.assertTrue(Jsoup.parse(html).select("input[type=checkbox]").first().parent().hasAttr("hidden"));
    }

    @Test public void caseSensitiveIdsRemainDifferentControls() {
        Assert.assertFalse(StepIntentBinder.describesSameControl(
                new DomCandidate("a", "id", "Save", "button", "Save"),
                new DomCandidate("b", "id", "save", "button", "Save")));
    }

    @Test public void stableIdPreservesComboboxRole() {
        var c = DomCandidateExtractor.extract("<div id='country' role='combobox' aria-label='Country'></div>").get(0);
        Assert.assertEquals(c.tag(), "combobox");
        Assert.assertEquals(StepIntentBinder.resolveFieldAction(c, "Fill Country with France"), "select");
    }

    @Test public void textXpathWithApostrophePassesValidator() {
        var c = DomCandidateExtractor.extract("<button>Save customer's address</button>").stream()
                .filter(x -> x.strategy().equals("xpath")).findFirst().orElseThrow();
        Assert.assertTrue(new LocatorValidator().validate(
                new LocatorCandidate(c.strategy(), c.value(), c.tag(), c.label())).valid());
    }

    @Test public void actionableControlsComeBeforeDecorativeIdsAtCap() {
        String html = "";
        for (int i = 0; i < 121; i++) html += "<div id='decoration-" + i + "'>Layout</div>";
        html += "<button id='checkout'>Checkout</button>";
        var cs = DomCandidateExtractor.extract(html);
        Assert.assertEquals(cs.size(), 120);
        Assert.assertTrue(cs.stream().anyMatch(c -> c.value().equals("checkout")));
    }

    @Test public void namedActionRejectsAmbiguousIdenticalActions() {
        var cs = List.of(new DomCandidate("a", "id", "add-red-backpack-one", "button", "Add Red Backpack"),
                new DomCandidate("b", "id", "add-red-backpack-two", "button", "Add Red Backpack"));
        var bound = StepIntentBinder.bindSingle(new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Add Red Backpack"), "audit", cs, List.of());
        Assert.assertFalse(bound.ok(), bound.toString());
        Assert.assertTrue(bound.rejectReason().startsWith("AMBIGUOUS:"));
    }
}
