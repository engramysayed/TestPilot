package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source-text smoke: Assertion.java contains helper method signatures — no Selenium. */
public class AssertionHelperSignatureTest {

    private static final Path ASSERTION_JAVA = Path.of(
            "customer-framework-template/src/main/java/project/validations/Assertion.java");

    @Test
    public void assertionJavaContainsHelperSignatures() throws Exception {
        String source = Files.readString(ASSERTION_JAVA);
        Assert.assertTrue(source.contains("void textContains"), "missing textContains");
        Assert.assertTrue(source.contains("void bodyTextContains"), "missing bodyTextContains");
        Assert.assertTrue(source.contains("void urlContains"), "missing urlContains");
        Assert.assertTrue(source.contains("void elementSelected"), "missing elementSelected");
        Assert.assertTrue(source.contains("void elementUnchecked"), "missing elementUnchecked");
        Assert.assertTrue(source.contains("void elementNotVisible"), "missing elementNotVisible");
    }
}
