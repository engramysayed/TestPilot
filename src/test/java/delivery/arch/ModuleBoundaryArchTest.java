package delivery.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * B02 (Phase 0a): module boundary rules on the current monolith, with today's violations allowlisted.
 *
 * <ul>
 *   <li><b>PORTAL_NO_SELENIUM</b>: {@code delivery.portal..} must not use {@code org.openqa.selenium..}.</li>
 *   <li><b>ENGINE_NO_DB</b>: engine-side packages (SPLIT_DESIGN §2: job, authoring, heal, vision, hunt,
 *       codegen, revise, runner, net, parsingLayer) must not use JPA, Spring Data/JDBC, java.sql, H2 or
 *       Hibernate.</li>
 *   <li><b>ENGINE_NO_PORTAL</b>: engine-side packages must not use {@code delivery.portal..} classes.</li>
 * </ul>
 *
 * The allowlist ({@code arch/module-boundary-allowlist.txt}, one {@code RULE|originClass|targetPackage}
 * per line) may only shrink: a new violation fails the build. Stale entries are reported so they get
 * removed as classes move (B05-B08). Regenerate deliberately with {@code -Dkeel.arch.allowlist.write=<path>}.
 */
public class ModuleBoundaryArchTest {

    static final List<String> ENGINE_PACKAGES = List.of(
            "delivery.job", "delivery.authoring", "delivery.heal", "delivery.vision", "delivery.hunt",
            "delivery.codegen", "delivery.revise", "delivery.runner", "delivery.net", "parsingLayer");
    static final List<String> DB_PACKAGES = List.of(
            "jakarta.persistence", "javax.persistence", "org.springframework.data", "org.springframework.jdbc",
            "org.springframework.orm", "java.sql", "javax.sql", "org.h2", "org.hibernate", "com.zaxxer.hikari");

    private static boolean in(String pkg, List<String> roots) {
        return roots.stream().anyMatch(r -> pkg.equals(r) || pkg.startsWith(r + "."));
    }

    static Set<String> violations(JavaClasses classes) {
        Set<String> out = new TreeSet<>();
        for (JavaClass c : classes) {
            String pkg = c.getPackageName();
            boolean portal = in(pkg, List.of("delivery.portal"));
            boolean engine = in(pkg, ENGINE_PACKAGES);
            if (!portal && !engine) {
                continue;
            }
            String origin = c.getName().replaceAll("\\$.*", "");
            for (Dependency d : c.getDirectDependenciesFromSelf()) {
                String t = d.getTargetClass().getPackageName();
                if (portal && in(t, List.of("org.openqa.selenium"))) {
                    out.add("PORTAL_NO_SELENIUM|" + origin + "|org.openqa.selenium");
                }
                if (engine && in(t, DB_PACKAGES)) {
                    String root = DB_PACKAGES.stream().filter(r -> in(t, List.of(r))).findFirst().orElse(t);
                    out.add("ENGINE_NO_DB|" + origin + "|" + root);
                }
                if (engine && in(t, List.of("delivery.portal"))) {
                    out.add("ENGINE_NO_PORTAL|" + origin + "|delivery.portal");
                }
            }
        }
        return out;
    }

    static Set<String> allowlist() throws IOException {
        Set<String> out = new TreeSet<>();
        try (InputStream in = ModuleBoundaryArchTest.class.getResourceAsStream("/arch/module-boundary-allowlist.txt")) {
            if (in == null) {
                return out;
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String l = line.trim();
                if (!l.isEmpty() && !l.startsWith("#")) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    @Test
    public void noNewModuleBoundaryViolations() throws IOException {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("delivery", "parsingLayer");
        Assert.assertTrue(classes.size() > 100, "classpath import looks wrong: " + classes.size());
        Set<String> found = violations(classes);
        String write = System.getProperty("keel.arch.allowlist.write");
        if (write != null && !write.isBlank()) {
            List<String> lines = new ArrayList<>();
            lines.add("# B02 module-boundary allowlist (RULE|originClass|targetPackage). May only shrink.");
            lines.addAll(found);
            Files.write(Path.of(write), lines, StandardCharsets.UTF_8);
        }
        Set<String> allowed = allowlist();
        Set<String> fresh = new TreeSet<>(found);
        fresh.removeAll(allowed);
        Set<String> stale = new TreeSet<>(allowed);
        stale.removeAll(found);
        if (!stale.isEmpty()) {
            System.out.println("[arch] " + stale.size() + " allowlist entries no longer violate; remove them:\n  "
                    + String.join("\n  ", stale));
        }
        Assert.assertTrue(fresh.isEmpty(), "New module-boundary violations (fix, don't allowlist):\n  "
                + String.join("\n  ", fresh));
    }

    @Test
    public void engineNoPortalAllowlistIsEmpty() throws IOException {
        // B07: the last 8 ENGINE_NO_PORTAL entries moved to delivery.portal.jobs (control-plane code).
        Assert.assertTrue(allowlist().stream().noneMatch(l -> l.startsWith("ENGINE_NO_PORTAL|")), allowlist().toString());
    }

    @Test
    public void rulesDetectViolations() {
        // self-check on test-only fixtures, so an empty result can't hide a broken rule
        Set<String> v = violations(new ClassFileImporter().importClasses(
                delivery.portal.archfixture.SeleniumUsingFixture.class,
                delivery.job.archfixture.DbUsingFixture.class));
        Assert.assertTrue(v.contains("PORTAL_NO_SELENIUM|delivery.portal.archfixture.SeleniumUsingFixture|org.openqa.selenium"), v.toString());
        Assert.assertTrue(v.contains("ENGINE_NO_DB|delivery.job.archfixture.DbUsingFixture|java.sql"), v.toString());
        Assert.assertTrue(v.contains("ENGINE_NO_PORTAL|delivery.job.archfixture.DbUsingFixture|delivery.portal"), v.toString());
        Assert.assertFalse(in("delivery.portalx", List.of("delivery.portal")));
    }
}
