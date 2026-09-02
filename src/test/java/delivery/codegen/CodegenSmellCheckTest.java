package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CodegenSmellCheckTest {
    @Test
    public void collapsedTypeNameThrows() {
        PageAccumulator acc = new PageAccumulator();
        acc.add(type("First name", "A"));
        PageAccumulator.PageModel page = acc.pages().get("Reg");
        page.methods().add(new PageAccumulator.MethodModel("type_Type", "type", "firstName_Txt_Locator", true));

        IllegalStateException ex = Assert.expectThrows(IllegalStateException.class, () -> CodegenSmellCheck.verify(acc.pages()));
        Assert.assertTrue(ex.getMessage().contains("type_Type"), ex.getMessage());
    }

    @Test
    public void collapsedAssertNameThrows() {
        PageAccumulator acc = new PageAccumulator();
        acc.add(type("First name", "A"));
        PageAccumulator.PageModel page = acc.pages().get("Reg");
        page.assertions().add(new PageAccumulator.AssertionModel(
                "assert_Assert_Is_Visible", "field_Lbl_Locator", "visible", ""));

        IllegalStateException ex = Assert.expectThrows(IllegalStateException.class, () -> CodegenSmellCheck.verify(acc.pages()));
        Assert.assertTrue(ex.getMessage().contains("assert_Assert_Is_Visible"), ex.getMessage());
    }

    @Test
    public void duplicateMethodNameThrows() {
        PageAccumulator acc = new PageAccumulator();
        acc.add(type("First name", "A"));
        PageAccumulator.PageModel page = acc.pages().get("Reg");
        page.methods().add(new PageAccumulator.MethodModel("type_First_Name", "type", "other_Txt_Locator", true));

        IllegalStateException ex = Assert.expectThrows(IllegalStateException.class, () -> CodegenSmellCheck.verify(acc.pages()));
        Assert.assertTrue(ex.getMessage().contains("duplicate method"), ex.getMessage());
        Assert.assertTrue(ex.getMessage().contains("type_First_Name"), ex.getMessage());
    }

    @Test
    public void facebookShapedPagePasses() {
        PageAccumulator acc = new PageAccumulator();
        acc.add(type("First name", "A"));
        acc.add(type("Surname", "B"));
        acc.add(selectCss("Select day", "15"));
        acc.add(selectCss("Select month", "Jan"));
        acc.add(visibleAssert("Surname"));

        CodegenSmellCheck.verify(acc.pages());
    }

    @Test
    public void smellFailureWritesNoActionsFiles() throws Exception {
        Path root = Files.createTempDirectory("codegen-smell-before-write");
        try {
            PageAccumulator acc = new PageAccumulator();
            acc.add(type("First name", "A"));
            acc.pages().get("Reg").methods().add(
                    new PageAccumulator.MethodModel("type_Type", "type", "x_Txt_Locator", true));

            CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
            Assert.expectThrows(IllegalStateException.class, () -> {
                CodegenSmellCheck.verify(acc.pages());
                writer.writeVerified(root, List.of(), acc);
            });

            Path actions = root.resolve("src/main/java/project/pages/Reg_Actions.java");
            Assert.assertFalse(Files.exists(actions), "must not write Actions after smell fail");
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void writeVerifiesBeforeEmitting() throws Exception {
        Path root = Files.createTempDirectory("codegen-write-verify-order");
        try {
            CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
            writer.write(root, List.of());
            Assert.assertFalse(Files.exists(
                    root.resolve("src/main/java/project/pages/Reg_Actions.java")));
        } finally {
            deleteTree(root);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            });
        }
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

    private static ProvenStep visibleAssert(String label) {
        return new ProvenStep("TC_FB_REG_02", "Reg", "elementAction", "assert",
                "xpath", "//input[@id=//label[normalize-space(.)='" + label + "']/@for]",
                "", "visible", "", true, "intent:ASSERT_VISIBLE");
    }
}
