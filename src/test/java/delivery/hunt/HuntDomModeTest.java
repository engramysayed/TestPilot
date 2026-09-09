package delivery.hunt;

import delivery.authoring.DomCandidate;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class HuntDomModeTest {

    @DataProvider(name = "parseCases")
    public Object[][] parseCases() {
        return new Object[][]{
                {null, HuntDomMode.AUTO},
                {"", HuntDomMode.AUTO},
                {"  ", HuntDomMode.AUTO},
                {"auto", HuntDomMode.AUTO},
                {"AUTO", HuntDomMode.AUTO},
                {"map", HuntDomMode.MAP},
                {"MAP", HuntDomMode.MAP},
                {"slim", HuntDomMode.SLIM},
                {"SLIM", HuntDomMode.SLIM},
                {"bogus", HuntDomMode.AUTO},
                {" map ", HuntDomMode.MAP},
        };
    }

    @Test(dataProvider = "parseCases")
    public void parseNormalizesInput(String raw, HuntDomMode expected) {
        Assert.assertEquals(HuntDomMode.parse(raw), expected);
    }

    @DataProvider(name = "shouldIncludeSlimCases")
    public Object[][] shouldIncludeSlimCases() {
        HuntPageMap thin = thinMap(2);
        HuntPageMap rich = thinMap(HuntPageMapBuilder.THIN_CONTROL_THRESHOLD);
        return new Object[][]{
                {HuntDomMode.SLIM, null, true},
                {HuntDomMode.SLIM, thin, true},
                {HuntDomMode.SLIM, rich, true},
                {HuntDomMode.MAP, null, false},
                {HuntDomMode.MAP, thin, false},
                {HuntDomMode.MAP, rich, false},
                {HuntDomMode.AUTO, null, true},
                {HuntDomMode.AUTO, thin, true},
                {HuntDomMode.AUTO, rich, false},
        };
    }

    @Test(dataProvider = "shouldIncludeSlimCases")
    public void shouldIncludeSlimFollowsModeAndMap(HuntDomMode mode, HuntPageMap map, boolean expected) {
        Assert.assertEquals(HuntDomMode.shouldIncludeSlim(mode, map), expected);
    }

    private static HuntPageMap thinMap(int controlCount) {
        List<DomCandidate> controls = new ArrayList<>();
        for (int i = 0; i < controlCount; i++) {
            controls.add(new DomCandidate("c" + i, "id", "btn-" + i, "button", "Btn " + i));
        }
        return new HuntPageMap("https://ex/", "Page", List.of(), List.of(), controls, List.of());
    }
}
