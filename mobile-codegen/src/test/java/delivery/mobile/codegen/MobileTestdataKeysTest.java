package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileContext;
import delivery.mobile.ir.MobileInput;
import delivery.mobile.ir.MobileIrCodec;
import delivery.mobile.ir.MobileOperation;
import delivery.mobile.ir.MobilePlatform;
import delivery.mobile.ir.MobileProof;
import delivery.mobile.ir.MobileStep;
import delivery.mobile.ir.MobileTarget;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class MobileTestdataKeysTest {

    @Test
    public void ownerPhaseKeysSeparateLoginAndBody() {
        MobileStep loginType = typeStep("s1", "login", "phone_Number_Input", "01011112222");
        MobileStep bodyType = typeStep("s2", "login", "phone_Number_Input", "01099998888");
        MobileCaseIr login = caseOf("TC_LOGIN", List.of(loginType));
        MobileCaseIr body = caseOf("TC_BODY", List.of(bodyType));

        MobileTestdataKeys keys = MobileTestdataKeys.assign(
                List.of(login, body), Set.of("TC_LOGIN"));
        String loginKey = keys.keyFor("TC_LOGIN", MobileTestdataKeys.PHASE_LOGIN, loginType);
        String bodyKey = keys.keyFor("TC_BODY", MobileTestdataKeys.PHASE_BODY, bodyType);

        Assert.assertTrue(loginKey.contains(".login."), loginKey);
        Assert.assertTrue(bodyKey.contains(".body."), bodyKey);
        Assert.assertNotEquals(loginKey, bodyKey);
        Assert.assertEquals(keys.valuesByKey().get(loginKey), "01011112222");
        Assert.assertEquals(keys.valuesByKey().get(bodyKey), "01099998888");
    }

    @Test
    public void loginBodySameForcedKeyWithDifferentValuesRejected() {
        MobileStep first = typeStep("s1", "login", "email_Input", "alpha@example.invalid");
        MobileStep second = typeStep("s2", "login", "email_Input", "beta@example.invalid");
        MobileTestdataKeys keys = MobileTestdataKeys.forcingKey("TC_DUP.body.type_Email_Input.1");
        keys.keyFor("TC_DUP", MobileTestdataKeys.PHASE_BODY, first);
        try {
            keys.keyFor("TC_DUP", MobileTestdataKeys.PHASE_BODY, second);
            Assert.fail("expected conflict");
        } catch (IllegalStateException ex) {
            Assert.assertTrue(ex.getMessage().contains("Conflicting test data"), ex.getMessage());
            Assert.assertTrue(ex.getMessage().contains("TC_DUP.body.type_Email_Input.1"), ex.getMessage());
        }
    }

    @Test
    public void conflictingValuesFailClosedAndWriterDoesNotPublish() throws Exception {
        Path dir = Files.createTempDirectory("m4-03-conflict");
        MobileStep first = typeStep("s1", "login", "email_Input", "alpha@example.invalid");
        MobileStep second = typeStep("s2", "login", "email_Input", "beta@example.invalid");
        MobileTestdataKeys keys = MobileTestdataKeys.forcingKey("TC_DUP.body.type_Email_Input.1");
        keys.keyFor("TC_DUP", MobileTestdataKeys.PHASE_BODY, first);
        try {
            keys.keyFor("TC_DUP", MobileTestdataKeys.PHASE_BODY, second);
            MobileTestdataWriter.write(dir, keys);
            Assert.fail("expected conflict before write");
        } catch (IllegalStateException ex) {
            Assert.assertTrue(ex.getMessage().contains("Conflicting test data"), ex.getMessage());
        }
        Assert.assertFalse(Files.exists(dir.resolve(MobileTestdataWriter.RELATIVE_PATH)));
    }

    @Test
    public void secretRefNeverPublishedAsPropertyValue() throws Exception {
        Path dir = Files.createTempDirectory("m4-03-secret-props");
        MobileStep secret = new MobileStep(
                "s1", "login", MobileOperation.TYPE,
                nativeCtx(), target("password_Input"),
                new MobileInput("SECRET_REF", "auth.password", null, true),
                null, null, false, null, null, null, null, null, null, null);
        MobileCaseIr c = caseOf("TC_SECRET", List.of(secret));
        MobileTestdataKeys keys = MobileTestdataKeys.assign(List.of(c), Set.of());
        String k = keys.keyFor("TC_SECRET", MobileTestdataKeys.PHASE_BODY, secret);
        Assert.assertEquals(k, "", "SECRET_REF must not allocate testdata key");
        Path props = MobileTestdataWriter.write(dir, keys);
        String content = Files.readString(props);
        Assert.assertFalse(content.contains("auth.password="));
        Assert.assertFalse(content.contains("SuperSecret"));
    }

    @Test
    public void writerEmitsOwnerPhaseLiteralKeys() throws Exception {
        Path dir = Files.createTempDirectory("m4-03-props");
        MobileCaseIr ir = load("fixtures/login-case.json");
        MobileTestdataKeys keys = MobileTestdataKeys.assign(List.of(ir), Set.of());
        Path propsPath = MobileTestdataWriter.write(dir, keys);
        Assert.assertTrue(Files.isRegularFile(propsPath));
        Properties p = new Properties();
        try (var in = Files.newInputStream(propsPath)) {
            p.load(in);
        }
        Assert.assertFalse(p.isEmpty());
        boolean found = false;
        for (String name : p.stringPropertyNames()) {
            if (name.startsWith("TC_01.body.") && "01012345678".equals(p.getProperty(name))) {
                found = true;
            }
        }
        Assert.assertTrue(found, "expected owner+phase key for phone literal: " + p);
    }

    private static MobileCaseIr load(String resource) throws Exception {
        try (var in = MobileTestdataKeysTest.class.getClassLoader().getResourceAsStream(resource)) {
            Assert.assertNotNull(in, resource);
            return MobileIrCodec.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static MobileCaseIr caseOf(String tcId, List<MobileStep> steps) {
        return new MobileCaseIr(
                MobileCaseIr.SCHEMA_V1, MobilePlatform.ANDROID, tcId,
                "lib-1", "app-1", "cs-1", "nm-1",
                new MobileProof("PROVEN", "EXECUTE", "a1"),
                List.of(), steps);
    }

    private static MobileStep typeStep(String id, String screen, String a11y, String value) {
        return new MobileStep(
                id, screen, MobileOperation.TYPE, nativeCtx(), target(a11y),
                new MobileInput("LITERAL", null, value, false),
                null, null, false, null, null, null, null, null, null, null);
    }

    private static MobileContext nativeCtx() {
        return new MobileContext("NATIVE", "com.example", null, null);
    }

    private static MobileTarget target(String a11y) {
        return new MobileTarget("accessibilityId", a11y, "USER", null);
    }
}
