package delivery.it;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import delivery.runner.EngineAgent;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * B09 (SPLIT_DESIGN rev 10, isolation test item 4; OPS §12.5 item 6): the portal and the engine run as two processes
 * with separate data roots. The engine gets only the portal URL and a runner token; it must finish every remote kind
 * (EXECUTE, CONVERT) without touching the portal root, and its config must not mention portal storage.
 *
 * <p><b>CI only</b>: skipped unless {@code CI=true} (never on the company machine). On the Windows CI job the engine
 * root is put on another drive letter with {@code subst} and passed as {@code -Dkeel.it.engine-root=E:\engine}.
 * Run: {@code mvn -B -pl keel-portal -am test -Dtest=EngineIsolationIT -Dsurefire.failIfNoSpecifiedTests=false}.
 */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:engine-isolation-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/it-isolation/portal-root/store",
        "delivery.work-dir=./target/it-isolation/portal-root/work",
        "keel.runner.v2.chunk-dir=./target/it-isolation/portal-root/chunks"
})
public class EngineIsolationIT extends AbstractTestNGSpringContextTests {
    static final List<String> FORBIDDEN = List.of("store-root", "jdbc:", "delivery-store", "spring.datasource");

    @LocalServerPort int port;
    @Autowired PortalStore store;
    @Autowired GeneratedWorkbookService workbooks;
    final HttpClient http = HttpClient.newHttpClient();

    static boolean ciEnabled(Map<String, String> env) {
        return "true".equalsIgnoreCase(env.getOrDefault("CI", "").trim());
    }

    @BeforeClass(alwaysRun = true)
    public void onlyInCi() {
        if (!ciEnabled(System.getenv())) {
            throw new SkipException("EngineIsolationIT runs in CI only (CI=true); never on the company machine");
        }
    }

    private JSONObject call(String method, String path, String json, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Keel-Requested-With", "Keel").header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(b::header);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        Assert.assertTrue(r.statusCode() < 300, path + " -> " + r.statusCode() + " " + r.body());
        return new JSONObject(r.body());
    }

