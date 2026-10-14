package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LoginPageHelperCodegenTest {
    @Test
    public void emitsLoginHelperAndCollapsesBeforeMethod() throws Exception {
        Path temp = Files.createTempDirectory("login-helper");
        ProvenStep user = new ProvenStep("TC_L", "LoginPage", "elementAction", "type",
                "id", "user", "alice", "", "", true, "ok");
        ProvenStep pass = new ProvenStep("TC_L", "LoginPage", "elementAction", "type",
                "id", "pass", "secret", "", "", true, "ok").withSecret(true);
        ProvenStep submit = new ProvenStep("TC_L", "LoginPage", "elementAction", "click",
                "id", "submit", "", "", "", true, "ok");
        ProvenStep body = new ProvenStep("TC_L", "Inventory", "elementAction", "click",
                "id", "item", "", "", "", true, "ok");
        new CodeWriter(Path.of("customer-framework-template/templates")).write(temp, List.of(
                new TcOutcome("TC_L", "Login flow", TcStatus.PASSED, List.of(body), "", null, true,
                        List.of(user, pass, submit), List.of())));
        String page = Files.readString(temp.resolve("src/main/java/project/pages/LoginPage.java"));
        Assert.assertTrue(page.contains("public void login(String username, String password)"), page);
        Assert.assertTrue(page.contains("type_User(username)") || page.contains("type_"), page);
        String test = Files.readString(temp.resolve("src/test/java/project/tests/generated/TC_L.java"));
        Assert.assertTrue(test.contains(".login("), test);
        Assert.assertFalse(test.contains("type_User(") && test.contains("click_Submit"),
                "login chron should collapse to login():\n" + test);
    }

    @Test
    public void baseTestExposesLoginIfConfiguredHook() throws Exception {
        String base = Files.readString(Path.of(
                "customer-framework-template/src/test/java/project/tests/BaseTest.java"));
        Assert.assertTrue(base.contains("loginIfConfigured()"), base);
        Assert.assertTrue(base.contains("protected void loginIfConfigured()"), base);
    }
}
