package delivery.portal.security;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class ProductionSafetyGuardTest {

    @Test
    public void productionRejectsDefaultAdminPasswordAndLocalHttp() {
        List<String> violations = ProductionSafetyGuard.violations(new ProductionSafetyGuard.Config(
                true,
                "ChangeMeAdmin1!",
                "http://localhost:8080",
                true,
                "",
                "shared",
                "",
                false,
                ""));
        Assert.assertTrue(violations.stream().anyMatch(v -> v.toLowerCase().contains("admin password")));
        Assert.assertTrue(violations.stream().anyMatch(v -> v.toLowerCase().contains("https")));
        Assert.assertTrue(violations.stream().anyMatch(v -> v.toLowerCase().contains("h2 console")));
        Assert.assertTrue(violations.stream().anyMatch(v -> v.toLowerCase().contains("allowlist")));
        Assert.assertThrows(IllegalStateException.class,
                () -> ProductionSafetyGuard.requireSafe(new ProductionSafetyGuard.Config(
                        true, "ChangeMeAdmin1!", "https://keel.example", false,
                        "ollama", "shared", "secret", true, "")));
    }

    @Test
    public void developmentAllowsLocalDefaults() {
        List<String> violations = ProductionSafetyGuard.violations(new ProductionSafetyGuard.Config(
                false,
                "ChangeMeAdmin1!",
                "http://localhost:8080",
                false,
                "ollama",
                "shared",
                "",
                false,
                ""));
        Assert.assertTrue(violations.isEmpty());
    }

    @Test
    public void dedicatedCidrsDoNotApplyToShared() {
        List<String> violations = ProductionSafetyGuard.violations(new ProductionSafetyGuard.Config(
                true,
                "A-Strong-Admin-Pass9!",
                "https://keel.example.com",
                false,
                "ollama",
                "shared",
                "db-secret",
                true,
                "10.0.0.0/8"));
        Assert.assertTrue(violations.stream().anyMatch(v -> v.toLowerCase().contains("private-cidrs")));
    }
}
