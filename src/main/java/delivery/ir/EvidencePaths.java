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
        if (Files.isDirectory(encoded)) {
            return encoded;
        }
        if (tcId != null && !tcId.isBlank()) {
            Path raw = evidenceRoot.resolve(tcId);
            if (Files.isDirectory(raw)) {
                return raw;
            }
            Path legacy = evidenceRoot.resolve(TcDraftStore.safeFileName(tcId));
            if (Files.isDirectory(legacy)) {
                return legacy;
            }
        }
        return encoded;
    }
}
