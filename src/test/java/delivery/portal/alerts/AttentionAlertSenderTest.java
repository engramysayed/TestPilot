package delivery.portal.alerts;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AttentionAlertSenderTest {
    private final AttentionAlertSender sender = new AttentionAlertSender();

    @Test
    public void offSendsNothing() {
        Rec transport = new Rec();
        AttentionAlertSender.Result result = sender.sendEach(
                AttentionAlertSender.Prefs.OFF, "hello", ready(), transport);
        Assert.assertTrue(result.ok());
        Assert.assertTrue(result.sent().isEmpty());
        Assert.assertTrue(transport.urls.isEmpty());
        Assert.assertTrue(transport.mail.isEmpty());
    }

    @Test
    public void telegramNeedsAServerToken() {
        Rec transport = new Rec();
        AttentionAlertSender.Prefs prefs = new AttentionAlertSender.Prefs(true, "@keelgroup", false, "");
        AttentionAlertSender.Result result = sender.sendEach(
                prefs, "hello", new AttentionAlertSender.Config("", false, ""), transport);
        Assert.assertFalse(result.ok());
        Assert.assertTrue(result.errors().get(0).contains("not set up"));
        Assert.assertTrue(transport.urls.isEmpty());
    }

    @Test
    public void telegramPostsChatWithoutLeakingTheTokenInTheError() {
        Rec transport = new Rec();
        transport.boom = new IllegalStateException("https://api.telegram.org/bot123:abc/sendMessage failed");
        AttentionAlertSender.Prefs prefs = new AttentionAlertSender.Prefs(true, "@keelgroup", false, "");
        AttentionAlertSender.Result result = sender.sendEach(
                prefs, "hello", new AttentionAlertSender.Config("123:abc", false, ""), transport);
        Assert.assertFalse(result.ok());
        Assert.assertFalse(result.errors().get(0).contains("123:abc"));
        Assert.assertTrue(result.errors().get(0).contains("could not be reached"));
    }

    @Test
    public void telegramAcceptsAChatId() {
        Rec transport = new Rec();
        AttentionAlertSender.Prefs prefs = new AttentionAlertSender.Prefs(true, "-10099", false, "");
        AttentionAlertSender.Result result = sender.sendEach(
                prefs, "hello", new AttentionAlertSender.Config("123:abc", false, ""), transport);
        Assert.assertTrue(result.ok(), result.errors().toString());
        Assert.assertEquals(result.sent(), List.of("Telegram"));
        Assert.assertTrue(transport.urls.get(0).startsWith("https://api.telegram.org/bot123:abc/sendMessage"));
        Assert.assertTrue(transport.bodies.get(0).contains("-10099"));
    }

    @Test
    public void emailNeedsMailAndARealAddress() {
        Rec transport = new Rec();
        AttentionAlertSender.Prefs prefs = new AttentionAlertSender.Prefs(false, "", true, "you@company.com");
        AttentionAlertSender.Result blocked = sender.sendEach(
                prefs, "hello", new AttentionAlertSender.Config("", false, ""), transport);
        Assert.assertTrue(blocked.errors().get(0).contains("delivery.mail-enabled"));
        Assert.assertTrue(transport.mail.isEmpty());

        AttentionAlertSender.Result sent = sender.sendEach(
                prefs, "Execute run failed.", new AttentionAlertSender.Config("", true, "keel@localhost"), transport);
        Assert.assertTrue(sent.ok(), sent.errors().toString());
        Assert.assertEquals(transport.mail, List.of("you@company.com"));
    }

    @Test
    public void onlyANewFailureOrBlockNeedsAttention() {
        Assert.assertTrue(AttentionAlertSender.newlyNeedsAttention("RUNNING", "FAILED"));
        Assert.assertTrue(AttentionAlertSender.newlyNeedsAttention("RUNNING", "COMPLETED_WITH_BLOCK"));
        Assert.assertFalse(AttentionAlertSender.newlyNeedsAttention("RUNNING", "COMPLETED"));
        Assert.assertFalse(AttentionAlertSender.newlyNeedsAttention("FAILED", "FAILED"));
        Assert.assertFalse(AttentionAlertSender.newlyNeedsAttention("", "CANCELLED"));
    }

    @Test
    public void attentionTextKeepsTheJobLinkAndDropsSecrets() {
        String text = AttentionAlertSender.attentionText(
                "http://localhost:8080/", "EXECUTE", "FAILED", "prj_1", "job_9", "password=secret");
        Assert.assertTrue(text.contains("Execute run failed"));
        Assert.assertTrue(text.contains("http://localhost:8080/status?jobId=job_9"));
        Assert.assertFalse(text.contains("secret"));
    }

    @Test
    public void prefsRoundTrip() {
        AttentionAlertSender.Prefs prefs = AttentionAlertSender.Prefs.fromRequest(Map.of(
                "telegram", Map.of("on", true, "target", " @keelgroup "),
                "email", Map.of("on", false, "target", "")
        ));
        AttentionAlertSender.Prefs again = AttentionAlertSender.Prefs.parse(prefs.toJson());
        Assert.assertTrue(again.telegramOn());
        Assert.assertEquals(again.telegramChat(), "@keelgroup");
        Assert.assertFalse(again.emailOn());
    }

    private static AttentionAlertSender.Config ready() {
        return new AttentionAlertSender.Config("", false, "keel@localhost");
    }

    static final class Rec implements AttentionAlertSender.Transport {
        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<String> mail = new ArrayList<>();
        int code = 200;
        String response = "{\"ok\":true}";
        RuntimeException boom;

        @Override
        public String postBody(String url, String json) {
            urls.add(url);
            bodies.add(json);
            if (boom != null) {
                throw boom;
            }
            return response;
        }

        @Override
        public void sendMail(String from, String to, String subject, String text) {
            mail.add(to);
        }
    }
}
