package delivery.ci;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * B10 CI check: no hard-coded hosts or paths ({@code localhost}, {@code 127.0.0.1}, {@code C:\}, {@code D:\}, {@code E:\},
 * {@code delivery-store}, {@code portal.internal}) in production literals outside profile files and tests.
 * Existing, reviewed uses live in {@code src/test/resources/ci/hardcoded-allowlist.txt} (shrink-only: a stale entry fails).
 * CI: {@code scripts/ci/check-hardcoded.sh}.
 */
public class HardcodedLiteralCheckTest {
    static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("src/main/java/delivery"))) {
            p = p.getParent();
        }
        Assert.assertNotNull(p, "repo root not found");
        return p;
    }

    static final Path ALLOWLIST = Path.of("src/test/resources/ci/hardcoded-allowlist.txt");

    @Test
    public void productionCodeHasNoNewHardcodedHostsOrPaths() throws Exception {
        Path root = root();
        List<HardcodedLiteralScanner.Hit> hits = HardcodedLiteralScanner.scan(root, HardcodedLiteralScanner.productionSources(root));
        Set<String> allowed = HardcodedLiteralScanner.allowlist(root.resolve(ALLOWLIST)).stream()
                .map(HardcodedLiteralScanner.Allowed::key).collect(Collectors.toSet());
        List<HardcodedLiteralScanner.Hit> fresh = hits.stream().filter(h -> !allowed.contains(h.key())).toList();
        Assert.assertTrue(fresh.isEmpty(), "hard-coded host/path literals (make them a property, or add a reviewed "
                + "allowlist entry with a reason):" + HardcodedLiteralScanner.describe(fresh));
    }

    @Test
    public void allowlistIsShrinkOnlyEveryEntryStillMatches() throws Exception {
        Path root = root();
        Set<String> hitKeys = HardcodedLiteralScanner.scan(root, HardcodedLiteralScanner.productionSources(root)).stream()
                .map(HardcodedLiteralScanner.Hit::key).collect(Collectors.toSet());
        List<String> stale = HardcodedLiteralScanner.allowlist(root.resolve(ALLOWLIST)).stream()
                .map(HardcodedLiteralScanner.Allowed::key).filter(k -> !hitKeys.contains(k)).toList();
        Assert.assertEquals(stale, List.of(), "stale allowlist entries: remove them");
    }

    @Test
    public void fixtureTripsEveryRuleAndCommentsDoNot() throws Exception {
        Path root = root();
        Path fixtureDir = root.resolve("src/test/resources/ci/hardcoded-fixture");
        List<Path> files;
        try (var s = Files.list(fixtureDir)) {
            files = s.sorted().toList();
        }
        List<HardcodedLiteralScanner.Hit> hits = HardcodedLiteralScanner.scan(root, files);
        Set<String> rules = hits.stream().map(HardcodedLiteralScanner.Hit::rule).collect(Collectors.toSet());
        Assert.assertEquals(rules, HardcodedLiteralScanner.RULES.stream().map(HardcodedLiteralScanner.Rule::id)
                .collect(Collectors.toSet()), "every rule fires on the fixture");
        Assert.assertTrue(hits.stream().noneMatch(h -> h.literal().contains("COMMENT")), "comments are not values");
        Assert.assertTrue(hits.stream().anyMatch(h -> h.file().endsWith("Bad.mjs") && h.rule().equals("loopback-ip")),
                "engine JS is scanned");
        Assert.assertTrue(hits.stream().noneMatch(h -> h.literal().equals("http://portal.example:8080")),
                "property-style values do not trip");
    }

    @Test
    public void allowlistLinesNeedAReason() throws Exception {
        Path tmp = Files.createTempFile("allow", ".txt");
        Files.writeString(tmp, "src/main/java/X.java|localhost|\n");
        Assert.expectThrows(IllegalArgumentException.class, () -> HardcodedLiteralScanner.allowlist(tmp));
    }
}
