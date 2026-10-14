package delivery.authoring;

import parsingLayer.HtmlSlimmer;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class SidebarSnapshotTest {
    @Test public void preferredHookWinsOverMenuIdAndTextLocator() {
        String slim=HtmlSlimmer.slim("<aside><li id='support' data-axis-test-id='support_Menu'><span>Support</span></li></aside>",2000);
        var candidates=DomCandidateExtractor.extract(slim,java.util.List.of("data-axis-test-id"));
        assertTrue(candidates.stream().anyMatch(c -> c.label().equals("Support")
                && c.strategy().equals("css") && c.value().equals("li[data-axis-test-id='support_Menu']")),candidates.toString());
        assertFalse(candidates.stream().anyMatch(c -> c.strategy().equals("id") && c.value().equals("support")),candidates.toString());
    }
    @Test public void navigationWordingDoesNotBecomePartOfTheControlName() {
        var candidates=DomCandidateExtractor.extract(HtmlSlimmer.slim(
                "<aside><li id='support'>Support</li><li id='bulk'>Bulk Charge Back</li></aside>",2000));
        for (String text : java.util.List.of("Clicks \"Support\" in the sidebar.",
                "Clicks \"Bulk Charge Back\" in the sub-menu.")) {
            var bound=StepIntentBinder.bindSingle(new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK,text),
                    "TC",candidates,candidates.stream().map(DomCandidate::id).toList());
            assertTrue(bound.ok(),bound.rejectReason());
            assertEquals(bound.steps().get(0).locatorValue(),text.contains("Support") ? "support" : "bulk");
        }
    }
    @Test public void repeatedMenuItemsCannotCrowdOutUniqueTarget() {
        String slim=HtmlSlimmer.slim("<aside><ul><li>Dashboard</li>"
                + "<li>Other item</li>".repeat(20)
                + "<li id='support'>Support</li></ul></aside>",1200);
        assertTrue(DomCandidateExtractor.extract(slim).stream().anyMatch(c -> c.label().equals("Support")), slim);
    }
    @Test public void identifiedMenuItemUsesItsOwnNestedText() {
        String slim=HtmlSlimmer.slim("<aside><ul><li><span>Dashboard</span></li>"
                + "<li id='support'><span>Support</span></li></ul></aside>",1200);
        assertEquals(AccessibleName.of(org.jsoup.Jsoup.parse(slim).getElementById("support")), "Support");
        assertTrue(DomCandidateExtractor.extract(slim).stream().anyMatch(c -> c.label().equals("Support")),
                slim + " CANDIDATES=" + DomCandidateExtractor.extract(slim));
    }
    @Test public void largeContentDoesNotCrowdOutFrameworkMenuItems() {
        String html="<main>" + "<button>Unrelated content</button>".repeat(500)
                + "</main><aside><ul><li><span aria-expanded='false'>Support</span></li>"
                + "<li><span>Bulk Charge Back</span></li></ul></aside>";
        String slim=HtmlSlimmer.slim(html, 1200);
        assertTrue(slim.contains("Support"), slim);
        assertTrue(slim.contains("Bulk Charge Back"), slim);
        assertTrue(DomCandidateExtractor.extract(slim).stream().anyMatch(c -> c.label().equals("Bulk Charge Back")), slim + " CANDIDATES=" + DomCandidateExtractor.extract(slim));
    }
    @Test public void hiddenResponsiveCopyStaysExcluded() {
        String slim=HtmlSlimmer.slim("<aside hidden><li>Hidden Support</li></aside><aside><li>Support</li></aside>", 2000);
        assertFalse(slim.contains("Hidden Support"));
        assertTrue(DomCandidateExtractor.extract(slim).stream().anyMatch(c -> c.label().equals("Support")), slim + " CANDIDATES=" + DomCandidateExtractor.extract(slim));
    }
    // ---- A1 additions ----
    @Test public void substringTwinDoesNotMarkNavItemAmbiguous() {
        String slim=HtmlSlimmer.slim("<aside hidden><li>Hidden Support</li></aside><aside><li>Support</li></aside>", 2000);
        assertFalse(slim.contains("data-keel-source-text-ambiguous"), slim);
        assertTrue(DomCandidateExtractor.extract(slim).stream().anyMatch(c -> c.label().equals("Support")
                && c.value().equals("//li[normalize-space(.)='Support']")), slim + " CANDIDATES=" + DomCandidateExtractor.extract(slim));
    }
    @Test public void exactHiddenTwinUsesSourceOrdinalNotTextXpath() {
        String slim=HtmlSlimmer.slim("<aside hidden><li>Support</li></aside><aside><li>Support</li></aside>", 2000);
        var cands=DomCandidateExtractor.extract(slim);
        assertTrue(cands.stream().anyMatch(c -> c.label().equals("Support") && c.value().equals("(//li)[2]")),
                slim + " CANDIDATES=" + cands);
        assertFalse(cands.stream().anyMatch(c -> c.value().equals("//li[normalize-space(.)='Support']")), cands.toString());
    }
}
