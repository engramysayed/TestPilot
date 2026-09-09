package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CodeWriterTest {
    @Test
    public void writesPassedAndTodoClasses() throws Exception {
        Path temp = Files.createTempDirectory("codegen");
        Path templateRoot = Path.of("customer-framework-template");
        CodeWriter writer = new CodeWriter(templateRoot.resolve("templates"));
        ProvenStep step = new ProvenStep("TC_001", "Login", "elementAction", "click",
                "css", "button[data-test='login-button']", "", "", "", true, "ok");
        List<TcOutcome> outcomes = List.of(
                new TcOutcome("TC_001", "Valid login", TcStatus.PASSED, List.of(step), "", null, false, List.of()),
                new TcOutcome("TC_002", TcStatus.TODO, List.of(), "failed", null)
        );
        writer.write(temp, outcomes);
        Assert.assertTrue(Files.exists(temp.resolve("src/test/java/project/tests/generated/TC_001.java")));
        Assert.assertTrue(Files.exists(temp.resolve("src/test/java/project/tests/todo/TC_002Todo.java")));
        Assert.assertTrue(Files.exists(temp.resolve("src/main/java/project/pages/Login_Locators.java")));
        Assert.assertTrue(Files.exists(temp.resolve("src/main/java/project/pages/Login_Actions.java")));
        String java = Files.readString(temp.resolve("src/test/java/project/tests/generated/TC_001.java"));
        Assert.assertTrue(java.contains("import project.pages.Login_Actions;"), java);
        Assert.assertTrue(java.contains("Login_Actions login = new Login_Actions(driver)"), java);
        Assert.assertTrue(java.contains("@Test(description = \"TC_001 — Valid login\")"), java);
        Assert.assertTrue(java.contains("public void Valid_login()"), java);
        Assert.assertFalse(java.contains("new project.pages.Login"));
        Assert.assertFalse(java.contains("typeType_"));
        Assert.assertFalse(java.contains("clickClick_"));
    }

    @Test
    public void writesLoginBeforeMethodWhenFlagged() throws Exception {
        Path temp = Files.createTempDirectory("codegen-login");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep loginType = new ProvenStep("TC_L", "PracticeTestLogin", "elementAction", "type",
                "id", "username", "${TARGET_USERNAME}", "", "", true, "ok");
        ProvenStep loginClick = new ProvenStep("TC_L", "PracticeTestLogin", "elementAction", "click",
                "id", "submit", "", "", "", true, "ok");
        ProvenStep body = new ProvenStep("TC_L", "LoggedInSuccessfully", "elementAction", "assert",
                "xpath", "//body//*[contains(normalize-space(.),'Logged In Successfully')]",
                "", "textContains", "Logged In Successfully", true, "intent:ASSERT_VISIBLE");
        TcOutcome outcome = new TcOutcome(
                "TC_L", "Login with valid credentials", TcStatus.PASSED, List.of(body), "", null,
                true, List.of(loginType, loginClick));
        writer.write(temp, List.of(outcome));
        String java = Files.readString(temp.resolve("src/test/java/project/tests/generated/TC_L.java"));
        Assert.assertTrue(java.contains("TARGET_USERNAME"), java);
        Assert.assertTrue(java.contains("PracticeTestLogin_Actions"), java);
        Assert.assertTrue(java.contains("type_Username"), java);
        Assert.assertTrue(java.contains("click_Submit_Button"), java);
        Assert.assertTrue(java.contains("assert_Logged_In_Successfully_Is_Visible"), java);
        Assert.assertTrue(Files.exists(temp.resolve(
                "src/main/java/project/pages/PracticeTestLogin_Actions.java")));
        Assert.assertFalse(Files.exists(temp.resolve("src/main/java/project/pages/LoginPage.java")));
        Assert.assertFalse(java.contains("assertTextContainsAssert___body"));
    }

    @Test
    public void writesPartialTodoWithProvenStepsAndStopComment() throws Exception {
        Path temp = Files.createTempDirectory("codegen-partial");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep ok = new ProvenStep("TC_P", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        ProvenStep typed = new ProvenStep("TC_P", "CheckoutStepOne", "elementAction", "type",
                "data-test", "firstName", "Test", "", "", true, "auto-fill:type");
        TcOutcome outcome = new TcOutcome(
                "TC_P", "Checkout flow", TcStatus.TODO, List.of(ok, typed),
                "No DOM candidate for intent CLICK: Click Finish", null, false, List.of());
        writer.write(temp, List.of(outcome));
        String java = Files.readString(temp.resolve("src/test/java/project/tests/todo/TC_PTodo.java"));
        Assert.assertTrue(java.contains("STOPPED HERE"), java);
        Assert.assertTrue(java.contains("Click Finish"), java);
        Assert.assertFalse(java.contains("HEAL_EXHAUSTED"), java);
        Assert.assertTrue(java.contains("Cart_Actions cart"), java);
        Assert.assertTrue(java.contains("CheckoutStepOne_Actions checkoutStepOne"), java);
        Assert.assertTrue(java.contains("TC_P — Checkout flow"), java);
        Assert.assertTrue(Files.exists(temp.resolve("src/main/java/project/pages/Cart_Actions.java")));
    }

    @Test
    public void pageVarNameEscapesJavaKeywords() {
        Assert.assertEquals(CodeWriter.pageVarName("New_Actions"), "newPage");
        Assert.assertEquals(CodeWriter.pageVarName("NewPage_Actions"), "newPage");
        Assert.assertEquals(CodeWriter.pageVarName("Class_Actions"), "classPage");
        Assert.assertEquals(CodeWriter.pageVarName("Login_Actions"), "login");
    }

    @Test
    public void writesKeywordPageStemWithoutIllegalVarName() throws Exception {
        Path temp = Files.createTempDirectory("codegen-keyword");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep step = new ProvenStep("TC_K", "New", "elementAction", "type",
                "css", "[data-axis-test-id='first-name-input']", "Ramy", "", "", true, "ok");
        writer.write(temp, List.of(new TcOutcome(
                "TC_K", "Create user", TcStatus.PASSED, List.of(step), "", null, false, List.of())));
        String java = Files.readString(temp.resolve("src/test/java/project/tests/generated/TC_K.java"));
        Assert.assertTrue(java.contains("NewPage_Actions newPage = new NewPage_Actions(driver)"), java);
        Assert.assertFalse(java.contains("New_Actions new ="), java);
        Assert.assertTrue(java.contains("newPage.type_"), java);
        Assert.assertTrue(Files.exists(temp.resolve("src/main/java/project/pages/NewPage_Actions.java")));
    }

    @Test
    public void emitsAssertsInChronologicalOrder() throws Exception {
        Path temp = Files.createTempDirectory("codegen-order");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep assertTitle = new ProvenStep("TC_O", "Inventory", "elementAction", "assert",
                "data-test", "title", "", "visible", "", true, "intent:ASSERT_VISIBLE");
        ProvenStep clickCart = new ProvenStep("TC_O", "Inventory", "elementAction", "click",
                "data-test", "shopping-cart-link", "", "", "", true, "intent:CLICK");
        ProvenStep assertHeader = new ProvenStep("TC_O", "Cart", "elementAction", "assert",
                "data-test", "cart-contents", "", "visible", "", true, "intent:ASSERT_VISIBLE");
        writer.write(temp, List.of(new TcOutcome(
                "TC_O", "Order", TcStatus.PASSED, List.of(assertTitle, clickCart, assertHeader),
                "", null, false, List.of())));
        String java = Files.readString(temp.resolve("src/test/java/project/tests/generated/TC_O.java"));
        int a = java.indexOf("assert_");
        int c = java.indexOf("click_");
        int a2 = java.lastIndexOf("assert_");
        Assert.assertTrue(a >= 0 && c > a && a2 > c, "expected assert, click, assert order in:\n" + java);
    }

    @Test
    public void textContainsUsesBodyGetTextContains() throws Exception {
        Path temp = Files.createTempDirectory("codegen-textcontains");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep bodyAssert = new ProvenStep("TC_T", "Home", "elementAction", "assert",
                "xpath", "//body", "", "textContains", "Welcome back", true, "intent:ASSERT");
        ProvenStep idTextAssert = new ProvenStep("TC_T", "Home", "elementAction", "assert",
                "id", "toast-msg", "", "textContains", "Created", true, "intent:ASSERT");
        ProvenStep checked = new ProvenStep("TC_T", "Home", "elementAction", "assert",
                "id", "agree", "", "checked", "", true, "intent:ASSERT");
        ProvenStep unknown = new ProvenStep("TC_T", "Home", "elementAction", "assert",
                "id", "x", "", "weirdType", "", true, "intent:ASSERT");
        writer.write(temp, List.of(new TcOutcome(
                "TC_T", "Text", TcStatus.PASSED, List.of(bodyAssert, idTextAssert, checked, unknown),
                "", null, false, List.of())));
        String actions = Files.readString(temp.resolve("src/main/java/project/pages/Home_Actions.java"));
        Assert.assertTrue(actions.contains("bodyTextContains(\"Welcome back\")"), actions);
        Assert.assertTrue(actions.contains("textContains("), actions);
        Assert.assertFalse(actions.contains("By.tagName(\"body\")"), actions);
        Assert.assertTrue(actions.contains("elementSelected("), actions);
        Assert.assertTrue(actions.contains("public void assert_"), actions);
        Assert.assertFalse(actions.contains("return this;"), actions);
        Assert.assertTrue(actions.contains("unsupported assertionType: weirdType"), actions);
        Assert.assertTrue(actions.contains("softTrue(false"), actions);
    }

    @Test
    public void actionMethodsAreVoidNotFluent() throws Exception {
        Path temp = Files.createTempDirectory("codegen-void-actions");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep click = new ProvenStep("TC_V", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        writer.write(temp, List.of(new TcOutcome(
                "TC_V", "Click checkout", TcStatus.PASSED, List.of(click),
                "", null, false, List.of())));
        String actions = Files.readString(temp.resolve("src/main/java/project/pages/Cart_Actions.java"));
        Assert.assertTrue(actions.contains("public void click_"), actions);
        Assert.assertFalse(actions.contains("return this;"), actions);
    }
}
