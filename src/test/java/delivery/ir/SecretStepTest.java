package delivery.ir;

import delivery.codegen.ProvenStep;
import delivery.ir.SecretStep.Target;
import delivery.settle.SettlePolicy.Action;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.testng.Assert.*;

public class SecretStepTest {

    static Target named(String name) { return new Target("input", "text", null, name, null, null, null, null, null, null); }

    @Test public void targetDetection() {
        assertTrue(SecretStep.isSecretTarget(Target.ofType("password")));
        assertTrue(SecretStep.isSecretTarget(new Target("input", "text", "one-time-code", null, null, null, null, "numeric", null, 6)));
        assertTrue(SecretStep.isSecretTarget(new Target("input", "text", "current-password", null, null, null, null, null, null, null)));
        assertTrue(SecretStep.isSecretTarget(new Target("input", "text", null, null, null, null, null, null, "disc", null)));
        for (String n : new String[]{"otp", "otp_code", "oneTimeCode", "one-time", "pin", "cvv", "Security code", "verification_code", "2fa", "Passwort"})
            assertTrue(SecretStep.isSecretTarget(named(n)), n);
        for (String n : new String[]{"email", "username", "search", "spinner", "option", "pinterest", "shipping"})
            assertFalse(SecretStep.isSecretTarget(named(n)), n);
        assertFalse(SecretStep.isSecretTarget(new Target("input", "text", null, null, null, null, null, null, "none", null)));
        assertFalse(SecretStep.isSecretTarget(null));
    }

    @Test public void valueDetection() {
        assertTrue(SecretStep.isSecretValue("${TARGET_PASSWORD}", null));
        assertTrue(SecretStep.isSecretValue("${env.API_KEY}", null));
        assertTrue(SecretStep.isSecretValue("${OTP}", null));
        assertTrue(SecretStep.isSecretValue("s3cret!", List.of("s3cret!")));
        assertFalse(SecretStep.isSecretValue("${TARGET_USERNAME}", null));
        assertFalse(SecretStep.isSecretValue("hello", List.of("s3cret!")));
        assertFalse(SecretStep.isSecretValue("", List.of("")));
    }

    @Test public void settleKindAndLogs() {
        assertEquals(SecretStep.settleKind(Action.TYPE, true), Action.TYPE_SECRET);
        assertEquals(SecretStep.settleKind(Action.CLEAR, true), Action.TYPE_SECRET);
        assertEquals(SecretStep.settleKind(Action.TYPE, false), Action.TYPE);
        assertEquals(SecretStep.settleKind(Action.CLICK, true), Action.CLICK);
        assertEquals(SecretStep.logValue("hunter2", true), SecretStep.MASK);
        ProvenStep s = new ProvenStep("TC1", "P", "elementAction", "type", "id", "pw", "hunter2", "", "", true, "", "", true);
        assertFalse(s.toString().contains("hunter2"), s.toString());
        assertTrue(s.withScreenshot("x.png").withPageName("Q").secret(), "with* keep the flag");
        assertTrue(new ProvenStep("TC1", "P", "elementAction", "type", "id", "q", "milk", "", "", true, "").toString().contains("milk"));
    }

    @Test public void irFieldRules() {
        ProvenStep plain = new ProvenStep("TC1", "P", "elementAction", "type", "id", "q", "milk", "", "", true, "");
        assertFalse(ProvenStepCodec.toJson(plain).has("secret"), "absent when false");
        JSONObject j = ProvenStepCodec.toJson(plain.withSecret(true));
        assertEquals(j.get("secret"), true);
        assertTrue(ProvenStepCodec.fromJson(j).secret());
        assertFalse(ProvenStepCodec.fromJson(new JSONObject().put("action", "type")).secret());
        assertFalse(ProvenStepCodec.fromJson(new JSONObject().put("action", "type").put("secret", JSONObject.NULL)).secret());
        assertThrows(IllegalArgumentException.class, () -> ProvenStepCodec.fromJson(new JSONObject().put("secret", "true")));
        assertThrows(IllegalArgumentException.class, () -> ProvenStepCodec.fromJson(new JSONObject().put("secret", 1)));
    }

    @Test public void liveElementFacts() {
        WebDriver d;
        try {
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu");
            d = new ChromeDriver(o);
        } catch (Exception e) { throw new SkipException("Chrome unavailable: " + e.getMessage()); }
        try {
            String html = "<input id=pw type=password value=hunter2><input id=otp autocomplete=one-time-code>"
                    + "<input id=masked style='-webkit-text-security:disc'><input id=code aria-label='SMS code'>"
                    + "<input id=email name=email value=a@b.c>";
            d.get("data:text/html;charset=utf-8," + URLEncoder.encode(html, StandardCharsets.UTF_8).replace("+", "%20"));
            for (String id : new String[]{"pw", "otp", "masked", "code"})
                assertTrue(SecretStep.isSecretTarget(SecretStep.fromElement(d.findElement(By.id(id)))), id);
            assertFalse(SecretStep.isSecretTarget(SecretStep.fromElement(d.findElement(By.id("email")))));
        } finally { d.quit(); }
    }
}
