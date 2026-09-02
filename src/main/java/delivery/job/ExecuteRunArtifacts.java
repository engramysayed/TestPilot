package delivery.job;

import delivery.ir.TcDraftStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Copies per-TC execute artifacts from a transient work dir to durable execute-runs storage. */
public final class ExecuteRunArtifacts {
    private ExecuteRunArtifacts() {
    }

    /**
     * Mirrors one TC's IR draft (and evidence dir when present) from {@code work} to {@code dest}.
     * No-op when the IR file is absent.
     */
    public static void syncTc(Path work, Path dest, String tcId) throws Exception {
        if (work == null || dest == null || tcId == null || tcId.isBlank()) {
            return;
        }
        String safe = TcDraftStore.safeFileName(tcId);
        Path irSrc = work.resolve("ir").resolve(safe + ".json");
        if (!Files.isRegularFile(irSrc)) {
            return;
        }
        Path irDest = dest.resolve("ir").resolve(safe + ".json");
        Files.createDirectories(irDest.getParent());
        Files.copy(irSrc, irDest, StandardCopyOption.REPLACE_EXISTING);

        Path evidenceSrc = work.resolve("evidence").resolve(tcId);
        if (Files.isDirectory(evidenceSrc)) {
            Path evidenceDest = dest.resolve("evidence").resolve(tcId);
            copyTree(evidenceSrc, evidenceDest);
        }
    }

    private static void copyTree(Path src, Path dest) throws Exception {
        Files.walk(src).forEach(path -> {
            try {
                Path rel = dest.resolve(src.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(rel);
                } else {
                    Files.createDirectories(rel.getParent());
                    Files.copy(path, rel, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
