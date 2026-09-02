package delivery.codegen;

import delivery.ir.TcDraft;
import delivery.ir.TcDraftStore;
import delivery.job.EmitCompileCheck;
import delivery.job.EmitPhase;
import delivery.job.TcOutcome;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Regression fixture: re-emit Facebook IR and prove distinct control identity in the customer package.
 * Skips cleanly when {@code delivery-store/facebook-com/prj_e9a9fc7313b2/ir} is absent.
 */
public class FacebookIrCodegenIdentityTest {
    private static final Path PROJECT_ROOT = Path.of("delivery-store/facebook-com/prj_e9a9fc7313b2");
    private static final Path IR_DIR = PROJECT_ROOT.resolve("ir");
    private static final Path FRAMEWORK = PROJECT_ROOT.resolve("framework");
    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");
    private static final Path TEMPLATE_README = Path.of("customer-framework-template/README.md");

    @Test
    public void facebookIrRegeneratesWithDistinctControlIdentity() throws Exception {
        if (!Files.isDirectory(IR_DIR)) {
            throw new SkipException("Facebook IR not present at " + IR_DIR.toAbsolutePath());
        }
        if (!Files.isDirectory(FRAMEWORK)) {
            throw new SkipException("Facebook framework not present at " + FRAMEWORK.toAbsolutePath());
        }

        TcDraftStore store = new TcDraftStore(PROJECT_ROOT);
        List<TcDraft> drafts = store.readAll();
        Assert.assertFalse(drafts.isEmpty(), "expected IR drafts under " + store.irDir());

        List<TcOutcome> outcomes = new ArrayList<>();
        for (TcDraft draft : drafts) {
            outcomes.add(EmitPhase.toOutcome(draft));
        }

        new CodeWriter(TEMPLATES).write(FRAMEWORK, outcomes);
        DomainCatalogWriter.write(FRAMEWORK, outcomes);

        if (Files.isRegularFile(TEMPLATE_README)) {
            Files.copy(TEMPLATE_README, FRAMEWORK.resolve("README.md"), StandardCopyOption.REPLACE_EXISTING);
        }

        String actions = Files.readString(FRAMEWORK.resolve("src/main/java/project/pages/Reg_Actions.java"));
        Assert.assertFalse(actions.contains("type_Type"), "Reg_Actions must not collapse type fields");
        Assert.assertTrue(actions.contains("type_First_Name"), actions);
        Assert.assertTrue(actions.contains("type_Surname"), actions);
        Assert.assertTrue(actions.contains("select_Gender"),
                "gender ordinal combobox should use intent field token, got methods in:\n" + actions);
        Assert.assertFalse(actions.contains("select_Combobox_4"), actions);
        Assert.assertFalse(actions.contains("select_Gender_Select"), actions);

        String test02 = Files.readString(
                FRAMEWORK.resolve("src/test/java/project/tests/generated/TC_FB_REG_02Test.java"));
        Assert.assertTrue(test02.contains("type_First_Name"), test02);
        Assert.assertTrue(test02.contains("type_Surname"), test02);
        Assert.assertFalse(test02.contains("type_Type("), test02);

        String props = Files.readString(
                FRAMEWORK.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        Assert.assertTrue(props.contains("First_Name"), props);
        Assert.assertTrue(props.contains("Surname"), props);

        EmitCompileCheck.runIfEnabled(FRAMEWORK);
    }
}
