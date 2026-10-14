package delivery.job;

import delivery.excel.ManualTestCase;
import delivery.ir.*;
import delivery.packager.FrameworkPackager;
import delivery.store.ProjectStore;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class HealthPublicationFenceTest {
    private record Fixture(ConversionJobRequest request, Path work, Path project, List<ManualTestCase> cases, ProjectStore store) {}
    private Fixture fixture() throws Exception {
        Path work=Files.createTempDirectory("health-emit");Path root=Files.createTempDirectory("health-emit-store");
        var req=new ConversionJobRequest("health",work.resolve("input.xlsx"),"https://example.invalid","","",work,root,
                Path.of("customer-framework-template"),"NEW","","",false,false);
        var tc=new ManualTestCase("TC_01","Health","","Open page","Page","P1","");
        new TcDraftStore(work).write(new TcDraft(tc.tcId(),tc.title(),tc.steps(),tc.expectedResult(),TcDraftStatus.TODO,
                List.of(),List.of(),false,-1,"","unproven","",0,""));
        Path project=work.resolve("project");new FrameworkPackager().copyTemplate(req.templateRoot(),project);
        var store=new ProjectStore(root,req.baseUrl());
        Files.createDirectories(store.projectRoot("health"));
        Files.writeString(store.projectRoot("health").resolve("tc-hashes.json"),"prior-hashes");
        return new Fixture(req,work,project,List.of(tc),store);
    }
    @Test public void cancelDuringCompileCannotReplaceProjectStateOrPublish() throws Exception {
        var f=fixture();var cancelled=new AtomicBoolean();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var emit=new EmitPhase(new JobProgressTracker()) {
            @Override protected void compileProject(Path dir, java.util.function.BooleanSupplier cancel) throws Exception {
                entered.countDown();Assert.assertTrue(release.await(10,TimeUnit.SECONDS));JobCancelSupport.checkCancelled(cancel);
            }
        };
        try(var pool=Executors.newSingleThreadExecutor()) {
            var result=pool.submit(()->emit.emit(f.request,f.cases,f.work,f.project,"work","NEW",cancelled::get,PublicationGuard.cancellable(cancelled::get)));
            Assert.assertTrue(entered.await(10,TimeUnit.SECONDS));cancelled.set(true);release.countDown();
            ExecutionException failure=Assert.expectThrows(ExecutionException.class,()->result.get(10,TimeUnit.SECONDS));
            Assert.assertTrue(failure.getCause() instanceof JobCancelledException);
        }
        assertUntouched(f);
    }
    @Test public void lostAttemptAtPublicationCannotWriteStagedIrOrFingerprints() throws Exception {
        var f=fixture();var emit=new EmitPhase(new JobProgressTracker()) {
            @Override protected void compileProject(Path dir, java.util.function.BooleanSupplier cancel) {}
        };
        PublicationGuard stale=new PublicationGuard() {
            public <T> T publish(Callable<T> action) throws Exception { throw new JobCancelledException(); }
        };
        Assert.expectThrows(JobCancelledException.class,()->emit.emit(f.request,f.cases,f.work,f.project,"work","NEW",()->false,stale));
        Assert.assertTrue(Files.isRegularFile(f.work.resolve("publication-state/ir/" + TcIdentity.storageKey("TC_01") + ".json")));
        assertUntouched(f);
    }
    private void assertUntouched(Fixture f) throws Exception {
        Path root=f.store.projectRoot("health");Assert.assertEquals(Files.readString(root.resolve("tc-hashes.json")),"prior-hashes");
        Assert.assertFalse(Files.exists(root.resolve("ir")));Assert.assertFalse(Files.exists(root.resolve("prove-context.json")));
        Assert.assertFalse(Files.exists(root.resolve("last-job.json")));Assert.assertEquals(f.store.load("health").version(),0);
        Assert.assertTrue(f.store.latestVersionZip("health").isEmpty());
    }
}
