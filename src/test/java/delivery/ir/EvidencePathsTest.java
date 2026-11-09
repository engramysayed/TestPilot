package delivery.ir;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class EvidencePathsTest {

    @Test
    public void resolvePrefersTheOccurrenceFolderWhereStepShotsLive() throws Exception {
        Path root = Files.createTempDirectory("ev");
        Path occ = root.resolve(TcIdentity.storageKey("TC_01") + "__occ_1");
        Files.createDirectories(occ);
        Files.write(occ.resolve("step-001.png"), new byte[] { 1, 2, 3 });
        Assert.assertEquals(EvidencePaths.resolve(root, "TC_01"), occ);
        Assert.assertTrue(Files.isRegularFile(EvidencePaths.resolve(root, "TC_01").resolve("step-001.png")));
    }

    @Test
    public void resolvePicksTheLatestOccurrenceWhenSeveralExist() throws Exception {
        Path root = Files.createTempDirectory("ev2");
        Path first = root.resolve(TcIdentity.storageKey("TC_01") + "__occ_1");
        Path second = root.resolve(TcIdentity.storageKey("TC_01") + "__occ_2");
        Files.createDirectories(first);
        Files.createDirectories(second);
        Files.write(first.resolve("step-001.png"), new byte[] { 1 });
        Files.write(second.resolve("step-001.png"), new byte[] { 2 });
        Assert.assertEquals(EvidencePaths.resolve(root, "TC_01"), second);
    }

    @Test
    public void resolveFallsBackToPlainFolderWhenNoOccurrenceExists() throws Exception {
        Path root = Files.createTempDirectory("ev3");
        Path plain = root.resolve(TcIdentity.storageKey("TC_01"));
        Files.createDirectories(plain);
        Files.write(plain.resolve("failure.png"), new byte[] { 9 });
        Assert.assertEquals(EvidencePaths.resolve(root, "TC_01"), plain);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsPathTraversalTcId() throws Exception {
        Path root = Files.createTempDirectory("ev4");
        EvidencePaths.resolve(root, "../../etc/hosts");
    }
}
