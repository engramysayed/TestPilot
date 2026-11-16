package delivery.packager;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regression: customer-framework-template POM must be valid and not gain duplicate deps via copyTemplate. */
public class CustomerFrameworkTemplatePomTest {

    private static final Path TEMPLATE_ROOT = Path.of("customer-framework-template").toAbsolutePath().normalize();
    private static final Pattern ALLURE_ENV_WRITER =
            Pattern.compile("<artifactId>\\s*allure-environment-writer\\s*</artifactId>");

    @Test
    public void templatePom_hasSingleAllureEnvironmentWriter() throws Exception {
        String pom = Files.readString(TEMPLATE_ROOT.resolve("pom.xml"));
        Assert.assertEquals(count(ALLURE_ENV_WRITER, pom), 1,
                "template pom must declare allure-environment-writer exactly once");
        Assert.assertTrue(pom.contains("io.qameta.allure"), "allure-testng must remain");
        Assert.assertTrue(pom.contains("aspectjweaver"), "aspectjweaver must remain for Allure listener");
    }

    @Test
    public void copyTemplate_pomMatchesTemplate_noDuplicateInjection() throws Exception {
        Path dest = Files.createTempDirectory("template-pom-copy-");
        Path project = dest.resolve("project");
        new FrameworkPackager().copyTemplate(TEMPLATE_ROOT, project);
        String packaged = Files.readString(project.resolve("pom.xml"));
        String template = Files.readString(TEMPLATE_ROOT.resolve("pom.xml"));
        Assert.assertEquals(packaged, template, "packager must copy pom verbatim");
        Assert.assertEquals(count(ALLURE_ENV_WRITER, packaged), 1);
    }

    @Test
    public void templatePom_mavenValidatePasses() throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = new ProcessBuilder(
                windows ? "mvn.cmd" : "mvn",
                "-q",
                "-f",
                TEMPLATE_ROOT.resolve("pom.xml").toString(),
                "validate");
        pb.directory(TEMPLATE_ROOT.toFile());
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()) + new String(p.getErrorStream().readAllBytes());
        int code = p.waitFor();
        Assert.assertEquals(code, 0, "mvn validate on template pom failed:\n" + out);
    }

    private static int count(Pattern pattern, String text) {
        int n = 0;
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            n++;
        }
        return n;
    }
}