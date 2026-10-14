package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileIrCodec;
import delivery.mobile.ir.MobileStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MobileCodeWriterTest {

    @Test
    public void writesOneScreenClassAndThinGeneratedTest() throws Exception {
        Path out = Files.createTempDirectory("m4-02-writer");
        MobileCaseIr ir = load("fixtures/login-case.json");
        MobileCaseIr roundTripped = MobileIrCodec.roundTrip(ir);
        NamingMap names = NamingMap.empty();
        new MobileCodeWriter().writeVerified(out, List.of(roundTripped), names);

        Path login = out.resolve("src/test/java/project/screens/LoginScreen.java");
        Assert.assertTrue(Files.isRegularFile(login), "LoginScreen.java");
        String src = Files.readString(login);
        Assert.assertTrue(src.contains("class LoginScreen"));
        Assert.assertTrue(src.contains("SettledMobileActions"));
        Assert.assertFalse(src.contains("Thread.sleep"));
        Assert.assertFalse(src.contains("_Locators"));
        Assert.assertFalse(src.contains("_Actions"));

        Path test = out.resolve("src/test/java/project/tests/generated/TC_01.java");
        Assert.assertTrue(Files.isRegularFile(test), "TC_01.java");
        Assert.assertTrue(Files.isRegularFile(out.resolve("docs/name-map.json")));
    }

    @Test
    public void arabicSelectorRoundTripThroughCodecAndWriter() throws Exception {
        Path out = Files.createTempDirectory("m4-02-ar");
        MobileCaseIr ir = MobileIrCodec.roundTrip(load("fixtures/arabic-case.json"));
        new MobileCodeWriter().writeVerified(out, List.of(ir), NamingMap.empty());
        Path screen = out.resolve("src/test/java/project/screens/ProfileScreen.java");
        String src = Files.readString(screen);
        Assert.assertTrue(src.contains("الاسم_الأول_Input"), src);
    }

    @Test
    public void demoteDumpStemToTodoKeepsGoodCases() throws Exception {
        Path out = Files.createTempDirectory("m4-02-demote");
        MobileCaseIr good = load("fixtures/login-case.json");
        MobileCaseIr dump = load("fixtures/dump-case.json");
        new MobileCodeWriter().writeVerified(out, List.of(good, dump), NamingMap.empty());

        Assert.assertTrue(Files.isRegularFile(out.resolve("src/test/java/project/screens/LoginScreen.java")));
        Assert.assertFalse(Files.exists(out.resolve("src/test/java/project/screens/Screen.java")),
                "must not emit dump Screen class");
        Path todo = out.resolve("src/test/java/project/tests/todo/TC_DUMPTodo.java");
        Assert.assertTrue(Files.isRegularFile(todo), "dump demoted to TODO");
        Assert.assertTrue(Files.isRegularFile(out.resolve("src/test/java/project/tests/generated/TC_01.java")));
    }

    @Test
    public void duplicateIdentitiesStayStableAcrossReorderAndTitleChange() throws Exception {
        Path out1 = Files.createTempDirectory("m4-02-n1");
        Path out2 = Files.createTempDirectory("m4-02-n2");
        MobileCaseIr ir = load("fixtures/login-case.json");
        NamingMap names = NamingMap.empty();
        new MobileCodeWriter().writeVerified(out1, List.of(ir), names);

        // Persist and reload
        Path mapPath = out1.resolve("docs/name-map.json");
        NamingMap reloaded = NamingMap.load(mapPath);

        // Reordered steps + different tc title (tcId change) — identities unchanged
        String json = MobileIrCodec.write(ir);
        json = json.replace("\"tcId\" : \"TC_01\"", "\"tcId\" : \"TC_01_RENAMED\"");
        // reverse steps array order manually via round trip rebuild
        MobileCaseIr renamed = MobileIrCodec.parse(json);
        List<MobileStep> reversed = List.of(renamed.steps().get(2), renamed.steps().get(1), renamed.steps().get(0));
        MobileCaseIr reordered = new MobileCaseIr(
                renamed.schemaVersion(),
                renamed.platform(),
                renamed.tcId(),
                renamed.libraryRevisionId(),
                renamed.appRevisionId(),
                renamed.caseSettingsRevisionId(),
                renamed.namingRevisionId(),
                renamed.proof(),
                renamed.setupRefs(),
                reversed);

        new MobileCodeWriter().writeVerified(out2, List.of(reordered), reloaded);
        String screen1 = Files.readString(out1.resolve("src/test/java/project/screens/LoginScreen.java"));
        String screen2 = Files.readString(out2.resolve("src/test/java/project/screens/LoginScreen.java"));
        // Locator field names must be stable
        Assert.assertEquals(
                extractFields(screen1),
                extractFields(screen2));
    }

    @Test
    public void smellRejectsDumpIfForcedThroughVerify() {
        ScreenAccumulator acc = new ScreenAccumulator(NamingMap.empty());
        MobileCaseIr dump = loadQuiet("fixtures/dump-case.json");
        acc.addAll(List.of(dump));
        try {
            MobileCodegenSmellCheck.verify(acc.screens());
            Assert.fail("expected smell failure");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("dump") || e.getMessage().contains("blank"), e.getMessage());
        }
    }

    private static List<String> extractFields(String src) {
        return src.lines().filter(l -> l.contains("_Locator")).map(String::trim).sorted().toList();
    }

    private static MobileCaseIr load(String resource) throws Exception {
        try (var in = MobileCodeWriterTest.class.getClassLoader().getResourceAsStream(resource)) {
            Assert.assertNotNull(in, resource);
            return MobileIrCodec.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static MobileCaseIr loadQuiet(String resource) {
        try {
            return load(resource);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
