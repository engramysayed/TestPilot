package delivery.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Binds a job download to that job's own file. Never substitutes another version. */
public final class ArtifactResolver {
    private ArtifactResolver() {
    }

    public static Optional<Path> boundFile(Path jobZip, Path persistedZip) {
        if (jobZip != null && Files.isRegularFile(jobZip)) {
            return Optional.of(jobZip);
        }
        if (persistedZip != null && Files.isRegularFile(persistedZip)) {
            return Optional.of(persistedZip);
        }
        return Optional.empty();
    }
}
