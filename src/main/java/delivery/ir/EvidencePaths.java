package delivery.ir;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves evidence directories for a display TC ID after storage-key encoding. */
public final class EvidencePaths {
    private EvidencePaths() {
    }

    public static Path resolve(Path evidenceRoot, String tcId) {
        if (evidenceRoot == null) {
            return Path.of("evidence", TcIdentity.storageKey(tcId));
        }
        Path encoded = evidenceRoot.resolve(TcIdentity.storageKey(tcId));
        if (Files.isDirectory(encoded) && hasFiles(encoded)) {
            return encoded;
        }
        Path occ = latestOccurrence(evidenceRoot, TcIdentity.storageKey(tcId));
        if (occ != null) {
            return occ;
        }
        if (tcId != null && !tcId.isBlank()) {
            Path raw = evidenceRoot.resolve(tcId);
            if (Files.isDirectory(raw) && hasFiles(raw)) {
                return raw;
            }
            Path legacy = evidenceRoot.resolve(TcDraftStore.safeFileName(tcId));
            if (Files.isDirectory(legacy) && hasFiles(legacy)) {
                return legacy;
            }
            Path legacyOcc = latestOccurrence(evidenceRoot, TcDraftStore.safeFileName(tcId));
            if (legacyOcc != null) {
                return legacyOcc;
            }
        }
        return encoded;
    }

    /** Newest {@code {prefix}__occ_N} folder that actually holds files. */
    static Path latestOccurrence(Path evidenceRoot, String prefix) {
        if (evidenceRoot == null || prefix == null || prefix.isBlank() || !Files.isDirectory(evidenceRoot)) {
            return null;
        }
        String start = prefix + "__occ_";
        Path best = null;
        int bestN = -1;
        try (var stream = Files.list(evidenceRoot)) {
            for (Path dir : stream.toList()) {
                if (!Files.isDirectory(dir) || !hasFiles(dir)) {
                    continue;
                }
                String name = dir.getFileName().toString();
                if (!name.startsWith(start)) {
                    continue;
                }
                try {
                    int n = Integer.parseInt(name.substring(start.length()));
                    if (n >= bestN) {
                        bestN = n;
                        best = dir;
                    }
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        } catch (Exception e) {
            return null;
        }
        return best;
    }

    private static boolean hasFiles(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(Files::isRegularFile);
        } catch (Exception e) {
            return false;
        }
    }
}
