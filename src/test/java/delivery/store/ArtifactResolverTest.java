package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ArtifactResolverTest {

    @Test
    public void missingJobZipDoesNotFallBackToAnotherVersionsLatest() throws Exception {
        Path root = Files.createTempDirectory("artifact-bind");
        Path v2 = root.resolve("versions").resolve("v2.zip");
        Files.createDirectories(v2.getParent());
        Files.writeString(v2, "v2-bytes");
        Optional<Path> resolved = ArtifactResolver.boundFile(null, Path.of("missing-job.zip"));
        Assert.assertTrue(resolved.isEmpty(), "expired/missing v1 must not download v2");
    }

    @Test
    public void boundPathIsReturnedWhenTheFileStillExists() throws Exception {
        Path zip = Files.createTempFile("job-art", ".zip");
        Files.writeString(zip, "job-bytes");
        Optional<Path> resolved = ArtifactResolver.boundFile(zip, null);
        Assert.assertEquals(resolved.orElseThrow(), zip);
    }

    @Test
    public void expiredMarkerIsNotServedAsLatest() throws Exception {
        ArtifactManifest.Entry v1 = new ArtifactManifest.Entry(1, "sha-a", "job_old", "CONVERT", true);
        ArtifactManifest.Entry v2 = new ArtifactManifest.Entry(2, "sha-b", "job_new", "CONVERT", false);
        ArtifactManifest manifest = new ArtifactManifest("prj", 2, java.util.List.of(v1, v2));
        Assert.assertTrue(manifest.entry(1).orElseThrow().expired());
        Assert.assertFalse(manifest.entry(2).orElseThrow().expired());
        Assert.assertTrue(ArtifactManifest.availability(manifest, 1).gone());
        Assert.assertFalse(ArtifactManifest.availability(manifest, 2).gone());
    }
}
