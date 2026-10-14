package delivery.mobile.codegen;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Mobile customer ZIP packager (M4-04). Deny-list strips helper tests, secrets, APKs,
 * machine identifiers, Appium caches. Never ships web-only page packs.
 */
public final class MobileFrameworkPackager {

    public MobileFrameworkPackager() {}

    public void copyTemplate(Path templateRoot, Path dest) throws IOException {
        Files.createDirectories(dest);
        Files.walkFileTree(templateRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = templateRoot.relativize(dir);
                String rel = relative.toString().replace('\\', '/');
                if (shouldSkipDirectory(rel)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(dest.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = templateRoot.relativize(file);
                String name = relative.toString().replace('\\', '/');
                if (!shouldInclude(name)) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, dest.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
        // Explicit helper-test cleanup
        deleteQuietly(dest.resolve("pom-helper-tests.xml"));
        deleteMatchingTests(dest.resolve("src/test/java"));
    }

    /** Preserve customer-editable config overlay from previous package. */
    public void overlayCustomerConfig(Path previousFramework, Path dest) throws IOException {
        if (previousFramework == null || dest == null || !Files.isDirectory(previousFramework)) {
            return;
        }
        Path src = previousFramework.resolve("src/test/resources/config");
        if (!Files.isDirectory(src)) {
            return;
        }
        Path dst = dest.resolve("src/test/resources/config");
        Files.createDirectories(dst);
        try (var files = Files.list(src)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!Files.isRegularFile(file) || name.endsWith(".example")) {
                    continue;
                }
                if (name.toLowerCase(Locale.ROOT).contains("secret")) {
                    continue; // never overlay secrets into new package
                }
                Files.copy(file, dst.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public void writeCoverageReport(Path projectRoot, int generated, int todo) throws IOException {
        Path docs = projectRoot.resolve("docs");
        Files.createDirectories(docs);
        String sb = "# MOBILE AUTOMATION COVERAGE\n\n"
                + "- Generated: " + generated + '\n'
                + "- TODO: " + todo + '\n'
                + "- Total: " + (generated + todo) + "\n\n"
                + "TODO cases are included in the package (enabled=false) for coverage accounting.\n";
        Files.writeString(docs.resolve("MOBILE_COVERAGE.md"), sb, StandardCharsets.UTF_8);
    }

    public void zip(Path projectDir, Path zipOut) throws IOException {
        Files.createDirectories(zipOut.getParent());
        try (OutputStream fos = Files.newOutputStream(zipOut);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            Files.walkFileTree(projectDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String rel = projectDir.relativize(dir).toString().replace('\\', '/');
                    if (shouldSkipDirectory(rel)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entry = projectDir.relativize(file).toString().replace('\\', '/');
                    if (!shouldInclude(entry)) {
                        return FileVisitResult.CONTINUE;
                    }
                    zos.putNextEntry(new ZipEntry(entry));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Publish only after successful compile. On compile failure, prior ZIP is left unchanged.
     */
    public MobileCompileGate.Result compileAndZip(
            Path projectDir, Path zipOut, Path priorPublishedZip, boolean invokeMaven) throws Exception {
        MobileCompileGate.Result gate;
        if (invokeMaven) {
            gate = MobileCompileGate.compileBeforePublish(projectDir, priorPublishedZip);
        } else {
            // Structural gate for unit tests: require pom.xml present
            if (!Files.isRegularFile(projectDir.resolve("pom.xml"))) {
                gate = MobileCompileGate.failPreserve(priorPublishedZip, "missing pom.xml");
            } else {
                gate = new MobileCompileGate.Result(true, "structural ok", priorPublishedZip);
            }
        }
        if (!gate.ok()) {
            return gate;
        }
        zip(projectDir, zipOut);
        return gate;
    }

    public static boolean zipContains(Path zip, String entry) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            return zf.getEntry(entry) != null;
        }
    }

    public static List<String> listZipEntries(Path zip) throws IOException {
        List<String> out = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            zf.stream().forEach(e -> out.add(e.getName()));
        }
        return out;
    }

    static boolean shouldInclude(String relativePath) {
        String rel = normalizeRel(relativePath);
        if (rel.isEmpty() || isExcludedPath(rel) || isExcludedFile(rel)) {
            return false;
        }
        if (rel.equals("pom.xml") || rel.equals("README.md") || rel.equals(".gitignore")
                || rel.equals("testng.xml")) {
            return true;
        }
        return rel.startsWith("src/") || rel.startsWith("docs/") || rel.startsWith("ci/")
                || rel.startsWith(".github/");
    }

    static boolean shouldSkipDirectory(String relativePath) {
        String rel = normalizeRel(relativePath);
        if (rel.isEmpty() || ".".equals(rel)) {
            return false;
        }
        if (isExcludedPath(rel)) {
            return true;
        }
        return !isAllowlistedPrefix(rel);
    }

    private static boolean isAllowlistedPrefix(String rel) {
        return rel.equals("src") || rel.startsWith("src/")
                || rel.equals("docs") || rel.startsWith("docs/")
                || rel.equals("ci") || rel.startsWith("ci/")
                || rel.equals(".github") || rel.startsWith(".github/");
    }

    private static boolean isExcludedPath(String rel) {
        for (String part : rel.split("/")) {
            if (part.equals(".git") || part.equals("templates") || part.equals("target")
                    || part.equals("test-output") || part.equals("allure-results")
                    || part.equals("allure-report") || part.equals(".idea")
                    || part.equals("node_modules") || part.equals("helper-tests")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcludedFile(String rel) {
        String name = rel.substring(rel.lastIndexOf('/') + 1);
        String lower = name.toLowerCase(Locale.ROOT);
        if (name.endsWith(".class") || name.endsWith(".log") || lower.endsWith(".apk")) {
            return true;
        }
        if ("pom-helper-tests.xml".equals(name)) {
            return true;
        }
        if (name.equals("secrets.example.properties")) {
            return false;
        }
        if (name.equals("secrets.properties") || name.endsWith("secrets.local.properties")
                || name.equals("delivery-secrets.local.properties")) {
            return true;
        }
        if (name.endsWith("Test.java")
                && (rel.contains("/config/") || rel.contains("/runtime/") || rel.contains("/utils/")
                || rel.contains("/naming/"))) {
            return true; // helper unit tests
        }
        // Machine identifiers / local overlays
        if (name.equals("local.properties") || name.equals("device.local.properties")) {
            return true;
        }
        // Web-only markers must never appear in mobile ZIP
        if (name.equals("WebDriverFactory.java") || name.equals("Browser.java")
                || rel.contains("/pages/") && name.endsWith("Page.java")) {
            return true;
        }
        return false;
    }

    private static void deleteMatchingTests(Path testJavaRoot) throws IOException {
        if (testJavaRoot == null || !Files.isDirectory(testJavaRoot)) {
            return;
        }
        Files.walkFileTree(testJavaRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = testJavaRoot.relativize(file).toString().replace('\\', '/');
                String full = "src/test/java/" + rel;
                if (isExcludedFile(full)) {
                    Files.deleteIfExists(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteQuietly(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static String normalizeRel(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        String rel = relativePath.replace('\\', '/');
        if (rel.startsWith("./")) {
            rel = rel.substring(2);
        }
        if (rel.endsWith("/")) {
            rel = rel.substring(0, rel.length() - 1);
        }
        return rel;
    }
}
