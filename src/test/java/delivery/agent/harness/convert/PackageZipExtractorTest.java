package delivery.agent.harness.convert;

import delivery.job.ArtifactIntegrityException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackageZipExtractorTest {

    @Test
    public void extractsValidZip() throws Exception {
        Path zip = Files.createTempFile("pkg", ".zip");
        Path dest = Files.createTempDirectory("ws");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("src/Main.java"));
            zos.write("class Main {}".getBytes());
            zos.closeEntry();
        }
        PackageZipExtractor.extract(zip, dest);
        Assert.assertTrue(Files.isRegularFile(dest.resolve("src/Main.java")));
    }

    @Test(expectedExceptions = ArtifactIntegrityException.class)
    public void rejectsTraversal() throws Exception {
        Path zip = Files.createTempFile("bad", ".zip");
        Path dest = Files.createTempDirectory("ws-bad");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../escape.txt"));
            zos.write("x".getBytes());
            zos.closeEntry();
        }
        PackageZipExtractor.extract(zip, dest);
    }

    @Test(expectedExceptions = ArtifactIntegrityException.class)
    public void rejectsAbsolutePath() throws Exception {
        Path zip = Files.createTempFile("abs", ".zip");
        Path dest = Files.createTempDirectory("ws-abs");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("/tmp/evil.txt"));
            zos.write("x".getBytes());
            zos.closeEntry();
        }
        PackageZipExtractor.extract(zip, dest);
    }

    @Test
    public void symlinkEntry_extractedAsRegularFile_notSymlink() throws Exception {
        Path dir = Files.createTempDirectory("zip-sym");
        Path zip = SymlinkZipFixtures.writeZipWithSymlinkEntry(dir);
        Path dest = Files.createTempDirectory("ws-sym");
        PackageZipExtractor.extract(zip, dest);
        Path linkOut = dest.resolve("link-out");
        Assert.assertTrue(Files.isRegularFile(linkOut));
        Assert.assertFalse(Files.isSymbolicLink(linkOut));
        Assert.assertFalse(Files.exists(dest.resolve("../outside").normalize()));
    }

    @Test
    public void cleansUpOnFailure() throws Exception {
        Path zip = Files.createTempFile("corrupt", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            // no entries
        }
        Path dest = Files.createTempDirectory("ws-corrupt");
        try {
            PackageZipExtractor.extract(zip, dest);
            Assert.fail("expected corrupt zip failure");
        } catch (ArtifactIntegrityException e) {
            Assert.assertFalse(Files.exists(dest.resolve("partial-marker-not-needed")));
        }
    }
}
