package delivery.agent.validation.automate;

import delivery.agent.validation.contract.FrameworkExpectedInventory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Advisory architecture quality for repaired frameworks — separate from mandatory contract v2.
 * A passing inventory report does not imply {@link ArchitectureQualityReport#functionallyComplete()}.
 */
public final class FrameworkRepairArchitectureAssessor {

    private static final Pattern BY_FIELD = Pattern.compile("protected\\s+final\\s+By\\s+\\w+");
    private static final Pattern ACTION_METHOD =
            Pattern.compile("public\\s+void\\s+(?!assert_)[\\w_]+\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern ASSERT_METHOD = Pattern.compile("public\\s+void\\s+assert_[\\w_]+\\s*\\(");
    private static final Pattern EMPTY_TEST_BODY =
            Pattern.compile("@Test\\s+public\\s+void\\s+\\w+\\s*\\(\\s*\\)\\s*\\{\\s*\\}");
    private static final Pattern PAGE_USE =
            Pattern.compile("\\w+\\s*\\.\\s*(click_|type_|assert_|waitFor)");

    private FrameworkRepairArchitectureAssessor() {
    }

    public record ArchitectureFinding(String code, String message, boolean functionalGap) {
    }

    public record ArchitectureQualityReport(
            boolean functionallyComplete,
            List<ArchitectureFinding> findings) {

        public static ArchitectureQualityReport of(List<ArchitectureFinding> findings) {
            boolean gaps = findings.stream().anyMatch(ArchitectureFinding::functionalGap);
            return new ArchitectureQualityReport(!gaps, List.copyOf(findings));
        }
    }

    public static ArchitectureQualityReport assess(Path projectRoot, FrameworkExpectedInventory inventory) {
        List<ArchitectureFinding> findings = new ArrayList<>();
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            findings.add(new ArchitectureFinding("ROOT_MISSING", "project root missing", true));
            return ArchitectureQualityReport.of(findings);
        }
        if (inventory == null) {
            findings.add(new ArchitectureFinding("INVENTORY_MISSING", "no inventory", true));
            return ArchitectureQualityReport.of(findings);
        }

        for (String pageClass : inventory.expectedPageClassNames()) {
            Path page = projectRoot.resolve("src/main/java/project/pages/" + pageClass + ".java");
            if (!Files.isRegularFile(page)) {
                findings.add(new ArchitectureFinding(
                        "PAGE_MISSING",
                        "expected page class file missing: " + pageClass,
                        true));
                continue;
            }
            try {
                String body = Files.readString(page);
                if (!body.contains("package project.pages;")) {
                    findings.add(new ArchitectureFinding("PAGE_PACKAGE", pageClass + " wrong package", true));
                }
                if (!BY_FIELD.matcher(body).find()) {
                    findings.add(new ArchitectureFinding(
                            "PAGE_NO_LOCATORS",
                            pageClass + " has no protected final By locators",
                            true));
                }
                if (!ACTION_METHOD.matcher(body).find()) {
                    findings.add(new ArchitectureFinding(
                            "PAGE_NO_ACTIONS",
                            pageClass + " has no action methods",
                            true));
                }
                int assertExpected = inventory.pages().stream()
                        .filter(p -> p.pageClassName().equals(pageClass))
                        .mapToInt(p -> p.expectedAssertMethods())
                        .findFirst()
                        .orElse(0);
                if (assertExpected > 0 && !ASSERT_METHOD.matcher(body).find()) {
                    findings.add(new ArchitectureFinding(
                            "PAGE_NO_ASSERTIONS",
                            pageClass + " missing assert methods (inventory expects " + assertExpected + ")",
                            true));
                }
            } catch (Exception e) {
                findings.add(new ArchitectureFinding(
                        "PAGE_READ",
                        pageClass + ": " + e.getMessage(),
                        true));
            }
        }

        for (var expectedTest : inventory.tests()) {
            String className = expectedTest.testClassName();
            String subdir = expectedTest.generatedTier() ? "generated" : "todo";
            Path test = projectRoot.resolve(
                    "src/test/java/project/tests/" + subdir + "/" + className + ".java");
            if (!Files.isRegularFile(test)) {
                findings.add(new ArchitectureFinding(
                        "TEST_MISSING",
                        "expected test missing: " + className,
                        true));
                continue;
            }
            try {
                String body = Files.readString(test);
                if (EMPTY_TEST_BODY.matcher(body).find()) {
                    findings.add(new ArchitectureFinding(
                            "TEST_STUB_EMPTY",
                            className + " has empty @Test body — not functional automation",
                            true));
                }
                for (String pageClass : expectedTest.linkedPageClassNames()) {
                    String varName = pageVarHint(pageClass);
                    if (!body.contains(pageClass) && !body.contains(varName)) {
                        findings.add(new ArchitectureFinding(
                                "TEST_PAGE_UNLINKED",
                                className + " does not reference " + pageClass,
                                true));
                    }
                }
                if (expectedTest.generatedTier()
                        && !PAGE_USE.matcher(body).find()
                        && !body.contains("_Page")) {
                    findings.add(new ArchitectureFinding(
                            "TEST_PAGE_UNUSED",
                            className + " does not invoke page actions/assertions",
                            true));
                }
            } catch (Exception e) {
                findings.add(new ArchitectureFinding(
                        "TEST_READ",
                        className + ": " + e.getMessage(),
                        true));
            }
        }
        return ArchitectureQualityReport.of(findings);
    }

    private static String pageVarHint(String pageClass) {
        String base = pageClass.endsWith("_Page") ? pageClass.substring(0, pageClass.length() - 5) : pageClass;
        return Character.toLowerCase(base.charAt(0)) + base.substring(1) + "_Page";
    }
}