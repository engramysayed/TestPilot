package delivery.ops;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records this workstation's FileStore and hostname. Does not constitute a deployed
 * shared or dedicated installation drill.
 */
public class Phase5HostEvidenceTest {

    @Test
    public void thisWorkstationIsNotADeployedSharedOrDedicatedInstall() throws Exception {
        Path probe = Files.createDirectories(Path.of("target", "phase5-host-evidence"));
        FileStore store = Files.getFileStore(probe);
        String host = InetAddress.getLocalHost().getHostName();
        String report = """
                host=%s
                os=%s
                fileStoreName=%s
                fileStoreType=%s
                deployedSharedInstallation=UNAVAILABLE
                deployedDedicatedInstallation=UNAVAILABLE
                workerNetworkIsolation=application-layer+PAC-on-this-host-only
                backupRestore=StoreBackup-on-this-volume
                filesystemLock=PublicationLock-on-this-FileStore
                namedApprovals=unsigned
                """.formatted(
                host,
                System.getProperty("os.name"),
                store.name(),
                store.type());
        Files.writeString(probe.resolve("evidence.txt"), report);
        Assert.assertFalse(store.type().isBlank());
        Assert.assertTrue(Files.readString(probe.resolve("evidence.txt")).contains("UNAVAILABLE"));
    }
}
