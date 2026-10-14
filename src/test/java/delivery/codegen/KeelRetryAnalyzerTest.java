package delivery.codegen;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.AllureResultsWriter;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.model.TestResultContainer;
import org.testng.Assert;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestListener;
import org.testng.ITestNGListener;
import org.testng.ITestResult;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs real TestNG suites against the customer-template retry analyzer + listener:
 * read-only assertion failure then pass => FLAKY (Allure flaky + label, log, verdicts.jsonl);
 * non-read-only and infrastructure failures are never retried; the budget is honoured.
 */
public class KeelRetryAnalyzerTest {
    private static final String PKG = "package rsample;\n"
            + "import org.testng.annotations.Test;\n"
            + "import project.utils.retry.KeelReadOnly;\n"
            + "import project.utils.retry.KeelRetryAnalyzer;\n";
    private static final Map<String, String> SAMPLES = Map.of(
            "FlakyReadOnly", "public class FlakyReadOnly { static int n;\n"
                    + "  @KeelReadOnly @Test(retryAnalyzer = KeelRetryAnalyzer.class)\n"
                    + "  public void heading() { if (n++ == 0) throw new AssertionError(\"text not yet rendered\"); } }",
            "Mutating", "public class Mutating { public static int n;\n"
                    + "  @Test(retryAnalyzer = KeelRetryAnalyzer.class)\n"
                    + "  public void submit() { if (n++ == 0) throw new AssertionError(\"first\"); } }",
            "Infra", "public class Infra { public static int n;\n"
                    + "  @KeelReadOnly @Test(retryAnalyzer = KeelRetryAnalyzer.class)\n"
                    + "  public void view() { n++; throw new org.openqa.selenium.WebDriverException(\"session deleted\"); } }",
            "GroupAlwaysFails", "public class GroupAlwaysFails { public static int n;\n"
                    + "  @Test(groups = KeelReadOnly.GROUP, retryAnalyzer = KeelRetryAnalyzer.class)\n"
                    + "  public void check() { n++; throw new org.openqa.selenium.TimeoutException(\"wait\"); } }",
            "CleanPass", "public class CleanPass {\n"
                    + "  @KeelReadOnly @Test(retryAnalyzer = KeelRetryAnalyzer.class)\n"
                    + "  public void ok() { } }");

    private URLClassLoader loader;
    private Path results;
    private final List<TestResult> written = new ArrayList<>();

    @BeforeClass
    public void compile() throws Exception {
        Path src = Files.createTempDirectory("rsample-src").resolve("rsample");
        Files.createDirectories(src);
        List<Path> files = new ArrayList<>();
        for (var e : SAMPLES.entrySet()) {
            Path f = src.resolve(e.getKey() + ".java");
            Files.writeString(f, PKG + e.getValue());
            files.add(f);
        }
        Path retry = Path.of("customer-framework-template/src/main/java/project/utils/retry");
        files.add(retry.resolve("KeelRetryListener.java"));
        loader = CustomerTemplateCompiler.compile(Files.createTempDirectory("rsample-classes"), files.toArray(Path[]::new));
        results = Files.createTempDirectory("keel-results");
        System.setProperty("keel.results.dir", results.toString());
        Allure.setLifecycle(new AllureLifecycle(new AllureResultsWriter() {
            public void write(TestResult r) { written.add(r); }
            public void write(TestResultContainer c) { }
            public void write(String s, InputStream in) { }
        }));
    }

    @AfterClass(alwaysRun = true)
    public void close() throws Exception {
        System.clearProperty("keel.results.dir");
        System.clearProperty("KEEL_RETRY_MAX");
        Allure.setLifecycle(new AllureLifecycle());
        if (loader != null) loader.close();
    }

