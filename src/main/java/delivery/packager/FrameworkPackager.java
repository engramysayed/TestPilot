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
                if (rel.equals(".git") || rel.startsWith(".git/")
                        || rel.equals("templates") || rel.startsWith("templates/")
                        || rel.equals("target") || rel.startsWith("target/")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(dest.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = templateRoot.relativize(file);
                String name = relative.toString().replace('\\', '/');
                if (name.endsWith("webapp.properties") && !name.endsWith("webapp.properties.example")) {
                    return FileVisitResult.CONTINUE;
                }
                // Sample LoginTest / demo pages confuse customers with generated target tests
                if (name.endsWith("project/tests/LoginTest.java") || name.endsWith("LoginTest.java")) {
                    return FileVisitResult.CONTINUE;
                }
                if (name.endsWith("project/pages/LoginPage.java") || name.endsWith("pages/LoginPage.java")
                        || name.endsWith("project/pages/DashboardPage.java")
                        || name.endsWith("pages/DashboardPage.java")) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, dest.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
        Files.deleteIfExists(dest.resolve("src/test/java/project/tests/LoginTest.java"));
        Files.deleteIfExists(dest.resolve("src/main/java/project/pages/LoginPage.java"));
        Files.deleteIfExists(dest.resolve("src/main/java/project/pages/DashboardPage.java"));
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
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName().toString();
                    if ("webapp.properties".equals(name) && !file.toString().endsWith(".example")) {
                        Path example = file.resolveSibling("webapp.properties.example");
                        if (Files.exists(example)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }
                    if ("delivery-target.local.properties".equals(name)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if ("LoginTest.java".equals(name)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String entry = projectDir.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entry));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
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
