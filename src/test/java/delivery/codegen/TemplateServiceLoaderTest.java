package delivery.codegen;

import org.testng.Assert;
import org.testng.ITestNGListener;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The template's META-INF/services/org.testng.ITestNGListener (moved from the misnamed
 * META-INF.services/) really loads through ServiceLoader, and the resulting double registration
 * (ServiceLoader + BaseTest @Listeners) is harmless: one verdict line per test.
 */
public class TemplateServiceLoaderTest {
    static final Path RES = Path.of("customer-framework-template/src/main/resources");
    private URLClassLoader loader;
    private Path results;

    @BeforeClass
    public void compile() throws Exception {
        Path src = Files.createTempDirectory("sl-src").resolve("slsample");
        Files.createDirectories(src);
        Path sample = src.resolve("DoubleRegistered.java");
        Files.writeString(sample, "package slsample;\n"
                + "@org.testng.annotations.Listeners({project.listeners.TestNGListeners.class, project.utils.retry.KeelRetryListener.class})\n"
                + "public class DoubleRegistered { @org.testng.annotations.Test public void ok() { } }\n");
        Path classes = Files.createTempDirectory("sl-classes");
        loader = CustomerTemplateCompiler.compile(classes,
                Path.of("customer-framework-template/src/main/java/project/listeners/TestNGListeners.java"),
                Path.of("customer-framework-template/src/main/java/project/utils/retry/KeelRetryListener.java"),
                sample);
        Path svc = classes.resolve("META-INF/services");
        Files.createDirectories(svc);
        Files.copy(RES.resolve("META-INF/services/org.testng.ITestNGListener"), svc.resolve("org.testng.ITestNGListener"));
        results = Files.createTempDirectory("sl-results");
    }

    @AfterClass(alwaysRun = true)
    public void close() throws Exception {
        System.clearProperty("keel.results.dir");
        System.clearProperty("keel.testng.listeners");
        if (loader != null) loader.close();
    }

    @Test
    public void misnamedDirectoryIsGone() {
        Assert.assertFalse(Files.exists(RES.resolve("META-INF.services")), "META-INF.services must not exist");
        Assert.assertTrue(Files.isRegularFile(RES.resolve("META-INF/services/org.testng.ITestNGListener")));
    }

    @Test
    public void listenersLoadViaServiceLoader() {
        List<String> types = ServiceLoader.load(ITestNGListener.class, loader).stream()
                .map(p -> p.type().getName()).filter(n -> n.startsWith("project.")).sorted().toList();
        Assert.assertEquals(types, List.of("project.listeners.TestNGListeners", "project.utils.retry.KeelRetryListener"));
    }

    @Test
    public void doubleRegistrationRecordsEachTestOnce() throws Exception {
        System.setProperty("keel.results.dir", results.toString());
        System.setProperty("keel.testng.listeners", "off"); // keep report generation/dir cleaning out of this JVM
        TestNG tng = new TestNG();
        tng.setUseDefaultListeners(false);
        tng.setVerbose(0);
        tng.setServiceLoaderClassLoader(loader);
        tng.setTestClasses(new Class[]{loader.loadClass("slsample.DoubleRegistered")});
        tng.run();
        Assert.assertEquals(tng.getStatus(), 0);
        List<String> lines = Files.readAllLines(results.resolve("verdicts.jsonl"));
        Assert.assertEquals(lines.size(), 1, lines.toString());
        Assert.assertTrue(lines.get(0).contains("\"verdict\":\"PASSED\""), lines.toString());
    }
}