    /** Minimal stand-in for allure-testng: starts a test case per attempt, writes it on finish. */
    static final class AllureHarness implements IInvokedMethodListener, ITestListener {
        final Map<ITestResult, String> uuids = new ConcurrentHashMap<>();
        public void beforeInvocation(IInvokedMethod m, ITestResult r) {
            if (!m.isTestMethod()) return;
            String id = UUID.randomUUID().toString();
            uuids.put(r, id);
            Allure.getLifecycle().scheduleTestCase(new TestResult().setUuid(id).setName(r.getMethod().getMethodName()));
            Allure.getLifecycle().startTestCase(id);
        }
        private void finish(ITestResult r, Status s) {
            String id = uuids.remove(r);
            if (id == null) return;
            Allure.getLifecycle().updateTestCase(id, t -> t.setStatus(s));
            Allure.getLifecycle().stopTestCase(id);
            Allure.getLifecycle().writeTestCase(id);
        }
        public void onTestSuccess(ITestResult r) { finish(r, Status.PASSED); }
        public void onTestFailure(ITestResult r) { finish(r, Status.FAILED); }
        public void onTestSkipped(ITestResult r) { finish(r, Status.SKIPPED); }
    }

    private List<String> run(String... classes) throws Exception {
        written.clear();
        Files.deleteIfExists(results.resolve("verdicts.jsonl"));
        TestNG tng = new TestNG();
        tng.setUseDefaultListeners(false);
        tng.setVerbose(0);
        List<Class<?>> cls = new ArrayList<>();
        for (String c : classes) cls.add(loader.loadClass("rsample." + c));
        tng.setTestClasses(cls.toArray(Class[]::new));
        tng.addListener(new AllureHarness());
        tng.addListener((ITestNGListener) loader.loadClass("project.utils.retry.KeelRetryListener")
                .getDeclaredConstructor().newInstance());
        tng.run();
        Path v = results.resolve("verdicts.jsonl");
        return Files.exists(v) ? Files.readAllLines(v) : List.of();
    }

    private int count(String cls) throws Exception {
        return loader.loadClass("rsample." + cls).getDeclaredField("n").getInt(null);
    }

    @Test
    public void passAfterRetryIsFlakyNotPassed() throws Exception {
        List<String> v = run("FlakyReadOnly");
        Assert.assertEquals(v.size(), 2, v.toString());
        Assert.assertTrue(v.get(0).contains("\"verdict\":\"RETRIED\""), v.toString());
        Assert.assertTrue(v.get(1).contains("\"verdict\":\"FLAKY\"") && v.get(1).contains("\"attempt\":2"), v.toString());
        TestResult last = written.get(written.size() - 1);
        Assert.assertEquals(last.getStatus(), Status.PASSED);
        Assert.assertTrue(last.getStatusDetails().isFlaky(), "allure flaky flag");
        Assert.assertTrue(last.getLabels().stream().anyMatch(l -> "keel_verdict".equals(l.getName()) && "FLAKY".equals(l.getValue())));
        Assert.assertTrue(last.getSteps().stream().anyMatch(s -> s.getName().startsWith("FLAKY: ")), "allure step");
    }

    @Test
    public void cleanPassStaysPassed() throws Exception {
        List<String> v = run("CleanPass");
        Assert.assertEquals(v.size(), 1);
        Assert.assertTrue(v.get(0).contains("\"verdict\":\"PASSED\""), v.toString());
        Assert.assertFalse(written.get(0).getStatusDetails() != null && written.get(0).getStatusDetails().isFlaky());
    }

    @Test
    public void notReadOnlyIsNeverRetried() throws Exception {
        List<String> v = run("Mutating");
        Assert.assertEquals(count("Mutating"), 1);
        Assert.assertEquals(v.size(), 1);
        Assert.assertTrue(v.get(0).contains("\"verdict\":\"FAILED\"") && v.get(0).contains("\"readOnly\":false"), v.toString());
    }

    @Test
    public void infrastructureErrorsDoNotConsumeRetries() throws Exception {
        List<String> v = run("Infra");
        Assert.assertEquals(count("Infra"), 1);
        Assert.assertTrue(v.get(0).contains("\"verdict\":\"FAILED\"") && v.get(0).contains("session deleted"), v.toString());
    }

    @Test
    public void groupMarkerAndBudgetAreHonoured() throws Exception {
        System.setProperty("KEEL_RETRY_MAX", "2");
        try {
            List<String> v = run("GroupAlwaysFails");
            Assert.assertEquals(count("GroupAlwaysFails"), 3);  // 1 + 2 retries
            Assert.assertEquals(v.stream().filter(l -> l.contains("RETRIED")).count(), 2);
            Assert.assertTrue(v.get(v.size() - 1).contains("\"verdict\":\"FAILED\""), v.toString());
        } finally {
            System.clearProperty("KEEL_RETRY_MAX");
        }
    }
}
