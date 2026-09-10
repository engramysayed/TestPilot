package delivery.job;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

public class LoginSecretResolverTest {

    @Test
    public void bodyLoginStepsResolveProfileNotLiteralToken() {
        ConversionJobRequest request = request("axis.user", "AxisPass1!");
        List<ProvenStep> body = List.of(
                step("type", "${TARGET_USERNAME}"),
                step("type", "${TARGET_PASSWORD}"),
                step("click", "")
        );
        List<ProvenStep> live = LoginSecretResolver.resolveForLive(body, request);
        Assert.assertEquals(live.get(0).value(), "axis.user");
        Assert.assertEquals(live.get(1).value(), "AxisPass1!");
        Assert.assertEquals(body.get(0).value(), "${TARGET_USERNAME}",
                "IR/codegen copy must keep the token");
    }

    @Test
    public void blankProfileDoesNotTypeTheToken() {
        ConversionJobRequest request = request("", "");
        List<ProvenStep> live = LoginSecretResolver.resolveForLive(
                List.of(step("type", "${TARGET_USERNAME}")), request);
        Assert.assertEquals(live.get(0).value(), "");
    }

    private static ConversionJobRequest request(String user, String pass) {
        return new ConversionJobRequest(
                "p1", Path.of("x.xlsx"), "https://example.com",
                user, pass, Path.of("work"), Path.of("store"), Path.of("tpl"),
                "NEW", "http://127.0.0.1:11434", "model");
    }

    private static ProvenStep step(String action, String value) {
        return new ProvenStep(
                "TC1", "Login", "elementAction", action,
                "id", "username", value,
                "", "", true, "test", "");
    }
}
