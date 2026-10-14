package delivery.mobile.codegen;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * M4-07: assert export hygiene — no APK binaries, secrets, or machine identifiers in ZIP.
 */
public final class MobileExportScrub {

    public record Finding(String path, String reason) {}

    private MobileExportScrub() {}

    public static List<Finding> scrubZip(Path zip) throws IOException {
        List<Finding> findings = new ArrayList<>();
        if (zip == null || !Files.isRegularFile(zip)) {
            findings.add(new Finding(String.valueOf(zip), "zip missing"));
            return findings;
        }
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            zf.stream().forEach(e -> {
                String name = e.getName().replace('\\', '/');
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".apk") || lower.endsWith(".aab")) {
                    findings.add(new Finding(name, "binary app artifact"));
                }
                if (lower.endsWith("secrets.properties") || lower.contains("secrets.local")) {
                    findings.add(new Finding(name, "secrets file"));
                }
                String base = name.substring(name.lastIndexOf('/') + 1);
                if (base.equals("local.properties") || base.equals("device.local.properties")) {
                    findings.add(new Finding(name, "machine-local properties"));
                }
                if (lower.contains("udid=") && !lower.contains("mobile.udid=")) {
                    // content scan deferred — entry name heuristics only here
                }
            });
        }
        return findings;
    }

    public static void assertClean(Path zip) throws IOException {
        List<Finding> f = scrubZip(zip);
        if (!f.isEmpty()) {
            throw new IllegalStateException("export scrub failed: " + f);
        }
    }

    public static List<Finding> scrubTree(Path root) throws IOException {
        List<Finding> findings = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return findings;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".apk") || name.equals("secrets.properties")) {
                    findings.add(new Finding(root.relativize(file).toString(), "forbidden in export tree"));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return findings;
    }
}
