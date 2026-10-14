package delivery.portal.service;

import delivery.portal.PortalApplication;
import delivery.portal.model.*;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.api.v1.PublicJobApiController;
import delivery.portal.security.ServiceIdentityFilter;
import delivery.excel.ManualTestCase;
import delivery.env.*;
import delivery.identity.*;
import delivery.job.*;
import delivery.portal.jobs.DurableJobClaim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.nio.file.*;
import java.util.*;
import java.time.Instant;

@SpringBootTest(classes=PortalApplication.class, properties={
    "spring.datasource.url=jdbc:h2:mem:health-fixes;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.jpa.hibernate.ddl-auto=create-drop", "delivery.dry-run=true",
    "delivery.store-root=./target/test-health-fixes-store", "delivery.work-dir=./target/test-health-fixes-work"
})
public class HealthAdmissionRegressionTest extends AbstractTestNGSpringContextTests {
    @Autowired PortalStore store;
    @Autowired GeneratedWorkbookService books;
    @Autowired ProjectCredentialService credentials;
    @Autowired PortalUserRepository users;
    @Autowired PublicJobApiController api;
    @Autowired delivery.portal.persistence.JobRepository jobs;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @org.testng.annotations.BeforeMethod public void cancelPreviousJobs() {
        jobs.findAll().forEach(e -> store.requestCancel(e.getJobId()));
    }

    private ProjectRecord project() throws Exception {
        long owner = users.findAll().get(0).getId();
        var p = store.createProject("Health " + UUID.randomUUID(), "https://project.example", owner);
        books.saveFromCases(p.getProjectId(), List.of(new ManualTestCase("TC_01", "Health", "", "Open page", "Page", "P1", "")), "IMPORT", "test");
        return p;
    }
    private JobRecord job(ProjectRecord p) throws Exception {
        var j = new JobRecord("job_health_" + UUID.randomUUID(), p.getProjectId(), p.getOwnerUserId(), "EXECUTE",
                books.copyForJob(p.getProjectId()), p.getBaseUrl(), "", "", false, JobRecord.JobKind.EXECUTE);
        j.setTenantId(p.getTenantId()); j.setRequirePrivateRunner(true); return j;
    }
    @Test public void environmentFreezesOriginCredentialsProvidersAndEnforcesCaps() throws Exception {
        var p=project(); credentials.create(p.getProjectId(), "qa", "synthetic-user", "synthetic-secret");
        var revision=new EnvironmentStore(store.environmentRoot(p.getProjectId())).commit(null,
                new EnvironmentProfile("staging", "https://staging.example", "qa", "ollama", 2, 1));
        var first=job(p);first.setEnvironmentRevisionId(revision.id());store.saveJob(first);
        Assert.assertEquals(first.getBaseUrl(), "https://staging.example");
        Assert.assertEquals(first.getUsername(), "synthetic-user"); Assert.assertEquals(first.getPassword(), "synthetic-secret");
        Assert.assertEquals(first.getProviderAllowlistSnapshot(), "ollama");
        Assert.assertEquals(first.getInputSnapshotHash(), DurableJobClaim.hashInputs(first));
        var second=job(p);second.setEnvironmentRevisionId(revision.id());store.saveJob(second);
        var third=job(p);third.setEnvironmentRevisionId(revision.id());
        Assert.expectThrows(JobAdmissionException.class, () -> store.saveJob(third));
        var enrolled=store.enrollWorkspaceRunner(p.getProjectId(), p.getOwnerUserId(), "env-runner");
        var runner=store.directory().runner(TenantId.parse(p.getTenantId()), enrolled.id()).orElseThrow();
        Assert.assertTrue(store.claimNextForRunner(runner).isPresent());
        Assert.assertTrue(store.claimNextForRunner(runner).isEmpty(), "Environment running cap must hold");
    }
    @Test public void missingEnvironmentAndCredentialReferenceRejectWithoutAJob() throws Exception {
        var p=project();var j=job(p);j.setEnvironmentRevisionId("envrev_missing");
        Assert.expectThrows(JobAdmissionException.class, () -> store.saveJob(j));
        Assert.assertTrue(store.getJob(j.getJobId()).isEmpty());
        var revision=new EnvironmentStore(store.environmentRoot(p.getProjectId())).commit(null,
                new EnvironmentProfile("staging", "https://staging.example", "missing", "ollama", 0, 0));
        j.setEnvironmentRevisionId(revision.id());
        Assert.expectThrows(JobAdmissionException.class, () -> store.saveJob(j));
        Assert.assertTrue(store.getJob(j.getJobId()).isEmpty());
    }
    @Test public void corruptBudgetRejectsAndRepairAllowsAdmission() throws Exception {
        var p=project(); Path ledger=ScopePaths.tenantRoot(store.directory().storeRoot(), TenantId.parse(p.getTenantId())).resolve("budget.json");
        byte[] before=Files.exists(ledger)?Files.readAllBytes(ledger):null;
        try {
            Files.createDirectories(ledger.getParent());Files.writeString(ledger,"{broken");var j=job(p);
            JobAdmissionException e=Assert.expectThrows(JobAdmissionException.class, () -> store.saveJob(j));
            Assert.assertEquals(e.code(), "BUDGET_UNAVAILABLE");Assert.assertTrue(store.getJob(j.getJobId()).isEmpty());
        } finally { if(before==null) Files.deleteIfExists(ledger); else Files.write(ledger,before); }
    }

