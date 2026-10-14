package delivery.mobile.ir;

import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.*;

public class MobileIrCodecTest {

    @Test
    public void parsesContractExampleAndRoundTrips() throws Exception {
        String json = Files.readString(
                Path.of("src/test/resources/mobile-ir/valid-type-secret.json"),
                StandardCharsets.UTF_8);
        MobileCaseIr ir = MobileIrCodec.parse(json);
        assertEquals(ir.schemaVersion(), MobileCaseIr.SCHEMA_V1);
        assertEquals(ir.platform(), MobilePlatform.ANDROID);
        assertEquals(ir.steps().size(), 1);
        assertEquals(ir.steps().get(0).operation(), MobileOperation.TYPE);
        assertEquals(ir.steps().get(0).input().kind(), "SECRET_REF");
        assertEquals(ir.steps().get(0).context().packageName(), "com.example.test");

        MobileCaseIr again = MobileIrCodec.roundTrip(ir);
        assertEquals(again.tcId(), ir.tcId());
        assertEquals(again.steps().get(0).target().value(), "phone_Number_Input");
    }

    @Test
    public void rejectsUnknownSchema() {
        String json = """
                {"schemaVersion":"mobile-ir/2","platform":"ANDROID","tcId":"T",
                "libraryRevisionId":"l","appRevisionId":"a","caseSettingsRevisionId":"c",
                "namingRevisionId":"n","proof":{"status":"TODO","source":"NONE"},"setupRefs":[],"steps":[]}
                """;
        try {
            MobileIrCodec.parse(json);
            fail("expected rejection");
        } catch (MobileIrException e) {
            assertEquals(e.code(), "SCHEMA_UNSUPPORTED");
        }
    }

    @Test
    public void rejectsWebPlatform() {
        String json = """
                {"schemaVersion":"mobile-ir/1","platform":"WEB","tcId":"T",
                "libraryRevisionId":"l","appRevisionId":"a","caseSettingsRevisionId":"c",
                "namingRevisionId":"n","proof":{"status":"TODO","source":"NONE"},"setupRefs":[],"steps":[]}
                """;
        try {
            MobileIrCodec.parse(json);
            fail();
        } catch (MobileIrException e) {
            assertEquals(e.code(), "PLATFORM_WEB_REFUSED");
        }
    }

    @Test
    public void rejectsUnknownOperation() {
        String json = """
                {"schemaVersion":"mobile-ir/1","platform":"ANDROID","tcId":"T",
                "libraryRevisionId":"l","appRevisionId":"a","caseSettingsRevisionId":"c",
                "namingRevisionId":"n","proof":{"status":"TODO","source":"NONE"},"setupRefs":[],
                "steps":[{"stepId":"s1","operation":"CLICK_JS","context":{"kind":"NATIVE"},
                "target":{"strategy":"id","value":"x","origin":"USER"}}]}
                """;
        try {
            MobileIrCodec.parse(json);
            fail();
        } catch (MobileIrException e) {
            assertEquals(e.code(), "OP_UNSUPPORTED");
        }
    }

    @Test
    public void rejectsLiteralOnSensitive() {
        String json = """
                {"schemaVersion":"mobile-ir/1","platform":"ANDROID","tcId":"T",
                "libraryRevisionId":"l","appRevisionId":"a","caseSettingsRevisionId":"c",
                "namingRevisionId":"n","proof":{"status":"TODO","source":"NONE"},"setupRefs":[],
                "steps":[{"stepId":"s1","operation":"TYPE","context":{"kind":"NATIVE","package":"p"},
                "target":{"strategy":"id","value":"x","origin":"USER"},
                "input":{"kind":"LITERAL","value":"secret","sensitive":true}}]}
                """;
        try {
            MobileIrCodec.parse(json);
            fail();
        } catch (MobileIrException e) {
            assertEquals(e.code(), "INPUT_LITERAL_SENSITIVE");
        }
    }

    @Test
    public void rejectsUnknownBehavioralTopLevelField() {
        String json = """
                {"schemaVersion":"mobile-ir/1","platform":"ANDROID","tcId":"T",
                "libraryRevisionId":"l","appRevisionId":"a","caseSettingsRevisionId":"c",
                "namingRevisionId":"n","proof":{"status":"TODO","source":"NONE"},"setupRefs":[],
                "steps":[],"healMutate":true}
                """;
        try {
            MobileIrCodec.parse(json);
            fail();
        } catch (MobileIrException e) {
            assertEquals(e.code(), "UNKNOWN_FIELD");
        }
    }

    @Test
    public void unicodeRoundTrip() {
        String json = """
                {"schemaVersion":"mobile-ir/1","platform":"ANDROID","tcId":"TC_عربي",
                "libraryRevisionId":"l","appRevisionId":"a","caseSettingsRevisionId":"c",
                "namingRevisionId":"n","proof":{"status":"TODO","source":"NONE"},"setupRefs":[],
                "steps":[{"stepId":"s1","operation":"TAP","context":{"kind":"NATIVE","package":"p"},
                "target":{"strategy":"text","value":"تسجيل الدخول","origin":"USER"}}]}
                """;
        MobileCaseIr ir = MobileIrCodec.parse(json);
        assertTrue(MobileIrCodec.write(ir).contains("تسجيل الدخول"));
        assertEquals(MobileIrCodec.roundTrip(ir).steps().get(0).target().value(), "تسجيل الدخول");
    }
}
