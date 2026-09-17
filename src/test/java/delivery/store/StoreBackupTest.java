package delivery.store;

import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public class StoreBackupTest {

    @Test
    public void restorePreservesTenantPathAndArtifactChecksum() throws Exception {
        Path live = Files.createTempDirectory("store-live");
        Path backup = Files.createTempDirectory("store-bak");
        TenantId tenant = TenantId.mint();
        Path artifact = ScopePaths.projectRoot(live, tenant, "prj_restore")
                .resolve("versions").resolve("v1.zip");
        Files.createDirectories(artifact.getParent());
        byte[] body = "FRAMEWORK_V1".getBytes(StandardCharsets.UTF_8);
        Files.write(artifact, body);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        Files.writeString(artifact.resolveSibling("v1.sha256"), sha);

        Path snapshot = StoreBackup.snapshot(live, backup);
        Assert.assertTrue(Files.isDirectory(snapshot));

        Files.walk(live)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
        Assert.assertFalse(Files.exists(artifact));

        StoreBackup.restore(snapshot, live);
        Path restored = ScopePaths.projectRoot(live, tenant, "prj_restore")
                .resolve("versions").resolve("v1.zip");
        Assert.assertTrue(Files.isRegularFile(restored));
        Assert.assertEquals(Files.readAllBytes(restored), body);
        Assert.assertEquals(Files.readString(restored.resolveSibling("v1.sha256")).trim(), sha);
        Assert.assertEquals(StoreBackup.sha256(restored), sha);
    }
}
