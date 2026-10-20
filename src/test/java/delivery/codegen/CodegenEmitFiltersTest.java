package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CodegenEmitFiltersTest {

    @Test
    public void dropsEchoHtmlAndFieldAcceptAsserts() {
        ProvenStep echo = new ProvenStep("TC_1", "LoginPage", "elementAction", "assert",
                "css", "html", "", "visible", "", true, "intent:ASSERT_VISIBLE:echo");
        ProvenStep field = new ProvenStep("TC_1", "LoginPage", "elementAction", "assert",
                "css", "input[name=u]", "", "visible", "", true, "intent:ASSERT_VISIBLE:field=Username");
        ProvenStep click = new ProvenStep("TC_1", "LoginPage", "elementAction", "click",
                "css", "button", "", "", "", true, "intent:CLICK");
        Assert.assertFalse(CodegenEmitFilters.emitStep(echo));
        Assert.assertFalse(CodegenEmitFilters.emitStep(field));
        Assert.assertTrue(CodegenEmitFilters.emitStep(click));
    }
}
