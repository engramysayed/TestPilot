package delivery.agent.validation.automate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

final class FrameworkRepairQualificationEvidence {
    private FrameworkRepairQualificationEvidence() {
    }

    static byte[] treeDigest(Path root) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        if (!Files.isDirectory(root)) {
            return md.digest();
        }
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(p -> {
                        try {
                            md.update(root.relativize(p).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            md.update(Files.readAllBytes(p));
                        } catch (Exception ignored) {
                        }
                    });
        }
        return md.digest();
    }

    static String treeDigestHex(Path root) throws Exception {
        return HexFormat.of().formatHex(treeDigest(root));
    }
}