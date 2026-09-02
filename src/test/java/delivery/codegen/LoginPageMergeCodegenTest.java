package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LoginPageMergeCodegenTest {
    @Test
    public void loginPreludeAndBodyShareOnePageClass() throws Exception {
        Path temp = Files.createTempDirectory("codegen-login-merge");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep preludeUser = new ProvenStep("TC1", "LoginForm", "elementAction", "type",
                "id", "username", "u", "", "", true, "ok");
        ProvenStep preludePass = new ProvenStep("TC1", "LoginForm", "elementAction", "type",
                "id", "password", "p", "", "", true, "ok");
        ProvenStep preludeClick = new ProvenStep("TC1", "LoginForm", "elementAction", "click",
                "cssSelector", "button[type='submit']", "", "", "", true, "ok");
        ProvenStep bodyUser = new ProvenStep("TC2", "Login", "elementAction", "type",
                "id", "username", "u", "", "", true, "ok");
        ProvenStep bodyPass = new ProvenStep("TC2", "Login", "elementAction", "type",
                "id", "password", "bad", "", "", true, "ok");
        ProvenStep bodyClick = new ProvenStep("TC2", "Login", "elementAction", "click",
                "id", "login", "", "", "", true, "ok");
        List<ProvenStep> login = List.of(preludeUser, preludePass, preludeClick).stream()
                .map(s -> s.withPageName(PageNameNormalizer.canonical(s.pageName(), "FormAuthentication")))
                .toList();
        List<ProvenStep> body = List.of(bodyUser, bodyPass, bodyClick).stream()
                .map(s -> s.withPageName(PageNameNormalizer.canonical(s.pageName(), "FormAuthentication")))
                .toList();
        writer.write(temp, List.of(
                new TcOutcome("TC1", "ok", TcStatus.PASSED, List.of(), "", null, true, login),
                new TcOutcome("TC2", "bad", TcStatus.TODO, body, "x", null, false, List.of())
        ));
        Assert.assertTrue(Files.exists(temp.resolve(
                "src/main/java/project/pages/FormAuthentication_Actions.java")));
        Assert.assertFalse(Files.exists(temp.resolve("src/main/java/project/pages/Login_Actions.java")));
        Assert.assertFalse(Files.exists(temp.resolve("src/main/java/project/pages/LoginForm_Actions.java")));
        String loc = Files.readString(temp.resolve(
                "src/main/java/project/pages/FormAuthentication_Locators.java"));
        Assert.assertTrue(loc.contains("button[type='submit']"), loc);
        Assert.assertFalse(loc.contains("By.id(\"login\")"), loc);
    }
}
