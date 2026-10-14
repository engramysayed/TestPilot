package project.runtime.settle;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Fail if Thread.sleep( appears as a call in project sources. */
public class NoThreadSleepGuardTest {

    private static final Pattern SLEEP_CALL = Pattern.compile("Thread\\.sleep\\s*\\(");

    @Test
    public void runtimeSourcesMustNotContainThreadSleep() throws IOException {
        Path root = Path.of("src/main/java/project");
        if (!Files.isDirectory(root)) {
            root = Path.of("../src/main/java/project");
        }
        Assert.assertTrue(Files.isDirectory(root), "project sources not found: " + root.toAbsolutePath());
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String src = Files.readString(p, StandardCharsets.UTF_8);
                    Assert.assertFalse(
                            SLEEP_CALL.matcher(src).find(),
                            "Thread.sleep(...) call forbidden in " + p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
