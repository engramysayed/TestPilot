package delivery.job;

import delivery.packager.FrameworkPackager;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/**
 * D-QA-05: headless Automate emit → compile gate → ZIP, with PublicationGuard cancel fence.
 * No Appium / no browser.
 */
public class AutomateEmitPackageIT {

    @Test
    public void compileGateThenZipSucceedsForMinimalProject() throws Exception {
        Path root = Files.createTempDirectory("automate-emit-pkg");
        Path project = root.resolve("framework");
        Files.createDirectories(project.resolve("src/main/java/demo"));
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>demo</groupId>
                  <artifactId>automate-emit-it</artifactId>
                  <version>1.0.0</version>
                  <properties><maven.compiler.release>17</maven.compiler.release></properties>
                </project>
                """);
        Files.writeString(project.resolve("src/main/java/demo/Hello.java"),
                "package demo;\npublic class Hello { public static int n() { return 1; } }\n");

        EmitCompileCheck.runIfEnabled(project, java.time.Duration.ofMinutes(3), () -> false);

        Path zip = root.resolve("package.zip");
        AtomicBoolean cancelled = new AtomicBoolean(false);
        PublicationGuard guard = PublicationGuard.cancellable(cancelled::get);

        Path published = guard.publish(() -> {
            new FrameworkPackager().zip(project, zip);
            return zip;
        });
        Assert.assertTrue(Files.isRegularFile(published));
        Assert.assertTrue(Files.size(published) > 0);
        try (ZipFile zf = new ZipFile(published.toFile())) {
            Assert.assertNotNull(zf.getEntry("pom.xml"));
        }
    }

    @Test(expectedExceptions = JobCancelledException.class)
    public void publicationGuardRejectsWhenCancelAlreadyTrue() throws Exception {
        PublicationGuard guard = PublicationGuard.cancellable(() -> true);
        guard.publish(() -> "should-not-run");
    }

    @Test
    public void cancelMidPublishPreventsAuthoritativeWrite() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        PublicationGuard guard = PublicationGuard.cancellable(cancelled::get);
        cancelled.set(true);
        try {
            guard.publish(() -> {
                Assert.fail("publish action must not run after cancel");
                return null;
            });
            Assert.fail("expected JobCancelledException");
        } catch (JobCancelledException expected) {
            // ok
        }
    }
}
