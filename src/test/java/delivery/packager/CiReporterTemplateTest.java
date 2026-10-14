package delivery.packager;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** The opt-in PR report template: off by default, no secrets, shipped in the ZIP, summariser output. */
public class CiReporterTemplateTest {
    static final Path ROOT = Path.of("customer-framework-template");
    static final Path WORKFLOW = ROOT.resolve(".github/workflows/keel-pr-report.yml");
    static final Path SCRIPT = ROOT.resolve(".github/keel/summarize_results.py");

    @Test
    @SuppressWarnings("unchecked")
    public void workflowIsOptInAndSecretFree() throws Exception {
        String text = Files.readString(WORKFLOW);
        Map<String, Object> wf = new Yaml().load(text);
        Map<String, Object> jobs = (Map<String, Object>) wf.get("jobs");
        Assert.assertEquals(jobs.keySet(), java.util.Set.of("report"));
        Map<String, Object> job = (Map<String, Object>) jobs.get("report");
        Assert.assertEquals(job.get("if"), "${{ vars.KEEL_PR_REPORT == 'true' }}", "job must be gated by a repo variable");
        Assert.assertEquals(job.get("permissions"), Map.of("contents", "read", "pull-requests", "write"));
        Assert.assertEquals(wf.get("permissions"), Map.of("contents", "read"));
        Map<String, Object> on = (Map<String, Object>) wf.get(Boolean.TRUE) != null
                ? (Map<String, Object>) wf.get(Boolean.TRUE) : (Map<String, Object>) wf.get("on");
        Assert.assertTrue(on.containsKey("pull_request"));
        Assert.assertFalse(on.containsKey("pull_request_target"), "never pull_request_target");
        Assert.assertFalse(text.matches("(?s).*\\bsecrets\\.[A-Za-z_].*"), "must not reference secrets");
        Assert.assertFalse(text.matches("(?s).*(gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}).*"), "no embedded tokens");
        List<Map<String, Object>> steps = (List<Map<String, Object>>) job.get("steps");
        Map<String, Object> comment = steps.stream().filter(s -> "Sticky PR comment".equals(s.get("name"))).findFirst().orElseThrow();
        String cond = String.valueOf(comment.get("if"));
        Assert.assertTrue(cond.contains("vars.KEEL_PR_COMMENT == 'true'") && cond.contains("head.repo.full_name == github.repository"), cond);
        Assert.assertTrue(text.contains("<!-- keel-pr-report v1 -->"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void e2eWorkflowRunsOncePerDayAndCanBeTriggeredManually() throws Exception {
        Map<String, Object> wf = new Yaml().load(Files.readString(ROOT.resolve(".github/workflows/E2E.yml")));
        Map<String, Object> on = (Map<String, Object>) (wf.containsKey(Boolean.TRUE) ? wf.get(Boolean.TRUE) : wf.get("on"));
        Assert.assertTrue(on.containsKey("push") && on.containsKey("pull_request") && on.containsKey("workflow_dispatch"), on.toString());
        List<Map<String, Object>> schedule = (List<Map<String, Object>>) on.get("schedule");
        Assert.assertEquals(schedule, List.of(Map.of("cron", "0 21 * * *")));
        String doc = Files.readString(ROOT.resolve("docs/CI_E2E_WORKFLOW.md"));
        Assert.assertTrue(doc.contains("Disable workflow") && doc.contains("0 21 * * *"), doc);
        Assert.assertTrue(FrameworkPackager.shouldInclude("docs/CI_E2E_WORKFLOW.md"));
    }

    @Test
    public void templateFilesShipInTheCustomerZip() {
        Assert.assertTrue(FrameworkPackager.shouldInclude(".github/workflows/keel-pr-report.yml"));
        Assert.assertTrue(FrameworkPackager.shouldInclude(".github/keel/summarize_results.py"));
        Assert.assertTrue(FrameworkPackager.shouldInclude("docs/CI_PR_REPORT.md"));
        Assert.assertFalse(FrameworkPackager.shouldInclude("test-output/keel-results/verdicts.jsonl"));
    }

    private static java.util.List<String> python;

    /**
     * A real Python 3 launcher: python3, python, then "py -3", each must answer {@code --version} with
     * "Python 3.x" (exit 0) within 10 s. Rejects the Windows Store {@code python.exe} alias stub, which
     * prints nothing / "Python was not found" and exits non-zero. {@code -Dkeel.python=none} forces a skip;
     * {@code -Dkeel.python="<cmd> [args]"} forces a launcher.
     */
    static synchronized java.util.List<String> realPython() {
        if (python != null) {
            return python;
        }
        String forced = System.getProperty("keel.python", "").trim();
        java.util.List<java.util.List<String>> candidates = forced.isEmpty()
                ? java.util.List.of(java.util.List.of("python3"), java.util.List.of("python"), java.util.List.of("py", "-3"))
                : "none".equalsIgnoreCase(forced) ? java.util.List.of() : java.util.List.of(java.util.List.of(forced.split("\\s+")));
        for (java.util.List<String> cmd : candidates) {
            try {
                java.util.List<String> argv = new java.util.ArrayList<>(cmd);
                argv.add("--version");
                Process v = new ProcessBuilder(argv).redirectErrorStream(true).start();
                if (!v.waitFor(10, TimeUnit.SECONDS)) {
                    v.destroyForcibly();
                    continue;
                }
                String out = new String(v.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (v.exitValue() == 0 && out.matches("(?s)Python 3\\.\\d+.*")) {
                    python = cmd;
                    return python;
                }
            } catch (java.io.IOException | InterruptedException notThere) {
                // next candidate
            }
        }
        python = java.util.List.of();
        return python;
    }

    private static String summarise(Path surefire, Path keel) throws Exception {
        return summarise(surefire, keel, "utf-8");
    }

    /** {@code ioEncoding} is put in PYTHONIOENCODING (the test's override is utf-8). */
    private static String summarise(Path surefire, Path keel, String ioEncoding) throws Exception {
        java.util.List<String> py = realPython();
        if (py.isEmpty()) {
            throw new SkipException("no real Python 3 (python3 / python / py -3 --version did not print 'Python 3.x'; "
                    + "Windows Store alias stub is not accepted)");
        }
        java.util.List<String> argv = new java.util.ArrayList<>(py);
        argv.addAll(java.util.List.of(SCRIPT.toString(), "--surefire", surefire.toString(), "--keel", keel.toString()));
        ProcessBuilder pb = new ProcessBuilder(argv).redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", ioEncoding);
        pb.environment().remove("PYTHONUTF8");
        Process p = pb.start();
        Assert.assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Assert.assertEquals(p.exitValue(), 0, out);
        return out;
    }

    /**
     * Windows console regression: stdout in cp1252 (as on a German/US Windows console) used to crash with
     * UnicodeEncodeError on the status emoji. The script must reconfigure its own stdout to UTF-8.
     */
    @Test
    public void emojiSummarySurvivesCp1252Stdout() throws Exception {
        Path keel = Files.createTempDirectory("keel-results");
        Files.writeString(keel.resolve("verdicts.jsonl"),
                "{\"test\":\"p.T.pay\",\"verdict\":\"FAILED\",\"attempt\":1,\"readOnly\":false,\"error\":\"boom\"}\n");
        String md = summarise(Files.createTempDirectory("none"), keel, "cp1252");
        Assert.assertTrue(md.contains("\u274c failed"), md);
        Assert.assertFalse(md.contains("UnicodeEncodeError"), md);
    }

    @Test
    public void summaryShowsFlakyFailedAndFallbackWins() throws Exception {
        Path keel = Files.createTempDirectory("keel-results");
        Files.writeString(keel.resolve("verdicts.jsonl"), String.join("\n",
                "{\"test\":\"p.T.heading\",\"verdict\":\"RETRIED\",\"attempt\":1,\"readOnly\":true}",
                "{\"test\":\"p.T.heading\",\"verdict\":\"FLAKY\",\"attempt\":2,\"readOnly\":true}",
                "{\"test\":\"p.T.ok\",\"verdict\":\"PASSED\",\"attempt\":1,\"readOnly\":false}",
                "{\"test\":\"p.T.pay\",\"verdict\":\"FAILED\",\"attempt\":1,\"readOnly\":false,\"error\":\"AssertionError: <script>x|y</script>\"}",
                ""));
        Files.writeString(keel.resolve("locator-events.jsonl"), String.join("\n",
                "{\"locator\":\"payButton\",\"index\":1,\"target\":\"frame=#pay >> css=[id=\\\"pay\\\"]\"}",
                "{\"locator\":\"payButton\",\"index\":1,\"target\":\"frame=#pay >> css=[id=\\\"pay\\\"]\"}", ""));
        String md = summarise(Files.createTempDirectory("none"), keel);
        Assert.assertTrue(md.startsWith("<!-- keel-pr-report v1 -->"), md);
        Assert.assertTrue(md.contains("| 1 | 1 | 1 | 0 | 1 | 33% |"), md);
        Assert.assertTrue(md.contains("`p.T.heading` passed on attempt 2"), md);
        Assert.assertTrue(md.contains("&lt;script&gt;x&#124;y&lt;/script&gt;"), "escaped: " + md);
        Assert.assertTrue(md.contains("| `payButton` | 1 | `frame=#pay &gt;&gt; css=[id=&quot;pay&quot;]` | 2 |"), md);
    }

    @Test
    public void fallsBackToJUnitXmlAndToleratesMissingInput() throws Exception {
        Path sure = Files.createTempDirectory("surefire");
        Files.writeString(sure.resolve("TEST-x.xml"), "<testsuite name='x' tests='2' failures='1' errors='0' skipped='0'>"
                + "<testcase classname='p.T' name='a'/><testcase classname='p.T' name='b'><failure message='boom'/></testcase></testsuite>");
        String md = summarise(sure, Files.createTempDirectory("empty"));
        Assert.assertTrue(md.contains("| 2 | 1 | 0 | 0 |") && md.contains("`p.T.b`: boom"), md);
        String none = summarise(Path.of("does-not-exist"), Path.of("does-not-exist"));
        Assert.assertTrue(none.contains("No test results were found"), none);
    }
}
