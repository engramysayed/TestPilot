package delivery.job.port;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.engine.InProcessEnginePorts;
import delivery.portal.engine.InProcessJobContext;
import delivery.portal.model.JobRecord;
import keel.contract.runner.WireJobKind;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;

/** B05: JobContextPort / SiteMemoryPort seam and the shrunken ENGINE_NO_PORTAL allowlist. */
public class JobContextSeamTest {
    static final List<String> SEAMED = List.of(
            "delivery.hunt.HuntRuntimeFactory", "delivery.job.AuthoringJobRequestFiles", "delivery.job.DesignComparePhase",
            "delivery.job.DryRunExecuteService", "delivery.job.ExecuteJobRunner", "delivery.job.GenerateBatchJobRequest",
            "delivery.job.GenerateBatchJobRunner");

    @AfterMethod
    public void reset() {
        EnginePorts.override(null);
    }

    private static JobRecord job() {
        JobRecord job = new JobRecord("hunt_seam1", "prj_1", 7L, "HUNT", Path.of("suite.xlsx"),
                "https://example.test", "u", "p", false, JobRecord.JobKind.HUNT);
        job.setTenantId("ten_1");
        return job;
    }

    @Test
    public void inProcessContextMirrorsJobRecordAndCancel() {
        AtomicBoolean cancel = new AtomicBoolean();
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        JobContextPort ctx = InProcessJobContext.of(job(), props, cancel::get);
        assertEquals(ctx.jobId(), "hunt_seam1");
        assertEquals(ctx.projectId(), "prj_1");
        assertEquals(ctx.tenantId(), "ten_1");
        assertEquals(ctx.ownerUserId(), Long.valueOf(7L));
        assertEquals(ctx.kind(), WireJobKind.HUNT);
        assertEquals(ctx.baseUrl(), "https://example.test");
        assertEquals(ctx.settings().storeRoot(), Path.of(props.getStoreRoot()));
        assertFalse(ctx.cancelRequested());
        cancel.set(true);
        assertTrue(ctx.cancelRequested());
        for (JobRecord.JobKind k : JobRecord.JobKind.values()) {
            assertEquals(InProcessJobContext.wire(k).name(), k.name());
        }
    }

    @Test
    public void serviceLoaderFindsInProcessProviderAndOverrideWins() {
        assertTrue(EnginePorts.siteMemory() instanceof InProcessEnginePorts);
        AtomicBoolean used = new AtomicBoolean();
        EnginePorts.override(new SiteMemoryPort() {
            public Optional<Path> designReference(Path r, String tc) { used.set(true); return Optional.empty(); }
            public Optional<Path> pickActualScreenshot(Path e) { return Optional.empty(); }
            public delivery.job.ProvePhase.HealWorkbookApplier healWorkbookApplier(Path s) { return null; }
        });
        EnginePorts.siteMemory().designReference(Path.of("."), "TC");
        assertTrue(used.get());
    }

    @Test
    public void inProcessHealApplierNullWithoutStoreRoot() {
        assertNull(new InProcessEnginePorts().healWorkbookApplier(null));
        assertNotNull(new InProcessEnginePorts().healWorkbookApplier(Path.of("target/seam-store")));
    }

    @Test
    public void seamedWorkersNoLongerReachThePortal() throws Exception {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("delivery.job", "delivery.hunt");
        Set<String> bad = new TreeSet<>();
        for (JavaClass c : classes) {
            String origin = c.getName().replaceAll("\\$.*", "");
            c.getDirectDependenciesFromSelf().forEach(d -> {
                String t = d.getTargetClass().getName();
                if (t.equals("delivery.portal.service.PortalStore")) {
                    bad.add("PortalStore <- " + origin);
                }
                if (SEAMED.contains(origin) && t.startsWith("delivery.portal.")) {
                    bad.add(origin + " -> " + t);
                }
            });
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    public void allowlistEntriesForSeamedWorkersWereDeleted() throws Exception {
        String allow;
        try (InputStream in = getClass().getResourceAsStream("/arch/module-boundary-allowlist.txt")) {
            allow = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String c : SEAMED) {
            assertFalse(allow.contains("ENGINE_NO_PORTAL|" + c + "|"), "stale allowlist entry kept: " + c);
        }
    }
}
