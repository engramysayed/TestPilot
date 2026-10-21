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

    @Test
    public void pageStemEscapesJavaKeywords() {
        Assert.assertEquals(CodegenNaming.pageStem("New"), "NewPage");
        Assert.assertEquals(CodegenNaming.pageStem("class"), "ClassPage");
        Assert.assertEquals(CodegenNaming.pageClassName("New"), "New_Page");
        Assert.assertEquals(CodegenNaming.pageClassName("NewPage"), "New_Page");
    }

    @Test
    public void pageStemMapsOpaqueHexToShortPageHash() {
        String stem = CodegenNaming.pageStem("be4f4d1cbc76453db3e06854ad5f4a64");
        Assert.assertTrue(stem.startsWith("Page_"), stem);
        Assert.assertTrue(stem.matches("Page_[A-Fa-f0-9]{8}"), stem);
        Assert.assertEquals(CodegenNaming.pageStem("OperationsUsers"), "OperationsUsers");
    }

    @Test
    public void safeLocalVarNameEscapesKeywords() {
        Assert.assertEquals(CodegenNaming.safeLocalVarName("New"), "newPage");
        Assert.assertEquals(CodegenNaming.safeLocalVarName("New_Actions"), "newPage");
        Assert.assertEquals(CodegenNaming.safeLocalVarName("NewPage"), "newPage");
        Assert.assertEquals(CodegenNaming.safeLocalVarName("Login"), "login");
        Assert.assertEquals(CodegenNaming.safeLocalVarName("Class"), "classPage");
    }

    @Test
    public void sanitizeJavaIdentifierEscapesKeywordsAndLeadingDigits() {
        Assert.assertEquals(CodegenNaming.sanitizeJavaIdentifier("new", "el"), "new_");
        Assert.assertEquals(CodegenNaming.sanitizeJavaIdentifier("1abc", "el"), "el_1abc");
        Assert.assertEquals(CodegenNaming.sanitizeJavaIdentifier("firstName_Txt_Locator", "el"),
                "firstName_Txt_Locator");
    }

    @Test
    public void validTcIdsArePreservedExactly() {
        Assert.assertEquals(CodegenNaming.testClassName("TC_01", "", true), "TC_01Test");
        Assert.assertEquals(CodegenNaming.testClassName("TC_06", "", false), "TC_06TestTodo");
        Assert.assertEquals(CodegenNaming.testClassName("TC_01", "LoginPage", true), "Login_Test");
        Assert.assertEquals(CodegenNaming.testClassName("TC_01", "Inventory", true), "Inventory_Test");
        Assert.assertEquals(CodegenNaming.tcIdToClassName("TC_A_B"), "TC_A_B");
        Assert.assertEquals(CodegenNaming.tcIdToClassName("TC_A__B"), "TC_A__B");
    }

    @Test
    public void pageClassUsesPageSuffixFromPathTitle() {
        Assert.assertEquals(CodegenNaming.pageTitleStem("LoginPage"), "Login");
        Assert.assertEquals(CodegenNaming.pageClassName("LoginPage"), "Login_Page");
        Assert.assertEquals(CodegenNaming.pageClassNameFromStem("Inventory"), "Inventory_Page");
    }

    @Test
    public void isValidJavaIdentifierRejectsKeywords() {
        Assert.assertTrue(CodegenNaming.isValidJavaIdentifier("login"));
        Assert.assertTrue(CodegenNaming.isValidJavaIdentifier("newPage"));
        Assert.assertFalse(CodegenNaming.isValidJavaIdentifier("new"));
        Assert.assertFalse(CodegenNaming.isValidJavaIdentifier("1bad"));
        Assert.assertFalse(CodegenNaming.isValidJavaIdentifier(""));
    }
}
