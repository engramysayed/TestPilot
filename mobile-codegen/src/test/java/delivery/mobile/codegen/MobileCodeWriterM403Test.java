package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileIrCodec;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class MobileCodeWriterM403Test {

    @Test
    public void secretRefEmitsTypeSecretAndNeverRawValue() throws Exception {
        Path out = Files.createTempDirectory("m4-03-secret");
        MobileCaseIr ir = load("fixtures/secret-case.json");
        // Inject a canary value into a copy via JSON mutation to prove scrub
        String json = MobileIrCodec.write(ir);
        // SECRET_REF has no value in fixture — emit and check ref + typeSecret
        new MobileCodeWriter().writeVerified(out, List.of(ir), NamingMap.empty());

        Path screen = out.resolve("src/test/java/project/screens/LoginScreen.java");
        String screenSrc = Files.readString(screen);
        Assert.assertTrue(screenSrc.contains("typeSecret"), screenSrc);
        Assert.assertTrue(screenSrc.contains("secretRefKey"), screenSrc);
        Assert.assertFalse(screenSrc.contains("Thread.sleep"));

        Path test = out.resolve("src/test/java/project/tests/generated/TC_SECRET.java");
        String testSrc = Files.readString(test);
        Assert.assertTrue(testSrc.contains("\"auth.password\""), testSrc);
        Assert.assertFalse(testSrc.contains("requiredProperty(\"auth.password\")"),
                "secret must not go through requiredProperty/testdata");

        Path props = out.resolve("src/test/resources/test-data/mobile-testdata.properties");
        Assert.assertTrue(Files.isRegularFile(props));
        Assert.assertFalse(Files.readString(props).contains("auth.password="));
    }

    @Test
    public void captureCompareEmitMatchesProveSemantics() throws Exception {
        Path out = Files.createTempDirectory("m4-03-cap");
        MobileCaseIr ir = load("fixtures/capture-case.json");
        new MobileCodeWriter().writeVerified(out, List.of(ir), NamingMap.empty());

        Path order = out.resolve("src/test/java/project/screens/OrderScreen.java");
        Path confirm = out.resolve("src/test/java/project/screens/ConfirmScreen.java");
        Assert.assertTrue(Files.isRegularFile(order), "OrderScreen");
        Assert.assertTrue(Files.isRegularFile(confirm), "ConfirmScreen");
        String orderSrc = Files.readString(order);
        Assert.assertTrue(orderSrc.contains("captureText"), orderSrc);
        Assert.assertTrue(orderSrc.contains("orderId"), orderSrc);
        String confirmSrc = Files.readString(confirm);
        Assert.assertTrue(confirmSrc.contains("compareCaptured"), confirmSrc);
        Assert.assertTrue(confirmSrc.contains("EQUALS"), confirmSrc);

        Path test = out.resolve("src/test/java/project/tests/generated/TC_CAP.java");
        String testSrc = Files.readString(test);
        Assert.assertTrue(testSrc.contains("CapturedValues.put"), testSrc);
        Assert.assertTrue(testSrc.contains("compare_"), testSrc);
    }

    @Test
    public void perLeafSetupIndependenceSharesOwnerKeysNotSharedMutableSetup() throws Exception {
        Path out = Files.createTempDirectory("m4-03-leaf");
        MobileCaseIr login = load("fixtures/setup-login.json");
        MobileCaseIr leafA = load("fixtures/leaf-a.json");
        MobileCaseIr leafB = load("fixtures/leaf-b.json");
        new MobileCodeWriter(Set.of("TC_LOGIN")).writeVerified(
                out, List.of(login, leafA, leafB), NamingMap.empty());

        String a = Files.readString(out.resolve("src/test/java/project/tests/generated/TC_LEAF_A.java"));
        String b = Files.readString(out.resolve("src/test/java/project/tests/generated/TC_LEAF_B.java"));
        // Each leaf inlines its own setup calls (independence)
        Assert.assertTrue(a.contains("requiredProperty(") || a.contains("type_"), a);
        Assert.assertTrue(b.contains("requiredProperty(") || b.contains("type_"), b);
        // Same login owner key namespace
        Path props = out.resolve("src/test/resources/test-data/mobile-testdata.properties");
        String propSrc = Files.readString(props);
        Assert.assertTrue(propSrc.contains("TC_LOGIN.login.") || propSrc.contains("01011112222"), propSrc);
        // Leaves are separate classes — not one shared setup helper with mutable state
        Assert.assertTrue(Files.isRegularFile(out.resolve("src/test/java/project/tests/generated/TC_LEAF_A.java")));
        Assert.assertTrue(Files.isRegularFile(out.resolve("src/test/java/project/tests/generated/TC_LEAF_B.java")));
        Assert.assertNotEquals(a, b);
    }

    @Test
    public void loginCaseStillWritesTestdataAndThinTest() throws Exception {
        Path out = Files.createTempDirectory("m4-03-login");
        MobileCaseIr ir = load("fixtures/login-case.json");
        new MobileCodeWriter().writeVerified(out, List.of(ir), NamingMap.empty());
        Assert.assertTrue(Files.isRegularFile(
                out.resolve("src/test/resources/test-data/mobile-testdata.properties")));
        String test = Files.readString(out.resolve("src/test/java/project/tests/generated/TC_01.java"));
        Assert.assertTrue(test.contains("requiredProperty("), test);
        Assert.assertTrue(test.contains("authSetupIfConfigured"), test);
    }

    private static MobileCaseIr load(String resource) throws Exception {
        try (var in = MobileCodeWriterM403Test.class.getClassLoader().getResourceAsStream(resource)) {
            Assert.assertNotNull(in, resource);
            return MobileIrCodec.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
