package parsingLayer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HtmlSlimmerFrameXpathTest {
    private static final String PAGE = "<html><body>"
            + "<button id='a'>Top one</button><button id='b'>Top two</button>"
            + "<iframe id='payment'></iframe>"
            + "</body></html>"
            // appended by PageSnapshot
            + "<div data-testpilot-context=\"iframe\" data-testpilot-frame-path='[\"#payment\"]'>"
            + "<button>Pay</button><button>Cancel</button></div>"
            + "<div data-testpilot-context=\"iframe\" data-testpilot-frame-path='[\"#payment\",\"iframe[name=&quot;inner&quot;]\"]'>"
            + "<button>Deep</button></div>"
            + "<div data-testpilot-context=\"shadow\" data-testpilot-frame-path='[]' data-testpilot-shadow-path='[\"my-app\"]'>"
            + "<button>Shadow</button></div>"
            + "<div data-testpilot-context=\"iframe\"><button>Legacy</button></div>";

    private static Element button(Document d, String text) {
        return d.select("button").stream().filter(b -> b.text().equals(text)).findFirst().orElseThrow();
    }

    @Test
    public void framedElementsGetXpathRelativeToTheirOwnFrameDocument() {
        Document d = Jsoup.parse(HtmlSlimmer.slim(PAGE, 0));
        Assert.assertEquals(button(d, "Top two").attr("data-keel-source-xpath"), "(//button)[2]");
        Assert.assertEquals(button(d, "Pay").attr("data-keel-source-xpath"), "(//button)[1]");
        Assert.assertEquals(button(d, "Cancel").attr("data-keel-source-xpath"), "(//button)[2]");
        Assert.assertEquals(button(d, "Pay").attr("data-keel-frame-path"), "[\"#payment\"]");
        Assert.assertEquals(button(d, "Deep").attr("data-keel-source-xpath"), "(//button)[1]");
        Assert.assertEquals(button(d, "Deep").attr("data-keel-frame-path"), "[\"#payment\",\"iframe[name=\"inner\"]\"]");
        Assert.assertTrue(button(d, "Top one").attr("data-keel-frame-path").isEmpty());
    }

    @Test
    public void shadowAndPathlessContextsHaveNoXpath() {
        Document d = Jsoup.parse(HtmlSlimmer.slim(PAGE, 0));
        Assert.assertEquals(button(d, "Shadow").attr("data-keel-source-xpath"), "unsupported-context-ordinal");
        Assert.assertEquals(button(d, "Shadow").attr("data-keel-shadow-path"), "[\"my-app\"]");
        Assert.assertEquals(button(d, "Legacy").attr("data-keel-source-xpath"), "unsupported-context-ordinal");
    }
}
