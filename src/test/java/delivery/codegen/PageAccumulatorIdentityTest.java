package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.stream.Collectors;

public class PageAccumulatorIdentityTest {
    @Test
    public void facebookFormKeepsDistinctTypeMethods() {
        PageAccumulator acc = new PageAccumulator();
        acc.add(type("First name", "A"));
        acc.add(type("Surname", "B"));
        acc.add(selectCss("Select day", "15"));
        acc.add(selectCss("Select month", "Jan"));
        PageAccumulator.PageModel page = acc.pages().get("Reg");
        Set<String> names = page.methods().stream().map(m -> m.name()).collect(Collectors.toSet());
        Assert.assertEquals(names, Set.of(
                "type_First_Name",
                "type_Surname",
                "select_Select_Day",
                "select_Select_Month"
        ), String.valueOf(page.methods()));
    }

    private static ProvenStep type(String label, String value) {
        return new ProvenStep("TC_FB_REG_02", "Reg", "elementAction", "type",
                "xpath", "//input[@id=//label[normalize-space(.)='" + label + "']/@for]",
                value, "", "", true, "intent:TYPE_FIELD");
    }

    private static ProvenStep selectCss(String ariaLabel, String value) {
        return new ProvenStep("TC_FB_REG_02", "Reg", "elementAction", "select",
                "css", "div[aria-label='" + ariaLabel + "']",
                value, "", "", true, "intent:TYPE_FIELD");
    }
}
