package delivery.codegen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persists project test-data env prefix for phase-2 emit (optional). */
public final class CodegenEnvHint {
    private static final String FILE = "codegen-test-data-env.txt";

    private CodegenEnvHint() {
    }

    public static void write(Path dir, String testDataEnvPrefix) throws java.io.IOException {
        if (dir == null) {
            return;
        }
        String env = testDataEnvPrefix == null ? "" : testDataEnvPrefix.trim();
        Path out = dir.resolve(FILE);
        if (env.isBlank()) {
            Files.deleteIfExists(out);
            return;
        }
        Files.createDirectories(dir);
        Files.writeString(out, env, StandardCharsets.UTF_8);
    }

    public static String read(Path workDir) {
        if (workDir == null) {
            return "";
        }
        try {
            Path f = workDir.resolve(FILE);
            if (!Files.isRegularFile(f)) {
                return "";
            }
            return Files.readString(f, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