    @Test public void damagedEnvironmentRevisionCannotRedirectAdmission() throws Exception {
        var p=project();var revision=new EnvironmentStore(store.environmentRoot(p.getProjectId())).commit(null,
                new EnvironmentProfile("staging","https://staging.example","","ollama",0,0));
        Path bytes=store.environmentRoot(p.getProjectId()).resolve("revisions").resolve(revision.id()+".bin");
        Files.writeString(bytes,"{}");var j=job(p);j.setEnvironmentRevisionId(revision.id());
        Assert.expectThrows(JobAdmissionException.class,()->store.saveJob(j));
        Assert.assertTrue(store.getJob(j.getJobId()).isEmpty());
    }
    @Test public void originalRunnerAttemptIsRequiredForHeartbeatAndCompletion() throws Exception {
        var p=project();var j=job(p);store.saveJob(j);
        var a=store.enrollWorkspaceRunner(p.getProjectId(),p.getOwnerUserId(),"A");
        var b=store.enrollWorkspaceRunner(p.getProjectId(),p.getOwnerUserId(),"B");
        var tenant=TenantId.parse(p.getTenantId());
        var runnerA=store.directory().runner(tenant,a.id()).orElseThrow();var runnerB=store.directory().runner(tenant,b.id()).orElseThrow();
        var lease=store.claimNextForRunner(runnerA).orElseThrow();
        Assert.assertFalse(store.heartbeatRunnerLease(runnerB,lease.jobId(),lease.attemptId(),lease.cancelGeneration()));
        Assert.assertTrue(store.heartbeatRunnerLease(runnerA,lease.jobId(),lease.attemptId(),lease.cancelGeneration()));
        Assert.assertFalse(store.heartbeatRunnerLease(runnerA,lease.jobId(),"stale",lease.cancelGeneration()));
        Assert.expectThrows(SecurityException.class, () -> store.completeFromRunner(runnerA,lease.jobId(),JobRecord.Status.COMPLETED,1,0,"",null,"stale"));
        store.requestCancel(lease.jobId());
        Assert.assertFalse(store.heartbeatRunnerLease(runnerA,lease.jobId(),lease.attemptId(),lease.cancelGeneration()));
        store.completeFromRunner(runnerA,lease.jobId(),JobRecord.Status.COMPLETED,1,0,"",null,lease.attemptId());
        Assert.assertEquals(store.getJob(lease.jobId()).orElseThrow().getStatus(),JobRecord.Status.CANCELLED);
    }
    @Test public void publicApiRejectRetryAndReplayUseOneDurableJob() throws Exception {
        var p=project(); var svc=new WorkspaceDirectory.ServiceIdentity("test",TenantId.parse(p.getTenantId()),WorkspaceRole.ADMIN,"test",false);
        SecurityContextHolder.getContext().setAuthentication(new ServiceIdentityFilter.ServiceToken(svc));
        String old=System.getProperty("delivery.jobs.max-queued-per-tenant");
        var body=new PublicJobApiController.SubmitRequest("EXECUTE",null,null,"private");
        String key="health-key-"+UUID.randomUUID();
        try {
            System.setProperty("delivery.jobs.max-queued-per-tenant","0");
            Assert.expectThrows(JobAdmissionException.class, () -> api.submit(p.getProjectId(),key,body));
            System.setProperty("delivery.jobs.max-queued-per-tenant","1000");
            var accepted=api.submit(p.getProjectId(),key,body);Assert.assertEquals(accepted.getStatusCode().value(),202);
            String id=String.valueOf(((Map<?,?>)accepted.getBody()).get("jobId"));Assert.assertTrue(store.getJob(id).isPresent());
            // Replay must not depend on the current workbook or credentials.
            delivery.store.ProjectStore.deleteRecursive(books.generatedDir(p.getProjectId()));
            credentials.create(p.getProjectId(),"new-profile","new-user","new-secret");
            var replay=api.submit(p.getProjectId(),key,body);Assert.assertEquals(replay.getStatusCode().value(),200);
            Assert.assertEquals(((Map<?,?>)replay.getBody()).get("jobId"),id);
        } finally { if(old==null)System.clearProperty("delivery.jobs.max-queued-per-tenant");else System.setProperty("delivery.jobs.max-queued-per-tenant",old);SecurityContextHolder.clearContext(); }
    }
    @Test public void cancelledAndExpiredAttemptsCannotPublish() throws Exception {
        var p=project();var j=job(p);j.setRequirePrivateRunner(false);store.saveJob(j);
        var lease=store.beginWork(j.getJobId()).orElseThrow();
        store.requestCancel(j.getJobId());
        Assert.expectThrows(JobCancelledException.class, () -> store.publishAttempt(j.getJobId(),lease,()->"bad"));
        var next=job(p);next.setRequirePrivateRunner(false);store.saveJob(next);var current=store.beginWork(next.getJobId()).orElseThrow();
        next.setLeaseUntil(Instant.now().minusSeconds(1));
        Assert.expectThrows(JobCancelledException.class, () -> store.publishAttempt(next.getJobId(),current,()->"bad"));
    }

