package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileIrCodec;
import delivery.mobile.ir.MobileStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

public class MobileCodegenNamingTest {

    @Test
    public void underscoreSuffixRules() throws Exception {
        MobileCaseIr ir = load("fixtures/login-case.json");
        MobileStep type = ir.steps().get(0);
        MobileStep tap = ir.steps().get(1);
        String typeField = MobileCodegenNaming.locatorFieldName(type, null);
        String tapField = MobileCodegenNaming.locatorFieldName(tap, null);
        Assert.assertTrue(typeField.endsWith("_Input_Locator") || typeField.contains("Input"), typeField);
        Assert.assertTrue(tapField.endsWith("_Btn_Locator") || tapField.contains("Btn"), tapField);
        Assert.assertTrue(MobileCodegenNaming.actionMethodName(type, null).startsWith("type_"));
        Assert.assertTrue(MobileCodegenNaming.actionMethodName(tap, null).startsWith("tap_"));
    }

    @Test
    public void arabicSelectorPreservedInByExpression() throws Exception {
        MobileCaseIr ir = load("fixtures/arabic-case.json");
        String by = MobileCodegenNaming.byExpression(ir.steps().get(0).target());
        Assert.assertTrue(by.contains("الاسم_الأول_Input"), by);
        Assert.assertFalse(by.contains("???"));
    }

    @Test
    public void javaKeywordsSanitized() {
        Assert.assertEquals(MobileCodegenNaming.sanitizeJavaIdentifier("class", "fallback"), "class_");
        Assert.assertFalse(MobileCodegenNaming.isValidJavaIdentifier("class"));
        String stem = MobileCodegenNaming.screenStem("class");
        Assert.assertTrue(stem.toLowerCase().contains("class"));
        Assert.assertTrue(MobileCodegenNaming.screenClassName("class").endsWith("Screen")
                || MobileCodegenNaming.isValidJavaIdentifier(MobileCodegenNaming.screenClassName("class")));
    }

    @Test
    public void dumpBlankScreenKeyDetected() {
        Assert.assertTrue(MobileCodegenNaming.isDumpScreenKey(null));
        Assert.assertTrue(MobileCodegenNaming.isDumpScreenKey(""));
        Assert.assertTrue(MobileCodegenNaming.isDumpScreenKey("Screen"));
        Assert.assertFalse(MobileCodegenNaming.isDumpScreenKey("login"));
    }

    private static MobileCaseIr load(String resource) throws Exception {
        try (var in = MobileCodegenNamingTest.class.getClassLoader().getResourceAsStream(resource)) {
            Assert.assertNotNull(in, resource);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return MobileIrCodec.parse(json);
        }
    }
}
