package delivery.agent.validation.contract;

import org.testng.Assert;
import org.testng.annotations.Test;

public class GeneratedJavaStructureTest {

    @Test
    public void inspectPage_countsLocatorsAndMethods() {
        String src = """
                public class Cart_Page {
                    protected final By cart_Btn_Locator = By.id("c");
                    public void click_Cart() {}
                    public void assert_Cart_Loaded() {}
                }
                """;
        var shape = GeneratedJavaStructure.inspectPage(src);
        Assert.assertEquals(shape.locatorFields(), 1);
        Assert.assertEquals(shape.actionMethods(), 1);
        Assert.assertEquals(shape.assertMethods(), 1);
    }

    @Test
    public void inspectTest_detectsStubAndUsages() {
        String src = """
                public class Login_Test {
                    private Login_Page loginPage;
                    @Test
                    public void empty() { }
                    @Test
                    public void real() {
                        loginPage = new Login_Page(driver);
                        loginPage.click();
                    }
                }
                """;
        var shape = GeneratedJavaStructure.inspectTest(src);
        Assert.assertEquals(shape.stubTestMethods(), java.util.List.of("empty"));
        Assert.assertEquals(shape.pageVarUsages(), 1);
    }
}