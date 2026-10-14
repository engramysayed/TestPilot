package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileIrCodec;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M4-08 contract: multi-control chron binds by step identity; SCROLL/SWIPE emit real gestures.
 */
public class MobileCodeWriterM408ChronIdentityTest {

    @Test
    public void multiTypeChronBindsDistinctMethodsPerTargetIdentity() throws Exception {
        Path out = Files.createTempDirectory("m4-08-multitype");
        MobileCaseIr ir = MobileIrCodec.roundTrip(load("fixtures/multi-type-case.json"));
        new MobileCodeWriter().writeVerified(out, List.of(ir), NamingMap.empty());

        Path screen = out.resolve("src/test/java/project/screens/LoginScreen.java");
        String screenSrc = Files.readString(screen);
        Assert.assertTrue(screenSrc.contains("type_"), screenSrc);
        // Both controls present as distinct methods
        Assert.assertTrue(screenSrc.contains("type_Username_Input") || screenSrc.contains("type_Username"),
                "expected username type method: " + screenSrc);
        Assert.assertTrue(screenSrc.contains("type_Password_Input") || screenSrc.contains("type_Password"),
                "expected password type method: " + screenSrc);

        Path test = out.resolve("src/test/java/project/tests/generated/TC_MULTI.java");
        String testSrc = Files.readString(test);
        List<String> typeCalls = testSrc.lines()
                .map(String::trim)
                .filter(l -> l.contains(".type_"))
                .toList();
        Assert.assertEquals(typeCalls.size(), 2, "expected two type chron lines: " + testSrc);
        String firstMethod = methodName(typeCalls.get(0));
        String secondMethod = methodName(typeCalls.get(1));
        Assert.assertNotEquals(firstMethod, secondMethod,
                "multi-TYPE must not reuse the same screen method: " + typeCalls);
        Assert.assertTrue(firstMethod.toLowerCase().contains("username"),
                "first TYPE chron must target username identity: " + firstMethod);
        Assert.assertTrue(secondMethod.toLowerCase().contains("password"),
                "second TYPE chron must target password identity: " + secondMethod);
        // Property keys stay aligned with each control
        Assert.assertTrue(typeCalls.get(0).contains("type_Username") || typeCalls.get(0).contains("Username"),
                typeCalls.get(0));
        Assert.assertTrue(typeCalls.get(1).contains("type_Password") || typeCalls.get(1).contains("Password"),
                typeCalls.get(1));
    }

    @Test
    public void multiAssertChronBindsDistinctMethodsPerTargetIdentity() throws Exception {
        Path out = Files.createTempDirectory("m4-08-multiassert");
        MobileCaseIr ir = MobileIrCodec.roundTrip(load("fixtures/multi-assert-case.json"));
        new MobileCodeWriter().writeVerified(out, List.of(ir), NamingMap.empty());

        Path screen = out.resolve("src/test/java/project/screens/HomeScreen.java");
        String screenSrc = Files.readString(screen);
        Assert.assertTrue(screenSrc.contains("assert_"), screenSrc);

        Path test = out.resolve("src/test/java/project/tests/generated/TC_ASSERTS.java");
        String testSrc = Files.readString(test);
        List<String> assertCalls = testSrc.lines()
                .map(String::trim)
                .filter(l -> l.contains(".assert_"))
                .toList();
        Assert.assertEquals(assertCalls.size(), 2, "expected two assert chron lines: " + testSrc);
        String first = methodName(assertCalls.get(0));
        String second = methodName(assertCalls.get(1));
        Assert.assertNotEquals(first, second, "multi-ASSERT must not reuse get(0): " + assertCalls);
        Assert.assertTrue(first.toLowerCase().contains("title"), first);
        Assert.assertTrue(second.toLowerCase().contains("subtitle"), second);
    }

    @Test
    public void scrollUntilAndSwipeEmitRealGestureChronNotTap() throws Exception {
        Path out = Files.createTempDirectory("m4-08-scroll");
        MobileCaseIr ir = MobileIrCodec.roundTrip(load("fixtures/scroll-swipe-case.json"));
        new MobileCodeWriter().writeVerified(out, List.of(ir), NamingMap.empty());

        Path screen = out.resolve("src/test/java/project/screens/CatalogScreen.java");
        Assert.assertTrue(Files.isRegularFile(screen), "CatalogScreen");
        String screenSrc = Files.readString(screen);
        Assert.assertTrue(screenSrc.contains("scrollUntil"), screenSrc);
        Assert.assertTrue(screenSrc.contains("SettledMobileActions.scrollUntil"), screenSrc);
        Assert.assertTrue(screenSrc.contains("SettledMobileActions.swipe"), screenSrc);
        // Must not map scroll/swipe util to tap
        Assert.assertFalse(Pattern.compile("void scrollUntil_\\w+\\([^)]*\\)\\s*\\{\\s*SettledMobileActions\\.tap")
                .matcher(screenSrc).find(), screenSrc);
        Assert.assertFalse(Pattern.compile("void swipe_\\w+\\([^)]*\\)\\s*\\{\\s*SettledMobileActions\\.tap")
                .matcher(screenSrc).find(), screenSrc);

        Path test = out.resolve("src/test/java/project/tests/generated/TC_SCROLL.java");
        String testSrc = Files.readString(test);
        Assert.assertTrue(testSrc.contains(".scrollUntil_"), "scroll chron missing: " + testSrc);
        Assert.assertTrue(testSrc.contains(".swipe_"), "swipe chron missing: " + testSrc);
        Assert.assertTrue(testSrc.contains("\"DOWN\""), testSrc);
        Assert.assertTrue(testSrc.contains(", 7)"), "maxSwipes from step: " + testSrc);
        Assert.assertTrue(testSrc.contains("\"LEFT\""), testSrc);
    }

    private static String methodName(String callLine) {
        Matcher m = Pattern.compile("\\.([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(callLine);
        Assert.assertTrue(m.find(), "no method in: " + callLine);
        return m.group(1);
    }

    private static MobileCaseIr load(String resource) throws Exception {
        try (var in = MobileCodeWriterM408ChronIdentityTest.class.getClassLoader().getResourceAsStream(resource)) {
            Assert.assertNotNull(in, resource);
            return MobileIrCodec.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
