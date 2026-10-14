package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** SettledActions util + thin smart/drag page methods (P1). */
public class SettledActionsContractTest {

    private static final Path SETTLED = Path.of(
            "customer-framework-template/src/main/java/project/utils/locators/SettledActions.java");
    private static final Path PAGE_FTL = Path.of(
            "customer-framework-template/templates/Page.java.ftl");

    @Test
    public void settledActionsWrapsAllureSettleSmartWaitReset() throws Exception {
        String src = Files.readString(SETTLED);
        Assert.assertTrue(src.contains("AllureSteps.run"), src);
        Assert.assertTrue(src.contains("Settle.after"), src);
        Assert.assertTrue(src.contains("SmartWait.untilActionable"), src);
        Assert.assertTrue(src.contains("KeelLocate.reset"), src);
        Assert.assertTrue(src.contains("public static void click("), src);
        Assert.assertTrue(src.contains("public static void type("), src);
        Assert.assertTrue(src.contains("public static void select("), src);
        Assert.assertTrue(src.contains("public static void drag("), src);
    }

    @Test
    public void pageFtlUsesSettledActionsForSmartAndDrag() throws Exception {
        String ftl = Files.readString(PAGE_FTL);
        Assert.assertTrue(ftl.contains("SettledActions.click"), ftl);
        Assert.assertTrue(ftl.contains("SettledActions.type"), ftl);
        Assert.assertTrue(ftl.contains("SettledActions.select"), ftl);
        Assert.assertTrue(ftl.contains("SettledActions.drag"), ftl);
        // No inlined Settle.after for action methods (assertions may still reset)
        Assert.assertFalse(ftl.contains("Settle.after("), ftl);
        Assert.assertFalse(ftl.contains("SmartWait.untilActionable"), ftl);
    }
}
