package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CodegenNamingTest {
    @Test
    public void tokenFromLabelForXpath() {
        ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "type",
                "xpath", "//input[@id=//label[normalize-space(.)='First name']/@for]",
                "Merna", "", "", true, "intent:TYPE_FIELD");
        Assert.assertEquals(CodegenNaming.actionMethodName(s), "type_First_Name");
    }

    @Test
    public void tokenFromAriaLabelCss() {
        ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "select",
                "css", "div[aria-label='Select day']", "15", "", "", true, "intent:TYPE_FIELD");
        Assert.assertEquals(CodegenNaming.actionMethodName(s), "select_Select_Day");
    }

    @Test
    public void visibleAssertUsesLabelNotAssertVerb() {
        ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "assert",
                "xpath", "//input[@id=//label[normalize-space(.)='Surname']/@for]",
                "", "visible", "", true, "intent:ASSERT_VISIBLE");
        Assert.assertEquals(CodegenNaming.assertMethodName(s), "assert_Surname_Is_Visible");
    }

    @Test
    public void linkTextBecomesSignUpNotGeneric() {
        ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "click",
                "xpath",
                "//a[contains(normalize-space(.),'Sign up')][not(.//*[contains(normalize-space(.),'Sign up')])]",
                "", "", "", true, "intent:CLICK");
        Assert.assertTrue(CodegenNaming.actionMethodName(s).toLowerCase().contains("sign_up"));
    }

    @Test
    public void opaqueLocatorUsesControlHashNotActionVerb() {
        String locator = "//div[@class='unlabeled-opaque-control']";
        ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "type",
                "xpath", locator, "value", "", "", true, "");
        String expectedToken = "Control" + String.format("%08x", locator.hashCode());
        Assert.assertEquals(CodegenNaming.semanticToken(s), expectedToken);
        String method = CodegenNaming.actionMethodName(s);
        Assert.assertNotEquals(method, "type_Type", "must not fall back to action verb");
        Assert.assertEquals(method, "type_" + expectedToken);
    }

    @Test
    public void ordinalComboboxXpathBecomesComboboxN() {
        ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "select",
                "xpath", "(//div[@role='combobox'])[4]", "Female", "", "", true,
                "intent:TYPE_FIELD");
        Assert.assertEquals(CodegenNaming.actionMethodName(s), "select_Combobox_4");
    }
}
