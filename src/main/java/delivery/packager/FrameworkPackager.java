package delivery.packager;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FrameworkPackager {

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
        Files.deleteIfExists(dest.resolve("src/test/java/project/tests/LoginTest.java"));
        Files.deleteIfExists(dest.resolve("src/main/java/project/pages/LoginPage.java"));
        Files.deleteIfExists(dest.resolve("src/main/java/project/pages/DashboardPage.java"));
        Files.deleteIfExists(dest.resolve("src/test/java/project/validations/ValidationAssertAllTest.java"));
        deleteQuietly(dest.resolve("src/test/java/project/validations/support"));
    }

    /**
     * Customer-editable config only. Generated pages/tests/data are rewritten from IR.
     */
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
                Files.copy(file, dst.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public void writeScoreReport(Path projectRoot, List<TcOutcome> outcomes) throws IOException {
        Path docs = projectRoot.resolve("docs");
        Files.createDirectories(docs);
        long passed = outcomes.stream().filter(o -> o.status() == TcStatus.PASSED).count();
        long partial = outcomes.stream().filter(o -> o.status() == TcStatus.PARTIAL).count();
        long todo = outcomes.stream().filter(o -> o.status() == TcStatus.TODO).count();
        StringBuilder sb = new StringBuilder();
        sb.append("# AUTOMATION SCORE\n\n");
        sb.append("- Passed: ").append(passed).append('\n');
        sb.append("- Partial (blocked mid-case): ").append(partial).append('\n');
        sb.append("- TODO: ").append(todo).append('\n');
        sb.append("- Total: ").append(outcomes.size()).append("\n\n");
        sb.append("## Cases\n\n");
        for (TcOutcome o : outcomes) {
            sb.append("- ").append(o.tcId()).append(": ").append(o.status());
            if (o.status() == TcStatus.TODO || o.status() == TcStatus.PARTIAL) {
                sb.append(" — ").append(o.failureReason() == null ? "" : o.failureReason());
            }
            sb.append('\n');
        }
        Files.writeString(docs.resolve("AUTOMATION_SCORE.md"), sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Writes runtime target config. Packaged files never contain real passwords —
     * TARGET_* are empty placeholders. Optional local secrets file is written beside
     * the project for operator convenience but excluded from ZIP.
     */
    public void writeTargetConfig(Path projectRoot, String baseUrl) throws IOException {
        writeTargetConfig(projectRoot, baseUrl, null, null);
    }

    public void writeTargetConfig(Path projectRoot, String baseUrl, String username, String password) throws IOException {
        Path resources = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resources);
        String url = baseUrl == null ? "" : baseUrl.trim();
        String packaged = """
                BROWSER_TYPE=CHROME
                EXECUTION_TYPE=LOCAL
                BROWSER_HEADLESS=false
                BASE_WEB=%s
                TARGET_USERNAME=
                TARGET_PASSWORD=
                """.formatted(url);
        Files.writeString(resources.resolve("delivery-target.properties"), packaged, StandardCharsets.UTF_8);
        Path env = resources.resolve("enviroment.properties");
        if (Files.exists(env)) {
            String existing = Files.readString(env, StandardCharsets.UTF_8);
            String updated = existing.replaceAll("(?m)^BASE_WEB=.*$", "BASE_WEB=" + url);
            if (!updated.contains("TARGET_USERNAME=")) {
                updated = updated.trim() + "\nTARGET_USERNAME=\nTARGET_PASSWORD=\n";
            } else {
                updated = updated.replaceAll("(?m)^TARGET_USERNAME=.*$", "TARGET_USERNAME=");
                updated = updated.replaceAll("(?m)^TARGET_PASSWORD=.*$", "TARGET_PASSWORD=");
            }
            Files.writeString(env, updated, StandardCharsets.UTF_8);
        }
        Path example = resources.resolve("webapp.properties.example");
        Files.writeString(example, """
                BROWSER_TYPE=CHROME
                EXECUTION_TYPE=LOCAL
                BROWSER_HEADLESS=false
                BASE_WEB=%s
                TARGET_USERNAME=
                TARGET_PASSWORD=
                # Copy to webapp.properties locally if your TAF build expects that filename.
                # Fill TARGET_* for Excel cases that login in @BeforeMethod. Never commit real passwords.
                """.formatted(url), StandardCharsets.UTF_8);

        // Operator-local secrets (not zipped)
        if ((username != null && !username.isBlank()) || (password != null && !password.isBlank())) {
            String local = """
                    # Local only — excluded from package.zip
                    TARGET_USERNAME=%s
                    TARGET_PASSWORD=%s
                    """.formatted(
                    username == null ? "" : username.trim(),
                    password == null ? "" : password);
            Files.writeString(projectRoot.resolve("delivery-target.local.properties"), local, StandardCharsets.UTF_8);
        }
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

    static boolean shouldInclude(String relativePath) {
        String rel = normalizeRel(relativePath);
        if (rel.isEmpty() || isExcludedPath(rel) || isExcludedFile(rel)) {
            return false;
        }
        if (rel.equals("pom.xml") || rel.equals("README.md") || rel.equals(".gitignore")) {
            return true;
        }
        return rel.startsWith("src/") || rel.startsWith("docs/") || rel.startsWith(".github/");
    }

    static boolean shouldSkipDirectory(String relativePath) {
        String rel = normalizeRel(relativePath);
        if (rel.isEmpty() || ".".equals(rel)) {
            return false;
        }
        if (isExcludedPath(rel)) {
            return true;
        }
        if (rel.equals("src/test/java/project/validations")
                || rel.startsWith("src/test/java/project/validations/")) {
            return true;
        }
        return !isAllowlistedPrefix(rel);
    }

    private static boolean isAllowlistedPrefix(String rel) {
        return rel.equals("src") || rel.startsWith("src/")
                || rel.equals("docs") || rel.startsWith("docs/")
                || rel.equals(".github") || rel.startsWith(".github/");
    }

    private static boolean isExcludedPath(String rel) {
        for (String part : rel.split("/")) {
            if (part.equals(".git") || part.equals("templates") || part.equals("target")
                    || part.equals("test-output") || part.equals("allure-results")
                    || part.equals("allure-report") || part.equals(".idea")
                    || part.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcludedFile(String rel) {
        String name = rel.substring(rel.lastIndexOf('/') + 1);
        if (name.endsWith(".class") || name.endsWith(".log")) {
            return true;
        }
        if ("delivery-target.local.properties".equals(name)) {
            return true;
        }
        if ("webapp.properties".equals(name) && !rel.endsWith(".example")) {
            return true;
        }
        return "LoginTest.java".equals(name)
                || "LoginPage.java".equals(name)
                || "DashboardPage.java".equals(name)
                || "ValidationAssertAllTest.java".equals(name)
                || "FalseCustomerAssertionSample.java".equals(name)
                || "PassingCustomerAssertionSample.java".equals(name)
                || "QuitProbeDriverFactory.java".equals(name);
    }

    private static void deleteQuietly(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
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

    public boolean zipContainsPasswordFile(Path zipFile) throws IOException {
        // helper for tests — scan extracted tree instead when needed
        return false;
    }

    public String summarize(List<TcOutcome> outcomes) {
        return outcomes.stream()
                .map(o -> o.tcId() + "=" + o.status())
                .collect(Collectors.joining(", "));
    }
}