    @Test public void sameRunnerReclaimDoesNotAuthorizeItsOldAttempt() throws Exception {
        var p=project();var j=job(p);store.saveJob(j);
        var created=store.enrollWorkspaceRunner(p.getProjectId(),p.getOwnerUserId(),"reclaim");
        var runner=store.directory().runner(TenantId.parse(p.getTenantId()),created.id()).orElseThrow();
        var old=store.claimNextForRunner(runner).orElseThrow();
        var claimed=store.getJob(old.jobId()).orElseThrow();claimed.setLeaseUntil(Instant.now().minusSeconds(1));
        store.syncJobPersistence(claimed);store.reconcileExpiredLeases();
        var fresh=store.claimNextForRunner(runner).orElseThrow();Assert.assertNotEquals(fresh.attemptId(),old.attemptId());
        Assert.assertFalse(store.heartbeatRunnerLease(runner,old.jobId(),old.attemptId(),old.cancelGeneration()));
        Assert.expectThrows(SecurityException.class,()->store.markRunnerStage(runner,old.jobId(),"BROWSER",old.attemptId()));
        Assert.expectThrows(SecurityException.class,()->store.storeRunnerArtifact(runner,old.jobId(),new byte[]{1},old.attemptId()));
        Assert.expectThrows(SecurityException.class,()->store.completeFromRunner(runner,old.jobId(),JobRecord.Status.COMPLETED,1,0,"",null,old.attemptId()));
        Assert.assertTrue(store.heartbeatRunnerLease(runner,fresh.jobId(),fresh.attemptId(),fresh.cancelGeneration()));
    }

    @Test public void publicSubmissionPreservesMissingWorkbookErrorAndRetry() throws Exception {
        var p=project();var svc=new WorkspaceDirectory.ServiceIdentity("test",TenantId.parse(p.getTenantId()),WorkspaceRole.ADMIN,"test",false);
        SecurityContextHolder.getContext().setAuthentication(new ServiceIdentityFilter.ServiceToken(svc));
        String key="empty-"+UUID.randomUUID();var body=new PublicJobApiController.SubmitRequest("EXECUTE",null,null,"private");
        try {
            delivery.store.ProjectStore.deleteRecursive(books.generatedDir(p.getProjectId()));
            var response=api.submit(p.getProjectId(),key,body);Assert.assertEquals(response.getStatusCode().value(),409);
            Assert.assertEquals(((Map<?,?>)response.getBody()).get("error"),"NO_GENERATED_WORKBOOK");
            books.saveFromCases(p.getProjectId(),List.of(new ManualTestCase("TC_01","Health","","Open page","Page","P1","")),"IMPORT","test");
            Assert.assertEquals(api.submit(p.getProjectId(),key,body).getStatusCode().value(),202);
        } finally {SecurityContextHolder.clearContext();}
    }
    @Test public void publicSubmissionPreservesCredentialSelectionError() throws Exception {
        var p=project();credentials.create(p.getProjectId(),"qa","user","secret");
        var svc=new WorkspaceDirectory.ServiceIdentity("test",TenantId.parse(p.getTenantId()),WorkspaceRole.ADMIN,"test",false);
        SecurityContextHolder.getContext().setAuthentication(new ServiceIdentityFilter.ServiceToken(svc));
        try {
            var response=api.submit(p.getProjectId(),"profile-"+UUID.randomUUID(),new PublicJobApiController.SubmitRequest("EXECUTE",null,null,"private"));
            Assert.assertEquals(response.getStatusCode().value(),400);
            Assert.assertEquals(((Map<?,?>)response.getBody()).get("error"),"CREDENTIAL_PROFILE_REQUIRED");
        } finally {SecurityContextHolder.clearContext();}
    }

    @Test public void rolledBackAdmissionCannotBeReplayedFromTheMemoryCache() throws Exception {
        var p=project();var j=job(p);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            store.saveJob(j);tx.setRollbackOnly();
        });
        Assert.assertFalse(store.isJobPersisted(j.getJobId()));
        Assert.assertTrue(store.getJob(j.getJobId()).isEmpty());
    }
}
