package delivery.store;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Write-then-rename so a JVM crash mid-write cannot leave a truncated file. */
public final class Atomic {
    private Atomic() {}

    public static void writeString(Path file, String content, Charset cs) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Files.writeString(tmp, content, cs);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Some filesystems (certain network mounts) don't support ATOMIC_MOVE.
            // Fall back to a plain replace — still safer than truncate-then-write.
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
