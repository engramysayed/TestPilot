package delivery.authoring;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AccessibleNameIconSiblingTest {

    @Test
    public void skipsIconBetweenInputAndCaption() {
        String html = "<html><body><div><input id='x'/>"
                + "<i class='icon'></i><span>Username</span></div></body></html>";
        Document doc = Jsoup.parse(html);
        Element input = doc.getElementById("x");
        Assert.assertEquals(AccessibleName.of(input), "Username");
    }

    @Test
    public void stopsAtInteractiveSibling() {
        String html = "<html><body><div><input id='x'/>"
                + "<button>Go</button><span>Username</span></div></body></html>";
        Document doc = Jsoup.parse(html);
        Element input = doc.getElementById("x");
        Assert.assertNotEquals(AccessibleName.of(input), "Username");
    }

    @Test
    public void exhaustsHopLimitBeforeCaption() {
        String html = "<html><body><div><input id='x'/>"
                + "<i></i><br/><em></em><span>Too far</span></div></body></html>";
        Document doc = Jsoup.parse(html);
        Element input = doc.getElementById("x");
        Assert.assertEquals(AccessibleName.of(input), "");
    }
}
