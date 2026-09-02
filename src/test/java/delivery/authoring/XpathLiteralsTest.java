package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class XpathLiteralsTest {

    @Test
    public void quoteWrapsPlainText() {
        Assert.assertEquals(XpathLiterals.quote("Login"), "'Login'");
    }

    @Test
    public void quoteUsesConcatForApostrophe() {
        String q = XpathLiterals.quote("It's gone");
        Assert.assertTrue(q.startsWith("concat("), q);
        Assert.assertTrue(q.contains("\"'\""), q);
    }
}
