package delivery.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Unique evidence folders so an interrupted run cannot overwrite a completed one.
 */
public final class LocalAcceptanceEvidence {
    private static final DateTimeFormatter UTC_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private LocalAcceptanceEvidence() {
    }

    public static Path createRunDir(Path evidenceRoot) throws IOException {
        if (evidenceRoot == null) {
            throw new IllegalArgumentException("evidenceRoot is required");
        }
        Files.createDirectories(evidenceRoot);
        String id = UTC_STAMP.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path dir = evidenceRoot.resolve(id);
        Files.createDirectories(dir);
        return dir;
    }
}
