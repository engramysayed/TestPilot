package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class HuntOracleLoginFeatureTest {
    @Test
    public void skipsUnexpectedLoginUrlWhenLoginFeature() {
        HuntOracle.PageSignals page = new HuntOracle.PageSignals(
                "https://ex/app/home",
                200,
                List.of("https://ex/app/home"),
                "happy",
                true,
                true);
        // Force URL to login with prior non-login visit
        page = new HuntOracle.PageSignals(
                "https://ex/login",
                200,
                List.of("https://ex/app/dashboard"),
                "happy",
                true,
                true);
        var bugs = HuntOracle.collect(1, List.of(), List.of(), List.of(), "repro", List.of(), page);
        Assert.assertTrue(bugs.stream().noneMatch(b ->
                String.valueOf(b.get("title")).toLowerCase().contains("unexpected redirect")), bugs.toString());
    }

    @Test
    public void stillFlagsUnexpectedLoginWhenNotLoginFeature() {
        HuntOracle.PageSignals page = new HuntOracle.PageSignals(
                "https://ex/login",
                200,
                List.of("https://ex/app/dashboard"),
                "happy",
                true,
                false);
        var bugs = HuntOracle.collect(1, List.of(), List.of(), List.of(), "repro", List.of(), page);
        Assert.assertTrue(bugs.stream().anyMatch(b ->
                String.valueOf(b.get("title")).toLowerCase().contains("unexpected redirect")), bugs.toString());
    }
}
