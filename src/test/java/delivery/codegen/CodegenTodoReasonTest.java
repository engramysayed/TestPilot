package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CodegenTodoReasonTest {
    @Test
    public void stripsHealExhaustedPrefix() {
        String in = "HEAL_EXHAUSTED: No DOM candidate for intent CLICK: Click Finish\nmore junk";
        Assert.assertEquals(
                CodegenTodoReason.summarize(in, 120),
                "No DOM candidate for intent CLICK: Click Finish");
    }

    @Test
    public void blankReturnsDefault() {
        Assert.assertEquals(CodegenTodoReason.summarize("", 120), "conversion incomplete");
    }
}
