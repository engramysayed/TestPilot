import delivery.codegen.*;
import delivery.ir.*;
import delivery.job.*;
import delivery.portal.*;
import delivery.portal.model.*;
import delivery.portal.persistence.*;
import delivery.portal.service.*;
import delivery.store.*;
import delivery.util.ProjectNaming;
import delivery.excel.*;
import delivery.hunt.*;
import drivers.WebDriverFactory;
import org.mockito.MockedConstruction;
import org.openqa.selenium.WebDriver;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;

/** Read-only audit of production behavior; all writes use a disposable temp directory.
 * These probes report observed defects, not a passing release acceptance suite.
 */
public class LaunchAuditProbe {
    static void observed(String id, boolean defect, String detail) {
        System.out.println(id + " | " + (defect ? "DEFECT REPRODUCED" : "NOT REPRODUCED") + " | " + detail);
    }
    static TcDraft draft(String id) {
        return new TcDraft(id, "Audit", "Click", "Visible", TcDraftStatus.TODO,
                List.of(), List.of(), false, 1, "Click", "blocked", "", 0, "");
    }
    static TcOutcome outcome(String id, String control, String value) {
        return new TcOutcome(id, "Audit " + id, TcStatus.PASSED, List.of(
                new ProvenStep(id, "Checkout", "elementAction", "type", "id", control,
                        value, "", "", true, "audit")), "", null, false, List.of());
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("keel-launch-audit-");
        Instant first = Instant.parse("2026-09-15T10:00:00.100Z");
        String a = ProjectNaming.fromBaseUrl("https://example.com/tenant-a", first);
        String b = ProjectNaming.fromBaseUrl("https://example.com/tenant-b", first.plusMillis(600));
        observed("F01", a.equals(b), "Distinct jobs within one second use identical work folder: " + a);

        TcDraftStore ir = new TcDraftStore(root.resolve("ir-probe"));
        ir.write(draft("TC/1")); ir.write(draft("TC_1"));
        observed("F05", ir.readAll().size() == 1, "Two distinct TC IDs persisted as " + ir.readAll().size() + " draft");

        Path code = root.resolve("code");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        writer.write(code, List.of(outcome("TC_A", "first-name", "Alice")));
        String oldPage = Files.readString(code.resolve("src/main/java/project/pages/Checkout_Actions.java"));
        writer.write(code, List.of(outcome("TC_B", "last-name", "Smith")));
        String page = Files.readString(code.resolve("src/main/java/project/pages/Checkout_Actions.java"));
        String data = Files.readString(code.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        observed("F04a", oldPage.contains("type_First_Name") && !page.contains("type_First_Name")
                && Files.exists(code.resolve("src/test/java/project/tests/generated/TC_A.java")),
                "Incremental emit retains TC_A but drops its page method");
        observed("F04b", !data.contains("TC_A.") && data.contains("TC_B."),
                "Incremental emit drops previous test-data keys");
        writer.write(code, List.of(new TcOutcome("TC_B", "Audit TC_B", TcStatus.TODO,
                List.of(), "blocked", null, false, List.of())));
        observed("F04c", Files.exists(code.resolve("src/test/java/project/tests/generated/TC_B.java"))
                && Files.exists(code.resolve("src/test/java/project/tests/todo/TC_BTodo.java")),
                "Demoting TC_B to TODO leaves old executable PASSED class");

        Path storeRoot = root.resolve("store");
        PreferredHooksStore.save(storeRoot, "https://example.com/account-a", "data-user-a");
        PreferredHooksStore.save(storeRoot, "https://example.com/account-b", "data-user-b");
        observed("F02", PreferredHooksStore.load(storeRoot, "https://example.com/account-a")
                .equals(List.of("data-user-b")), "Second account's domain settings overwrite first account's settings");

        Path oldRoot = DomainStorePaths.resolveProjectRoot(storeRoot, "https://old.example.com", "prj_a");
        Files.createDirectories(oldRoot);
        Files.writeString(oldRoot.resolve("project.json"), "{\"version\":1}");
        Path newRoot = DomainStorePaths.resolveProjectRoot(storeRoot, "https://new.example.com", "prj_a");
        observed("F06", !oldRoot.equals(newRoot) && !Files.exists(newRoot),
                "Changing base URL host resolves to an empty folder despite old project metadata");

        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(storeRoot.toString());
        JobRepository jobs = mock(JobRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        when(jobs.findByJobId(anyString())).thenReturn(Optional.empty());
        when(jobs.findByProjectId(anyString())).thenReturn(List.of());
        when(projects.findByProjectId(anyString())).thenReturn(Optional.empty());
        Path versions = storeRoot.resolve("prj_download/versions");
        Files.createDirectories(versions);
        Files.writeString(versions.resolve("v2.zip"), "audit-only fake newer package");
        PortalStore portal = new PortalStore(props, projects, jobs);
        JobRecord oldJob = new JobRecord("old-job", "prj_download", 1L, "NEW", root.resolve("missing.xlsx"),
                "https://example.com", "", "", false, JobRecord.JobKind.CONVERT);
        oldJob.setStatus(JobRecord.Status.COMPLETED);
        oldJob.setZipPath(root.resolve("expired-v1.zip"));
        observed("F07", portal.resolveZip(oldJob).orElseThrow().equals(versions.resolve("v2.zip")),
                "Expired old job returns v2.zip instead of its own package or an unavailable response");

        ManualTestCase blocked = new ManualTestCase("TC_BLOCKED", "Blocked case", "",
                "1. Click Submit", "Success is visible", "High", "");
        Path reuseStore = root.resolve("reuse-store");
        Path previousRoot = new ProjectStore(reuseStore, "https://example.com").projectRoot("prj_reuse");
        new TcDraftStore(previousRoot).write(draft(blocked.tcId()));
        Map<String, String> hashes = new TcDiffService().hashesOf(List.of(blocked));
        var diff = new TcDiffService().diff(List.of(blocked), hashes);
        ConversionJobRequest request = new ConversionJobRequest("prj_reuse", root.resolve("unused.xlsx"),
                "https://example.com", "", "", root, reuseStore,
                Path.of("customer-framework-template"), "UPDATE", "http://127.0.0.1:1", "unused");
        try (MockedConstruction<WebDriverFactory> ignored = mockConstruction(WebDriverFactory.class)) {
            var result = new ProvePhase(new JobProgressTracker()).prove(request, List.of(blocked),
                    diff.toAuthor().stream().map(ManualTestCase::tcId).collect(java.util.stream.Collectors.toSet()),
                    root.resolve("reuse-work"));
            observed("F03", result.get(0).status() == TcDraftStatus.REUSED
                    && EmitPhase.toOutcome(result.get(0)).status() == TcStatus.PASSED,
                    "Stored TODO with unchanged hash becomes REUSED/PASSED without browser proof");
        }

        WebDriver browser = mock(WebDriver.class);
        HuntActionExecutor executor = new HuntActionExecutor(browser);
        executor.setGuard(new HuntActionGuard(null, ""));
        var navigation = executor.executeOne(Map.of("type", "navigate", "url", "http://127.0.0.1:9876/audit"));
        verify(browser).get("http://127.0.0.1:9876/audit");
        observed("F08", "ok".equals(navigation.get("status")),
                "Navigation guard passes loopback URL to WebDriver (mock only; no request sent)");
        String syntheticSecret = "AUDIT_SYNTHETIC_PASSWORD";
        String slim = parsingLayer.HtmlSlimmer.slim("<input type='password' value='" + syntheticSecret + "'>", 80000);
        observed("F09", slim.contains(syntheticSecret),
                "Password value attribute survives HTML preparation used for planner context and hunt artifacts");
    }
}