    private Map<String, String> admin() {
        return Map.of("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                "admin@testpilot.local:ChangeMeAdmin1!".getBytes(StandardCharsets.UTF_8)));
    }

    /** The engine's whole configuration: argv + KEEL_* env. Shared with the config grep below. */
    static List<String> engineCommand(String portalUrl, Path engineRoot) {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(EngineAgent.class.getName());
        cmd.add("--keel.engine.portal-url=" + portalUrl);
        cmd.add("--keel.engine.home=" + engineRoot);
        cmd.add("--keel.engine.template-root=" + repoRoot().resolve("customer-framework-template"));
        cmd.add("--keel.engine.dry-run=true");
        cmd.add("--keel.engine.poll-ms=200");
        return cmd;
    }

    static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p.getParent() != null && !Files.isDirectory(p.resolve("customer-framework-template"))) {
            p = p.getParent();
        }
        return p;
    }

    static List<String> configViolations(List<String> cmd, Map<String, String> env) {
        List<String> out = new ArrayList<>();
        List<String> all = new ArrayList<>(cmd.subList(4, cmd.size())); // skip java + classpath + main class
        env.forEach((k, v) -> all.add(k + "=" + v));
        for (String s : all) {
            for (String f : FORBIDDEN) {
                if (s.toLowerCase(Locale.ROOT).contains(f)) {
                    out.add(f + " in " + (s.startsWith("KEEL_RUNNER_TOKEN") ? "KEEL_RUNNER_TOKEN=***" : s));
                }
            }
        }
        return out;
    }

    @Test
    public void engineFinishesEveryRemoteKindFromItsOwnRootByUrlOnly() throws Exception {
        Path portalRoot = Path.of("target/it-isolation/portal-root").toAbsolutePath().normalize();
        Path engineRoot = Path.of(System.getProperty("keel.it.engine-root",
                Files.createTempDirectory("keel-engine-it").toString())).toAbsolutePath().normalize();
        Files.createDirectories(engineRoot);
        Assert.assertFalse(engineRoot.startsWith(portalRoot) || portalRoot.startsWith(engineRoot),
                "data roots must be disjoint: " + portalRoot + " vs " + engineRoot);

        String projectId = call("POST", "/api/projects", "{\"name\":\"Isolation\",\"baseUrl\":\"https://staging.example/\"}", admin())
                .getString("projectId");
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")), "test", "isolation");
        String token = call("POST", "/api/projects/" + projectId + "/runners", "{\"label\":\"engine\"}", admin()).getString("token");
        String svc = call("POST", "/api/projects/" + projectId + "/services", "{\"label\":\"ci\",\"role\":\"ADMIN\"}", admin()).getString("token");
        List<String> jobs = new ArrayList<>();
        jobs.add(call("POST", "/api/v1/projects/" + projectId + "/jobs", "{\"runner\":\"private\"}",
                Map.of("Authorization", "Bearer " + svc)).getString("jobId"));
        jobs.add(delivery.runner.EngineTestJobs.convertLike(store, jobs.get(0)));
        // every kind the 0a engine claims (EngineAgent.REMOTE_KINDS) must be covered here
        Assert.assertEquals(jobs.stream().map(id -> store.getJob(id).orElseThrow().getJobKind().name())
                .collect(java.util.stream.Collectors.toSet()), EngineAgent.REMOTE_KINDS.stream().map(Enum::name)
                .collect(java.util.stream.Collectors.toSet()));

        List<String> cmd = engineCommand("http://localhost:" + port, engineRoot);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(engineRoot.toFile())
                .redirectErrorStream(true).redirectOutput(engineRoot.resolve("engine.log").toFile());
        pb.environment().keySet().removeIf(k -> k.startsWith("KEEL_") || k.startsWith("DELIVERY_") || k.startsWith("SPRING_"));
        pb.environment().put("KEEL_RUNNER_TOKEN", token);
        Map<String, String> keelEnv = Map.of("KEEL_RUNNER_TOKEN", token);
        Assert.assertEquals(configViolations(cmd, keelEnv), List.of(), "engine config references portal storage");

        Process engine = pb.start();
        try {
            long deadline = System.currentTimeMillis() + 180_000;
            while (System.currentTimeMillis() < deadline && !jobs.stream().allMatch(this::finished)) {
                Assert.assertTrue(engine.isAlive(), "engine exited early: " + Files.readString(engineRoot.resolve("engine.log")));
                Thread.sleep(250);
            }
        } finally {
            engine.destroy();
            engine.waitFor();
        }
        String log = Files.readString(engineRoot.resolve("engine.log"));
        for (String jobId : jobs) {
            JobRecord job = store.getJob(jobId).orElseThrow();
            Assert.assertEquals(job.getStatus(), JobRecord.Status.COMPLETED, jobId + " " + job.getMessage() + "\n" + log);
            Assert.assertTrue(Files.isDirectory(engineRoot.resolve("work").resolve(jobId)), "engine worked in its own root");
            Assert.assertNotNull(job.getZipPath(), "artifact uploaded over HTTP");
            Assert.assertTrue(job.getZipPath().toAbsolutePath().normalize().startsWith(portalRoot), "artifact lands in portal root");
        }
        try (Stream<Path> files = Files.walk(engineRoot)) {
            List<String> leaks = files.map(p -> engineRoot.relativize(p).toString().replace(File.separatorChar, '/'))
                    .filter(p -> p.contains("delivery-store") || p.endsWith(".mv.db") || p.contains("portal-db"))
                    .toList();
            Assert.assertEquals(leaks, List.of(), "engine root holds portal storage");
        }
    }

    private boolean finished(String jobId) {
        JobRecord.Status s = store.getJob(jobId).map(JobRecord::getStatus).orElse(null);
        return s == JobRecord.Status.COMPLETED || s == JobRecord.Status.FAILED || s == JobRecord.Status.CANCELLED;
    }
}
