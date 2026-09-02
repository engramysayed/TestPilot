package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestDataPropertiesWriterTest {
    @Test
    public void writesInventedValuesAndPropKeysInTests() throws Exception {
        Path dir = Files.createTempDirectory("testdata-emit");
        Path templates = Path.of("customer-framework-template/templates");
        ProvenStep type = new ProvenStep("TC_X", "CheckoutStepOne", "elementAction", "type",
                "id", "first-name", "Merna", "", "", true, "invent");
        ProvenStep click = new ProvenStep("TC_X", "CheckoutStepOne", "elementAction", "click",
                "id", "continue", "", "", "", true, "");
        TcOutcome outcome = new TcOutcome("TC_X", "Checkout", TcStatus.PASSED,
                List.of(type, click), "", null, false, List.of());
        new CodeWriter(templates).write(dir, List.of(outcome));
        Path props = dir.resolve("src/test/resources/test-data/delivery-testdata.properties");
        Assert.assertTrue(Files.isRegularFile(props), "missing " + props);
        String propText = Files.readString(props);
        Assert.assertTrue(propText.contains("Merna"), propText);
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_XTest.java"));
        Assert.assertFalse(test.contains("\"Merna\""), test);
        Assert.assertTrue(test.contains("PropertyReader.getProperty"), test);
    }

    @Test
    public void facebookFormEmitsDistinctPropKeys() throws Exception {
        Path dir = Files.createTempDirectory("testdata-identity");
        List<ProvenStep> steps = List.of(
                new ProvenStep("TC_FB_REG_02", "Reg", "elementAction", "type",
                        "xpath", "//input[@id=//label[normalize-space(.)='First name']/@for]",
                        "A", "", "", true, "intent:TYPE_FIELD"),
                new ProvenStep("TC_FB_REG_02", "Reg", "elementAction", "type",
                        "xpath", "//input[@id=//label[normalize-space(.)='Surname']/@for]",
                        "B", "", "", true, "intent:TYPE_FIELD"),
                new ProvenStep("TC_FB_REG_02", "Reg", "elementAction", "select",
                        "css", "div[aria-label='Select day']",
                        "15", "", "", true, "intent:TYPE_FIELD"),
                new ProvenStep("TC_FB_REG_02", "Reg", "elementAction", "select",
                        "css", "div[aria-label='Select month']",
                        "Jan", "", "", true, "intent:TYPE_FIELD")
        );
        TcOutcome outcome = new TcOutcome("TC_FB_REG_02", "Reg", TcStatus.PASSED, steps, "", null, false, List.of());
        TestDataPropertiesWriter.write(dir, List.of(outcome));
        Path props = dir.resolve("src/test/resources/test-data/delivery-testdata.properties");
        String propText = Files.readString(props);
        Assert.assertTrue(propText.contains("TC_FB_REG_02.type_First_Name=A"), propText);
        Assert.assertTrue(propText.contains("TC_FB_REG_02.type_Surname=B"), propText);
        Assert.assertTrue(propText.contains("TC_FB_REG_02.select_Select_Day=15"), propText);
        Assert.assertTrue(propText.contains("TC_FB_REG_02.select_Select_Month=Jan"), propText);
        long keyCount = propText.lines().filter(l -> l.startsWith("TC_FB_REG_02.")).count();
        Assert.assertEquals(keyCount, 4L, propText);
    }

    @Test
    public void bodyTextAssertDoesNotCreateLocatorField() {
        PageAccumulator acc = new PageAccumulator();
        acc.add(new ProvenStep("TC", "Inventory", "elementAction", "assert",
                "xpath",
                "//body//*[contains(normalize-space(.),'Products')]",
                "", "textContains", "Products", true, "x"));
        PageAccumulator.PageModel page = acc.pages().get("Inventory");
        Assert.assertEquals(page.fields().size(), 0, String.valueOf(page.fields()));
        Assert.assertEquals(page.assertions().size(), 1);
    }
}
