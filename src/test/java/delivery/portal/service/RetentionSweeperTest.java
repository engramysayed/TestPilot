package delivery.portal.service;



import delivery.portal.DeliveryPortalProperties;

import org.testng.Assert;

import org.testng.annotations.Test;



import java.io.IOException;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.attribute.FileTime;

import java.time.Instant;

import java.time.temporal.ChronoUnit;



public class RetentionSweeperTest {



    @Test

    public void deletesAgedExecuteRunJobDir() throws Exception {

        Path store = Files.createTempDirectory("retention-store-");

        Path project = store.resolve("proj-1");

        Path executeRuns = project.resolve("execute-runs");

        Path oldJob = executeRuns.resolve("job-old");

        Path recentJob = executeRuns.resolve("job-recent");

        Files.createDirectories(oldJob);

        Files.createDirectories(recentJob);

        age(oldJob, 20);

        age(recentJob, 1);



        sweep(store, null, 14);



        Assert.assertFalse(Files.exists(oldJob));

        Assert.assertTrue(Files.isDirectory(recentJob));

    }



    @Test

    public void deletesAgedExecuteRunUnderNestedDomainLayout() throws Exception {

        Path store = Files.createTempDirectory("retention-store-nested-");

        Path executeRuns = store.resolve("example-com").resolve("proj-2").resolve("execute-runs");

        Path oldJob = executeRuns.resolve("job-nested-old");

        Files.createDirectories(oldJob);

        age(oldJob, 30);



        sweep(store, null, 14);



        Assert.assertFalse(Files.exists(oldJob));

    }



    @Test

    public void deletesAgedWorkDirChild() throws Exception {

        Path work = Files.createTempDirectory("retention-work-");

        Path oldWork = work.resolve("facebook-com-20260101-120000");

        Path recentWork = work.resolve("facebook-com-20260820-120000");

        Files.createDirectories(oldWork);

        Files.createDirectories(recentWork);

        age(oldWork, 20);

        age(recentWork, 2);



        sweep(null, work, 14);



        Assert.assertFalse(Files.exists(oldWork));

        Assert.assertTrue(Files.isDirectory(recentWork));

    }



    @Test

    public void neverTouchesGeneratedDir() throws Exception {

        Path store = Files.createTempDirectory("retention-store-gen-");

        Path generated = store.resolve("proj-3").resolve("generated");

        Path oldWorkbook = generated.resolve("workbook-v1");

        Files.createDirectories(oldWorkbook);

        age(oldWorkbook, 60);



        sweep(store, null, 14);



        Assert.assertTrue(Files.isDirectory(oldWorkbook));

    }



    @Test

    public void retentionDaysZeroDisablesSweeper() throws Exception {

        Path store = Files.createTempDirectory("retention-store-off-");

        Path oldJob = store.resolve("proj-4").resolve("execute-runs").resolve("job-off");

        Files.createDirectories(oldJob);

        age(oldJob, 90);



        sweep(store, null, 0);



        Assert.assertTrue(Files.isDirectory(oldJob));

    }



    @Test

    public void propertiesDefaultRetentionDaysIs14() {

        Assert.assertEquals(new DeliveryPortalProperties().getRetention().getDays(), 14);

    }



    private static void sweep(Path storeRoot, Path workDir, int retentionDays) throws IOException {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot((storeRoot != null ? storeRoot : Files.createTempDirectory("retention-store-unused-")).toString());
        props.setWorkDir((workDir != null ? workDir : Files.createTempDirectory("retention-work-unused-")).toString());
        props.getRetention().setDays(retentionDays);
        new RetentionSweeper(props).sweep();
    }



    private static void age(Path dir, long daysAgo) throws IOException {

        Files.setLastModifiedTime(dir, FileTime.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)));

    }

}

