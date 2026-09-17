package delivery.privacy;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class SecretSanitizerTest {

    private static final String CANARY = "SECRET_CANARY_PASSWORD";

    @Test
    public void passwordDomValuesAndConfiguredSecretsAreMasked() {
        String html = "<form><input type=\"password\" name=\"p\" value=\"" + CANARY + "\">"
                + "<p>token=" + CANARY + "</p></form>";
        String scrubbed = SecretSanitizer.scrubHtml(html, List.of(CANARY));
        Assert.assertFalse(scrubbed.contains(CANARY), scrubbed);
        Assert.assertTrue(scrubbed.contains(SecretSanitizer.MASK), scrubbed);
        Assert.assertEquals(SecretSanitizer.extractPasswordValues(html), List.of(CANARY));
        String prompt = SecretSanitizer.scrubPrompt("type password " + CANARY);
        Assert.assertFalse(prompt.contains(CANARY), prompt);
    }

    @Test
    public void urlsDropUserinfoAndTokenQuery() {
        String url = "https://user:SECRET_CANARY_PASSWORD@shop.example.com/x?token=abc123&ok=1";
        String scrubbed = SecretSanitizer.scrubUrl(url);
        Assert.assertFalse(scrubbed.contains("SECRET_CANARY_PASSWORD"), scrubbed);
        Assert.assertFalse(scrubbed.toLowerCase().contains("token=abc123"), scrubbed);
        Assert.assertTrue(scrubbed.contains("ok=1"), scrubbed);
    }

    @Test
    public void jsResultsAreScrubbedBeforeLogging() {
        String js = "{\"password\":\"" + CANARY + "\"}";
        String scrubbed = SecretSanitizer.scrubJsResult(js, List.of(CANARY));
        Assert.assertFalse(scrubbed.contains(CANARY));
    }
}
