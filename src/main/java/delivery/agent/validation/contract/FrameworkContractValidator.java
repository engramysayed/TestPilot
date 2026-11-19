package delivery.agent.validation.contract;

import delivery.codegen.CodegenNaming;
import delivery.store.PreferredHooksStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Read-only deterministic checks for emitted Selenium frameworks: one {@code *_Page} and one {@code *_Test}
 * per page stem under conventional packages, or inventory-driven checks from emit-time {@link FrameworkExpectedInventory}.
 */
public final class FrameworkContractValidator {
  public static final String CONTRACT_VERSION_INVENTORY = "2";

  /** Maven-relative codegen test dirs; matches absolute and relative walked paths. */
  private static final Pattern GENERATED_TEST_DIR = Pattern.compile(
      "(?:^|/)project/tests/(?:generated|todo)/");

  private FrameworkContractValidator() {
  }

  public static ValidationReport validateWorkspace(Path workspaceRoot) {
    return ValidationReport.of(ValidationContractIds.FRAMEWORK_CONTRACT_V1, legacyFindings(workspaceRoot));
  }

  /** Validates generated tree; optional {@code templateRoot} enables helper-reuse heuristics. */
  public static ValidationReport validateWorkspace(Path workspaceRoot, Path templateRoot) {
    return validateWorkspace(workspaceRoot, templateRoot, null);
  }

  /** Inventory-driven validation (emit-time source of truth). */
  public static ValidationReport validateWorkspace(
      Path workspaceRoot, Path templateRoot, FrameworkExpectedInventory inventory) {
    if (inventory == null) {
      ValidationReport base = ValidationReport.of(
          ValidationContractIds.FRAMEWORK_CONTRACT_V1, legacyFindings(workspaceRoot));
      FrameworkTemplateCatalog.Catalog catalog = catalogFor(templateRoot);
      List<ValidationFinding> extra = architectureAndHelperChecks(workspaceRoot, catalog, null);
      return mergeReports(base, extra, ValidationReport.VERSION, Map.of());
    }
    List<ValidationFinding> findings = new ArrayList<>();
    findings.addAll(validateRoot(workspaceRoot));
    Map<String, Path> pageFiles = collectPageFilesByClass(workspaceRoot, findings);
    Map<String, Path> testFiles = collectTestFilesByClass(workspaceRoot, findings);
    findings.addAll(inventoryPresenceChecks(workspaceRoot, inventory, pageFiles, testFiles));
    findings.addAll(inventoryOrphanChecks(workspaceRoot, inventory, pageFiles, testFiles));
    FrameworkTemplateCatalog.Catalog catalog = catalogFor(templateRoot);
    findings.addAll(inventoryArchitectureChecks(workspaceRoot, catalog, inventory, pageFiles, testFiles));
    findings.addAll(inventoryNamingChecks(workspaceRoot, inventory, pageFiles, testFiles));
    findings.addAll(userRulesChecks(workspaceRoot, pageFiles, testFiles));
    Map<String, String> meta = new LinkedHashMap<>(inventory.toReportMetadata());
    meta.put("contractSemantics", "inventory-v2");
    return ValidationReport.of(
        ValidationContractIds.FRAMEWORK_CONTRACT_V1,
        CONTRACT_VERSION_INVENTORY,
        findings,
        meta);
  }

  private static List<ValidationFinding> legacyFindings(Path workspaceRoot) {
    if (workspaceRoot == null || !Files.isDirectory(workspaceRoot)) {
      return List.of(new ValidationFinding(
          "FW_STRUCT_ROOT",
          "Framework workspace root is missing",
          ValidationSeverity.MANDATORY,
          "framework",
          "",
          null,
          "",
          "Point validation at the isolated framework tree."));
    }
    List<ValidationFinding> findings = new ArrayList<>();
    Map<String, Path> pages = new HashMap<>();
    Map<String, Path> tests = new HashMap<>();
    collectBySuffix(workspaceRoot, "_Page.java", pages, findings);
    collectBySuffix(workspaceRoot, "_Test.java", tests, findings);

    Set<String> allStems = new HashSet<>();
    allStems.addAll(pages.keySet());
    allStems.addAll(tests.keySet());

    for (String stem : allStems) {
      boolean hasPage = pages.containsKey(stem);
      boolean hasTest = tests.containsKey(stem);
      if (hasPage && !hasTest) {
        findings.add(missingPair(stem, "test", pages.get(stem)));
      } else if (hasTest && !hasPage) {
        findings.add(missingPair(stem, "page", tests.get(stem)));
      }
    }

    for (Map.Entry<String, Path> e : pages.entrySet()) {
      findings.addAll(legacyNamingChecks(e.getKey(), e.getValue(), "Page"));
    }
    for (Map.Entry<String, Path> e : tests.entrySet()) {
      findings.addAll(legacyNamingChecks(e.getKey(), e.getValue(), "Test"));
    }

    if (pages.isEmpty() && tests.isEmpty()) {
      findings.add(new ValidationFinding(
          "FW_ARCH_EMPTY",
          "No generated *_Page.java or *_Test.java sources found under workspace",
          ValidationSeverity.RECOMMENDED,
          "framework",
          relativize(workspaceRoot, workspaceRoot),
          null,
          "",
          "Ensure validation runs after EmitPhase codegen."));
    }
    return findings;
  }

  private static ValidationReport mergeReports(
      ValidationReport base, List<ValidationFinding> extra, String version, Map<String, String> meta) {
    if (extra == null || extra.isEmpty()) {
      return base;
    }
    List<ValidationFinding> merged = new ArrayList<>(base.findings());
    merged.addAll(extra);
    return ValidationReport.of(ValidationContractIds.FRAMEWORK_CONTRACT_V1, version, merged, meta);
  }

  private static FrameworkTemplateCatalog.Catalog catalogFor(Path templateRoot) {
    return (templateRoot != null && Files.isDirectory(templateRoot))
        ? FrameworkTemplateCatalog.load(templateRoot)
        : new FrameworkTemplateCatalog.Catalog(List.of());
  }

  private static List<ValidationFinding> validateRoot(Path workspaceRoot) {
    if (workspaceRoot == null || !Files.isDirectory(workspaceRoot)) {
      return List.of(new ValidationFinding(
          "FW_STRUCT_ROOT",
          "Framework workspace root is missing",
          ValidationSeverity.MANDATORY,
          "framework",
          "",
          null,
          "",
          "Point validation at the isolated framework tree."));
    }
    return List.of();
  }

  private static Map<String, Path> collectPageFilesByClass(Path root, List<ValidationFinding> findings) {
    Map<String, Path> byClass = new LinkedHashMap<>();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(p -> Files.isRegularFile(p)
              && p.getFileName().toString().endsWith("_Page.java")
              && p.toString().replace('\\', '/').contains("/project/pages/"))
          .forEach(p -> {
            String className = p.getFileName().toString().replace(".java", "");
            if (byClass.putIfAbsent(className, p) != null) {
              findings.add(new ValidationFinding(
                  "FW_ARCH_DUPLICATE_PAGE",
                  "Duplicate page class '" + className + "'",
                  ValidationSeverity.MANDATORY,
                  className,
                  relativize(root, p),
                  null,
                  "existing=" + relativize(root, byClass.get(className)),
                  "Emit must produce at most one Page class per page."));
            }
          });
    } catch (IOException e) {
      findings.add(ioFinding(root, e));
    }
    return byClass;
  }

  private static Map<String, Path> collectTestFilesByClass(Path root, List<ValidationFinding> findings) {
    Map<String, Path> byClass = new LinkedHashMap<>();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(p -> Files.isRegularFile(p) && isGeneratedTestFile(p)).forEach(p -> {
        String className = p.getFileName().toString().replace(".java", "");
        if (byClass.putIfAbsent(className, p) != null) {
          findings.add(new ValidationFinding(
              "FW_ARCH_DUPLICATE_TEST",
              "Duplicate test class '" + className + "'",
              ValidationSeverity.MANDATORY,
              className,
              relativize(root, p),
              null,
              "existing=" + relativize(root, byClass.get(className)),
              "Emit must produce at most one Test class per grouped TC."));
        }
      });
    } catch (IOException e) {
      findings.add(ioFinding(root, e));
    }
    return byClass;
  }

  private static boolean isGeneratedTestFile(Path p) {
    String path = p.normalize().toString().replace('\\', '/');
    // Codegen emits under generated/ or todo/; template keeps BaseTest at project/tests/ root.
    if (!GENERATED_TEST_DIR.matcher(path).find()) {
      return false;
    }
    String name = p.getFileName().toString();
    return name.endsWith("_Test.java") || name.endsWith("TestTodo.java");
  }

  private static List<ValidationFinding> inventoryPresenceChecks(
      Path root,
      FrameworkExpectedInventory inventory,
      Map<String, Path> pageFiles,
      Map<String, Path> testFiles) {
    List<ValidationFinding> findings = new ArrayList<>();
    for (FrameworkExpectedInventory.ExpectedPage page : inventory.pages()) {
      if (!pageFiles.containsKey(page.pageClassName())) {
        findings.add(new ValidationFinding(
            "FW_EXPECTED_PAGE_PRESENT",
            "Expected page class '" + page.pageClassName() + "' (stem " + page.stem() + ") was not emitted",
            ValidationSeverity.MANDATORY,
            page.pageClassName(),
            "src/main/java/project/pages/" + page.pageClassName() + ".java",
            null,
            "stem=" + page.stem(),
            "Re-run emit or repair missing Page class."));
      }
    }
    for (FrameworkExpectedInventory.ExpectedTest test : inventory.tests()) {
      if (!testFiles.containsKey(test.testClassName())) {
        findings.add(new ValidationFinding(
            "FW_EXPECTED_TEST_PRESENT",
            "Expected test class '" + test.testClassName() + "' was not emitted",
            ValidationSeverity.MANDATORY,
            test.testClassName(),
            (test.generatedTier() ? "src/test/java/project/tests/generated/" : "src/test/java/project/tests/todo/")
                + test.testClassName()
                + ".java",
            null,
            "linkedPages=" + test.linkedPageClassNames() + " tcs=" + test.tcIds(),
            "Re-run emit or repair missing Test class."));
      }
    }
    Map<String, Integer> testsPerPage = new HashMap<>();
    for (FrameworkExpectedInventory.ExpectedTest t : inventory.tests()) {
      for (String pageClass : t.linkedPageClassNames()) {
        testsPerPage.merge(pageClass, 1, Integer::sum);
      }
    }
    for (FrameworkExpectedInventory.ExpectedPage page : inventory.pages()) {
      if (!testsPerPage.containsKey(page.pageClassName())) {
        findings.add(new ValidationFinding(
            "FW_EXPECTED_PAGE_TEST_LINK",
            "No emitted test class references expected page '" + page.pageClassName() + "'",
            ValidationSeverity.MANDATORY,
            page.pageClassName(),
            "",
            null,
            "stem=" + page.stem(),
            "Every web page stem must have at least one Test class from codegen."));
      }
    }
    return findings;
  }

  private static List<ValidationFinding> inventoryOrphanChecks(
      Path root,
      FrameworkExpectedInventory inventory,
      Map<String, Path> pageFiles,
      Map<String, Path> testFiles) {
    Set<String> expectedPages = inventory.expectedPageClassNames();
    Set<String> expectedTests = inventory.expectedTestClassNames();
    List<ValidationFinding> findings = new ArrayList<>();
    for (Map.Entry<String, Path> e : pageFiles.entrySet()) {
      if (!expectedPages.contains(e.getKey())) {
        findings.add(new ValidationFinding(
            "FW_ORPHAN_PAGE_CLASS",
            "Page class '" + e.getKey() + "' is not in emit-time inventory",
            ValidationSeverity.MANDATORY,
            e.getKey(),
            relativize(root, e.getValue()),
            null,
            "",
            "Remove stray Page sources or fix clustering."));
      }
    }
    for (Map.Entry<String, Path> e : testFiles.entrySet()) {
      if (!expectedTests.contains(e.getKey())) {
        findings.add(new ValidationFinding(
            "FW_ORPHAN_TEST_CLASS",
            "Test class '" + e.getKey() + "' is not in emit-time inventory",
            ValidationSeverity.MANDATORY,
            e.getKey(),
            relativize(root, e.getValue()),
            null,
            "",
            "Remove stray Test sources or fix codegen grouping."));
      }
    }
    return findings;
  }

  private static List<ValidationFinding> inventoryArchitectureChecks(
      Path root,
      FrameworkTemplateCatalog.Catalog catalog,
      FrameworkExpectedInventory inventory,
      Map<String, Path> pageFiles,
      Map<String, Path> testFiles) {
    List<ValidationFinding> findings = new ArrayList<>();
    for (FrameworkExpectedInventory.ExpectedPage expected : inventory.pages()) {
      Path file = pageFiles.get(expected.pageClassName());
      if (file == null) {
        continue;
      }
      String body = readQuiet(file);
      GeneratedJavaStructure.PageShape shape = GeneratedJavaStructure.inspectPage(body);
      if (expected.expectedLocatorFields() > 0 && shape.locatorFields() == 0) {
        findings.add(new ValidationFinding(
            "FW_PAGE_MISSING_LOCATORS",
            "Page '" + expected.pageClassName() + "' has no locator fields",
            ValidationSeverity.MANDATORY,
            expected.pageClassName(),
            relativize(root, file),
            null,
            "expectedLocators=" + expected.expectedLocatorFields(),
            "Page classes must own page-specific By locators."));
      }
      if (expected.expectedActionMethods() > 0 && shape.actionMethods() == 0) {
        findings.add(new ValidationFinding(
            "FW_PAGE_MISSING_ACTIONS",
            "Page '" + expected.pageClassName() + "' has no action methods",
            ValidationSeverity.MANDATORY,
            expected.pageClassName(),
            relativize(root, file),
            null,
            "expectedActions=" + expected.expectedActionMethods(),
            "Page classes must own page-specific actions."));
      }
      if (expected.expectedAssertMethods() > 0 && shape.assertMethods() == 0) {
        findings.add(new ValidationFinding(
            "FW_PAGE_MISSING_ASSERTIONS",
            "Page '" + expected.pageClassName() + "' has no assertion methods",
            ValidationSeverity.MANDATORY,
            expected.pageClassName(),
            relativize(root, file),
            null,
            "expectedAsserts=" + expected.expectedAssertMethods(),
            "Page classes must own page-specific assertions."));
      }
      findings.addAll(helperHeuristics(root, file, catalog));
    }
    for (FrameworkExpectedInventory.ExpectedTest expected : inventory.tests()) {
      Path file = testFiles.get(expected.testClassName());
      if (file == null) {
        continue;
      }
      String body = readQuiet(file);
      if (!body.contains("extends BaseTest")) {
        findings.add(new ValidationFinding(
            "FW_TEST_BASE_CLASS",
            "Generated test must extend project.tests.BaseTest",
            ValidationSeverity.MANDATORY,
            expected.testClassName(),
            relativize(root, file),
            null,
            "template=GeneratedTest.java.ftl",
            "Extend BaseTest and keep @BeforeMethod login prelude pattern."));
      }
      GeneratedJavaStructure.TestShape shape = GeneratedJavaStructure.inspectTest(body);
      List<GeneratedJavaStructure.TestPageField> pageFields = GeneratedJavaStructure.inspectTestPageFields(body);
      if (expected.linkedPageClassNames().isEmpty()) {
        if (pageFields.stream().anyMatch(p -> p.pageClass().endsWith("_Page"))) {
          findings.add(new ValidationFinding(
              "FW_TEST_USES_PAGE",
              "Test declares page fields but inventory expects no page imports (e.g. demoted stem)",
              ValidationSeverity.MANDATORY,
              expected.testClassName(),
              relativize(root, file),
              null,
              "",
              "Remove stray page fields or fix demotion inventory."));
        }
      } else {
        for (String pageClass : expected.linkedPageClassNames()) {
          String importLine = "import project.pages." + pageClass + ";";
          if (!body.contains(importLine)) {
            findings.add(new ValidationFinding(
                "FW_TEST_USES_PAGE",
                "Test must import matching page class " + pageClass,
                ValidationSeverity.MANDATORY,
                expected.testClassName(),
                relativize(root, file),
                null,
                "expectedImport=" + importLine,
                "Import and instantiate the page under test."));
          }
          String expectedVar = delivery.codegen.CodegenNaming.safeLocalVarName(pageClass);
          boolean hasField = pageFields.stream().anyMatch(
              p -> pageClass.equals(p.pageClass()) && expectedVar.equals(p.varName()));
          if (!hasField) {
            findings.add(new ValidationFinding(
                "FW_TEST_PAGE_FIELD",
                "Test should declare a private " + pageClass + " " + expectedVar + " field",
                ValidationSeverity.MANDATORY,
                expected.testClassName(),
                relativize(root, file),
                null,
                "expectedVar=" + expectedVar,
                "Follow GeneratedTest.java.ftl pageFields pattern."));
          }
          int usages = pageFields.stream()
                  .filter(p -> pageClass.equals(p.pageClass()))
                  .mapToInt(GeneratedJavaStructure.TestPageField::varUsages)
                  .sum();
          if (usages == 0 && !shape.testMethods().isEmpty()) {
            findings.add(new ValidationFinding(
                "FW_TEST_PAGE_UNUSED",
                "Test does not invoke page " + pageClass + " in test methods",
                ValidationSeverity.MANDATORY,
                expected.testClassName(),
                relativize(root, file),
                null,
                "pageVar=" + expectedVar,
                "Call page action/assert methods from @Test bodies."));
          }
        }
      }
      for (String stub : shape.stubTestMethods()) {
        findings.add(new ValidationFinding(
            "FW_TEST_STUB_EMPTY",
            "@Test method '" + stub + "' is an empty stub",
            ValidationSeverity.MANDATORY,
            expected.testClassName(),
            relativize(root, file),
            null,
            "method=" + stub,
            "Implement proven steps or leave TODO tier emit."));
      }
      findings.addAll(helperHeuristics(root, file, catalog));
    }
    return findings;
  }

  private static List<ValidationFinding> inventoryNamingChecks(
      Path root,
      FrameworkExpectedInventory inventory,
      Map<String, Path> pageFiles,
      Map<String, Path> testFiles) {
    List<ValidationFinding> findings = new ArrayList<>();
    for (FrameworkExpectedInventory.ExpectedPage page : inventory.pages()) {
      Path file = pageFiles.get(page.pageClassName());
      if (file == null) {
        continue;
      }
      String expectedName = CodegenNaming.pageClassNameFromStem(page.stem());
      if (!page.pageClassName().equals(expectedName)) {
        findings.add(new ValidationFinding(
            "FW_NAME_PAGE_CLASS",
            "Page class name '" + page.pageClassName() + "' does not match CodegenNaming for stem",
            ValidationSeverity.MANDATORY,
            page.pageClassName(),
            relativize(root, file),
            null,
            "expected=" + expectedName,
            "Align page class with CodegenNaming.pageClassNameFromStem."));
      }
      String body = readQuiet(file);
      GeneratedJavaStructure.PageShape shape = GeneratedJavaStructure.inspectPage(body);
      for (String loc : shape.locatorNames()) {
        if (!GeneratedJavaStructure.locatorNameMatchesConvention(loc)) {
          findings.add(new ValidationFinding(
              "FW_NAME_LOCATOR",
              "Locator field '" + loc + "' does not follow CodegenNaming locator suffix",
              ValidationSeverity.RECOMMENDED,
              page.pageClassName(),
              relativize(root, file),
              null,
              "field=" + loc,
              "Use *_Locator / *_Btn_Locator style from codegen."));
        }
      }
    }
    for (FrameworkExpectedInventory.ExpectedTest test : inventory.tests()) {
      Path file = testFiles.get(test.testClassName());
      if (file == null) {
        continue;
      }
      if (!file.getFileName().toString().replace(".java", "").equals(test.testClassName())) {
        findings.add(new ValidationFinding(
            "FW_NAME_TEST_CLASS",
            "Test file name does not match expected class " + test.testClassName(),
            ValidationSeverity.RECOMMENDED,
            test.testClassName(),
            relativize(root, file),
            null,
            "",
            "Match CodegenTestClasses class name."));
      }
    }
    return findings;
  }

  private static void collectBySuffix(
      Path root,
      String suffix,
      Map<String, Path> sink,
      List<ValidationFinding> findings) {
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(suffix))
          .forEach(p -> {
            String name = p.getFileName().toString();
            String stem = name.substring(0, name.length() - suffix.length());
            if (sink.putIfAbsent(stem, p) != null) {
              findings.add(new ValidationFinding(
                  "FW_ARCH_DUPLICATE_STEM",
                  "Duplicate framework stem '" + stem + "' for " + suffix,
                  ValidationSeverity.MANDATORY,
                  "class:" + stem,
                  relativize(root, p),
                  null,
                  "Existing=" + relativize(root, sink.get(stem)) + " duplicate=" + relativize(root, p),
                  "Emit must produce at most one " + suffix + " per page stem."));
            }
          });
    } catch (IOException e) {
      findings.add(ioFinding(root, e));
    }
  }

  private static ValidationFinding ioFinding(Path root, IOException e) {
    return new ValidationFinding(
        "FW_IO",
        "Failed to walk framework workspace: " + e.getMessage(),
        ValidationSeverity.MANDATORY,
        "framework",
        relativize(root, root),
        null,
        "",
        "Fix workspace permissions or path.");
  }

  private static ValidationFinding missingPair(String stem, String missingKind, Path presentFile) {
    return new ValidationFinding(
        "FW_ARCH_PAGE_TEST_PAIR",
        "Stem '" + stem + "' missing matching *_" + missingKind + ".java",
        ValidationSeverity.MANDATORY,
        "page-stem:" + stem,
        presentFile == null ? "" : presentFile.getFileName().toString(),
        null,
        "expected one Page class and one Test class per web page",
        "Add the missing generated class or fix page clustering.");
  }

  private static List<ValidationFinding> legacyNamingChecks(String stem, Path file, String kind) {
    List<ValidationFinding> out = new ArrayList<>();
    String name = file.getFileName().toString().replace(".java", "");
    if (!name.matches("^[A-Z][A-Za-z0-9_]*_" + kind + "$")) {
      out.add(new ValidationFinding(
          "FW_NAME_CLASS",
          "Class name '" + name + "' does not match PascalCase_*_" + kind,
          ValidationSeverity.RECOMMENDED,
          name,
          file.getFileName().toString(),
          null,
          "stem=" + stem,
          "Follow CodegenNaming page/test class conventions."));
    }
    String lowerStem = stem.toLowerCase(Locale.ROOT);
    if (lowerStem.contains("page") || lowerStem.contains("test")) {
      out.add(new ValidationFinding(
          "FW_NAME_STEM_NOISE",
          "Stem '" + stem + "' embeds 'Page'/'Test' — may indicate double suffixing",
          ValidationSeverity.RECOMMENDED,
          name,
          file.getFileName().toString(),
          null,
          "",
          "Use CodegenNaming stem without redundant Page/Test tokens."));
    }
    return out;
  }

  private static String relativize(Path root, Path file) {
    try {
      return root.relativize(file).toString().replace('\\', '/');
    } catch (Exception e) {
      return file == null ? "" : file.getFileName().toString();
    }
  }

  private static List<ValidationFinding> architectureAndHelperChecks(
      Path workspaceRoot, FrameworkTemplateCatalog.Catalog catalog, FrameworkExpectedInventory inventory) {
    if (inventory != null) {
      return List.of();
    }
    List<ValidationFinding> findings = new ArrayList<>();
    Map<String, Path> pages = new HashMap<>();
    Map<String, Path> tests = new HashMap<>();
    collectBySuffix(workspaceRoot, "_Page.java", pages, findings);
    collectBySuffix(workspaceRoot, "_Test.java", tests, findings);

    for (Map.Entry<String, Path> e : tests.entrySet()) {
      String stem = e.getKey();
      Path testFile = e.getValue();
      String body = readQuiet(testFile);
      if (!body.contains("extends BaseTest")) {
        findings.add(new ValidationFinding(
            "FW_TEST_BASE_CLASS",
            "Generated test must extend project.tests.BaseTest",
            ValidationSeverity.MANDATORY,
            testFile.getFileName().toString(),
            relativize(workspaceRoot, testFile),
            null,
            "template=GeneratedTest.java.ftl",
            "Extend BaseTest and keep @BeforeMethod login prelude pattern."));
      }
      String expectedImport = "import project.pages." + stem + "_Page;";
      if (pages.containsKey(stem) && !body.contains(expectedImport)) {
        findings.add(new ValidationFinding(
            "FW_TEST_USES_PAGE",
            "Test should import and use the matching page class",
            ValidationSeverity.MANDATORY,
            testFile.getFileName().toString(),
            relativize(workspaceRoot, testFile),
            null,
            "expectedImport=" + expectedImport,
            "Instantiate " + stem + "_Page in test methods."));
      }
      if (body.contains("@Test") && !body.matches("(?s).*\\bvoid\\s+\\w+\\s*\\(\\s*\\).*")) {
        findings.add(new ValidationFinding(
            "FW_TEST_METHOD_SHAPE",
            "Test class should declare at least one TestNG @Test method",
            ValidationSeverity.RECOMMENDED,
            testFile.getFileName().toString(),
            relativize(workspaceRoot, testFile),
            null,
            "",
            "Follow GeneratedTest.java.ftl method naming."));
      }
    }

    for (Path generated : List.copyOf(pages.values())) {
      findings.addAll(helperHeuristics(workspaceRoot, generated, catalog));
    }
    for (Path generated : List.copyOf(tests.values())) {
      findings.addAll(helperHeuristics(workspaceRoot, generated, catalog));
    }
    findings.addAll(userRulesChecks(workspaceRoot, pages, tests));
    return findings;
  }

  /**
   * Deterministic checks for the workspace-owner's "framework style" rules (one Test class per TC,
   * reuse Actions utilities, assertions take locators, strict locator ladder, local reuse of literals).
   * All findings are RECOMMENDED so they flag without blocking emit; the agent repair phase can
   * promote them to MANDATORY if the project policy chooses to.
   */
  private static List<ValidationFinding> userRulesChecks(
      Path root, Map<String, Path> pageFiles, Map<String, Path> testFiles) {
    List<ValidationFinding> out = new ArrayList<>();
    List<String> userHooks = PreferredHooksStore.current();
    for (Map.Entry<String, Path> e : testFiles.entrySet()) {
      String body = readQuiet(e.getValue());
      out.addAll(ruleRawDriverInTest(root, e.getKey(), e.getValue(), body));
      out.addAll(ruleAssertScansHtml(root, e.getKey(), e.getValue(), body));
      out.addAll(ruleDuplicateLiterals(root, e.getKey(), e.getValue(), body));
      out.addAll(ruleTestNoRawBy(root, e.getKey(), e.getValue(), body));
      out.addAll(ruleTestDescriptionPresent(root, e.getKey(), e.getValue(), body));
      out.addAll(ruleNoSysout(root, e.getKey(), e.getValue(), body));
      out.addAll(ruleTestMethodNaming(root, e.getKey(), e.getValue(), body));
    }
    for (Map.Entry<String, Path> e : pageFiles.entrySet()) {
      String body = readQuiet(e.getValue());
      out.addAll(ruleComplexLocators(root, e.getKey(), e.getValue(), body, userHooks));
      out.addAll(ruleNoStepOnValueMethod(root, e.getKey(), e.getValue(), body));
      out.addAll(ruleNoSysout(root, e.getKey(), e.getValue(), body));
      out.addAll(ruleLocatorFieldVisibility(root, e.getKey(), e.getValue(), body));
    }
    return out;
  }

  /* --- Rule 3a: tests must reuse Actions classes, not raw driver calls --- */
  private static final Pattern RAW_DRIVER_FIND = Pattern.compile(
      "\\bdriver\\.(findElement|findElements)\\s*\\(|\\bnew\\s+WebDriverWait\\s*\\(|\\bnew\\s+org\\.openqa\\.selenium\\.interactions\\.Actions\\s*\\(");

  private static List<ValidationFinding> ruleRawDriverInTest(
      Path root, String cls, Path file, String body) {
    Matcher m = RAW_DRIVER_FIND.matcher(body);
    List<ValidationFinding> out = new ArrayList<>();
    if (m.find()) {
      out.add(new ValidationFinding(
          "FW_RULE_REUSE_ACTIONS",
          "Test '" + cls + "' bypasses the Actions utilities with " + m.group()
              + " — call ElementsHandler / BrowserHandler / WaitHandler instead",
          ValidationSeverity.RECOMMENDED,
          cls,
          relativize(root, file),
          lineOf(body, m.start()),
          "at offset " + m.start(),
          "Reuse project.utils.Actions.* rather than raw WebDriver calls in tests."));
    }
    return out;
  }

  /* --- Rule 3c: assertions must take a locator, not scan the whole HTML --- */
  private static final Pattern HTML_SCAN = Pattern.compile(
      "\\bdriver\\.(getPageSource|getTitle|getCurrentUrl)\\s*\\(\\s*\\)");
  private static final Pattern BODY_TEXT_ASSERT = Pattern.compile(
      "\\bbodyTextContains\\s*\\(");

  private static List<ValidationFinding> ruleAssertScansHtml(
      Path root, String cls, Path file, String body) {
    List<ValidationFinding> out = new ArrayList<>();
    Matcher m = HTML_SCAN.matcher(body);
    if (m.find()) {
      out.add(new ValidationFinding(
          "FW_RULE_ASSERT_TAKES_LOCATOR",
          "Test '" + cls + "' asserts on " + m.group() + " — assertions must take a locator and compare expected vs actual, not scan the whole page",
          ValidationSeverity.MANDATORY,
          cls,
          relativize(root, file),
          lineOf(body, m.start()),
          m.group(),
          "Add a page element and assert against its text/value/visibility with a By."));
    }
    Matcher b = BODY_TEXT_ASSERT.matcher(body);
    if (b.find()) {
      out.add(new ValidationFinding(
          "FW_RULE_ASSERT_TAKES_LOCATOR",
          "Test '" + cls + "' uses bodyTextContains — assertions must target a specific locator",
          ValidationSeverity.MANDATORY,
          cls,
          relativize(root, file),
          lineOf(body, b.start()),
          "bodyTextContains",
          "Replace with elementTextEquals(By, expected) or textContains(By, expected)."));
    }
    return out;
  }

  /* --- Rule: @Step on a method taking a String arg leaks the arg via Allure's aspect weaver --- */
  private static final Pattern STEP_ON_VALUE_METHOD = Pattern.compile(
      "@(?:io\\.qameta\\.allure\\.)?Step\\s*\\([^)]*\\)\\s*(?:public|protected|private)?\\s*\\w[\\w.<>\\[\\]]*\\s+\\w+\\s*\\(\\s*(?:final\\s+)?String\\b");

  private static List<ValidationFinding> ruleNoStepOnValueMethod(
      Path root, String cls, Path file, String body) {
    List<ValidationFinding> out = new ArrayList<>();
    Matcher m = STEP_ON_VALUE_METHOD.matcher(body);
    while (m.find()) {
      out.add(new ValidationFinding(
          "FW_RULE_NO_STEP_ON_VALUE_METHODS",
          "@Step on a method taking a String arg — Allure's aspect weaver will log that arg. Remove @Step; keep the body inside AllureSteps.run(name, Runnable) so Secrets.redact applies.",
          ValidationSeverity.MANDATORY,
          cls,
          relativize(root, file),
          lineOf(body, m.start()),
          truncate(m.group(), 80),
          "Delete the @Step annotation on this method."));
    }
    return out;
  }

  /* --- Rule 3a extension: tests must not instantiate raw Selenium By in test bodies --- */
  private static final Pattern RAW_BY_IN_TEST = Pattern.compile(
      "\\bBy\\.(cssSelector|xpath|id|name|className|tagName|linkText|partialLinkText)\\s*\\(");

  private static List<ValidationFinding> ruleTestNoRawBy(
      Path root, String cls, Path file, String body) {
    List<ValidationFinding> out = new ArrayList<>();
    Matcher m = RAW_BY_IN_TEST.matcher(body);
    if (m.find()) {
      out.add(new ValidationFinding(
          "FW_RULE_TEST_NO_RAW_BY",
          "Test '" + cls + "' instantiates " + m.group() + " directly — tests must call page methods only; locators belong in page classes",
          ValidationSeverity.RECOMMENDED,
          cls,
          relativize(root, file),
          lineOf(body, m.start()),
          m.group(),
          "Move the locator to the page class and call a page method from the test."));
    }
    return out;
  }

  /* --- Rule: every generated @Test must declare a non-blank description --- */
  private static final Pattern TEST_DESCRIPTION = Pattern.compile(
      "@Test\\s*\\(([^)]*)\\)");

  private static List<ValidationFinding> ruleTestDescriptionPresent(
      Path root, String cls, Path file, String body) {
    List<ValidationFinding> out = new ArrayList<>();
    Matcher m = TEST_DESCRIPTION.matcher(body);
    while (m.find()) {
      String args = m.group(1);
      if (args == null || !args.contains("description")) {
        out.add(new ValidationFinding(
            "FW_RULE_TEST_DESC_REQUIRED",
            "@Test in '" + cls + "' has no description — every generated test must carry a non-blank description from the TC",
            ValidationSeverity.RECOMMENDED,
            cls,
            relativize(root, file),
            lineOf(body, m.start()),
            truncate(m.group(), 80),
            "Add description = \"<TC title/summary>\" to the @Test annotation."));
        continue;
      }
      Matcher descMatcher = Pattern.compile("description\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(args);
      if (descMatcher.find() && descMatcher.group(1).trim().isEmpty()) {
        out.add(new ValidationFinding(
            "FW_RULE_TEST_DESC_REQUIRED",
            "@Test in '" + cls + "' has an empty description",
            ValidationSeverity.RECOMMENDED,
            cls,
            relativize(root, file),
            lineOf(body, m.start()),
            truncate(m.group(), 80),
            "Populate description with the TC's title or summary."));
      }
    }
    return out;
  }

  /* --- Rule: @Test methods must be named should_<snake_case> mirroring the TC title --- */
  private static final Pattern TEST_METHOD_NAME = Pattern.compile(
      "@Test\\s*(?:\\([^)]*\\))?\\s*(?:public|protected|private)?\\s*(?:static\\s+)?\\w[\\w.<>\\[\\]]*\\s+(\\w+)\\s*\\(");
  private static final Pattern VALID_TEST_METHOD_NAME = Pattern.compile(
      "^should(?:_[a-z0-9]+)+$");

  private static List<ValidationFinding> ruleTestMethodNaming(
      Path root, String cls, Path file, String body) {
    List<ValidationFinding> out = new ArrayList<>();
    Matcher m = TEST_METHOD_NAME.matcher(body);
    while (m.find()) {
      String name = m.group(1);
      if (name == null || name.isEmpty()) {
        continue;
      }
      if (VALID_TEST_METHOD_NAME.matcher(name).matches()) {
        continue;
      }
      out.add(new ValidationFinding(
          "FW_RULE_TEST_METHOD_NAMING",
          "@Test method '" + name + "' in '" + cls + "' does not follow should_<snake_case> naming from the TC title",
          ValidationSeverity.RECOMMENDED,
          cls,
          relativize(root, file),
          lineOf(body, m.start()),
          "method=" + name,
          "Rename to `should_<tc_title_as_snake_case>`, e.g. should_login_with_valid_credentials."));
    }
    return out;
  }

  /* --- Rule: no System.out / System.err in generated code — use LogsManager --- */
  private static final Pattern SYSOUT = Pattern.compile(
      "\\bSystem\\.(out|err)\\.(print|println|printf)\\s*\\(");

  private static List<ValidationFinding> ruleNoSysout(
      Path root, String cls, Path file, String body) {
    List<ValidationFinding> out = new ArrayList<>();
    Matcher m = SYSOUT.matcher(body);
    if (m.find()) {
      out.add(new ValidationFinding(
          "FW_RULE_NO_SYSOUT",
          "'" + cls + "' uses " + m.group() + " — generated code must log via LogsManager, not System.out/err",
          ValidationSeverity.RECOMMENDED,
          cls,
          relativize(root, file),
          lineOf(body, m.start()),
          m.group(),
          "Replace with project.utils.Logs.LogsManager.info / warn / error."));
    }
    return out;
  }

  /* --- Rule: page locator By fields must be declared `protected final By` --- */
  // (?m) + (?:^|[;{]) anchors each match to a statement start — the beginning of a line, OR right
  // after a semicolon or opening brace. That catches both one-per-line declarations and the
  // mid-line case `protected final By a = …; public By b = …;` where the second slips past a
  // plain `^\s*` anchor.
  private static final Pattern BY_FIELD_DECL = Pattern.compile(
      "(?m)(?:^|[;{])\\s*((?:public|protected|private)\\s+)?(?:static\\s+)?((?:final\\s+)?)By\\s+(\\w+)\\s*[=;]");

  /**
   * True when {@code pos} is inside a class body (not a method / constructor body). Walks back
   * through balanced braces and, at the nearest enclosing unmatched {@code '{'}, checks whether
   * that brace is preceded by {@code ')'} (method sig) or something else (class / init block).
   */
  static boolean isInClassScope(String body, int pos) {
    int depth = 0;
    for (int i = pos - 1; i >= 0; i--) {
      char c = body.charAt(i);
      if (c == '}') {
        depth++;
      } else if (c == '{') {
        if (depth == 0) {
          int j = i - 1;
          while (j >= 0 && Character.isWhitespace(body.charAt(j))) {
            j--;
          }
          return j < 0 || body.charAt(j) != ')';
        }
        depth--;
      }
    }
    return true;
  }

  private static List<ValidationFinding> ruleLocatorFieldVisibility(
      Path root, String cls, Path file, String body) {
    List<ValidationFinding> out = new ArrayList<>();
    Matcher m = BY_FIELD_DECL.matcher(body);
    while (m.find()) {
      // Skip local `By` variables inside method/constructor bodies — rule is for class fields only.
      if (!isInClassScope(body, m.start())) {
        continue;
      }
      String visibility = m.group(1) == null ? "" : m.group(1).trim();
      String finality = m.group(2) == null ? "" : m.group(2).trim();
      String fieldName = m.group(3);
      if ("protected".equals(visibility) && "final".equals(finality)) {
        continue;
      }
      out.add(new ValidationFinding(
          "FW_RULE_LOCATOR_FIELD_VISIBILITY",
          "Page '" + cls + "' declares locator field '" + fieldName + "' as '"
              + (visibility.isEmpty() ? "<package-private>" : visibility) + " "
              + (finality.isEmpty() ? "<non-final>" : finality)
              + "' — By fields must be `protected final By`",
          ValidationSeverity.RECOMMENDED,
          cls,
          relativize(root, file),
          lineOf(body, m.start()),
          truncate(m.group().trim(), 80),
          "Change the declaration to `protected final By " + fieldName + " = …;`."));
    }
    return out;
  }

  /* --- Rule 2: literals that repeat in one test class should become a private field --- */
  private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\\\n]|\\\\.){3,}\"");

  private static List<ValidationFinding> ruleDuplicateLiterals(
      Path root, String cls, Path file, String body) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    Map<String, Integer> firstOffsets = new LinkedHashMap<>();
    Matcher m = STRING_LITERAL.matcher(body);
    while (m.find()) {
      String literal = m.group();
      if (literal.length() < 5 || literal.length() > 200) continue;
      counts.merge(literal, 1, Integer::sum);
      firstOffsets.putIfAbsent(literal, m.start());
    }
    List<ValidationFinding> out = new ArrayList<>();
    for (Map.Entry<String, Integer> e : counts.entrySet()) {
      if (e.getValue() < 2) continue;
      String lit = e.getKey();
      out.add(new ValidationFinding(
          "FW_RULE_LOCAL_REUSE",
          "Literal " + lit + " appears " + e.getValue() + "× in '" + cls + "' — extract to a private field",
          ValidationSeverity.RECOMMENDED,
          cls,
          relativize(root, file),
          lineOf(body, firstOffsets.get(lit)),
          "occurrences=" + e.getValue(),
          "Declare `private String <name> = " + lit + ";` and reference it from each call site."));
    }
    return out;
  }

  /* --- Rule 4: strict locator ladder — hook → id → name → simple CSS → indexed XPath --- */
  private static final Pattern BY_CALL = Pattern.compile(
      "By\\.(cssSelector|xpath|className|tagName|linkText|partialLinkText)\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");
  private static final Pattern SIMPLE_ATTR_CSS = Pattern.compile(
      "^\\s*([a-zA-Z][a-zA-Z0-9]*)?\\[([a-zA-Z][a-zA-Z0-9_-]*)\\s*(?:\\^=|\\$=|\\*=|=)\\s*[\"']([^\"']+)[\"']\\]\\s*$");
  private static final Pattern SIMPLE_XPATH = Pattern.compile(
      "^\\s*\\(?//([a-zA-Z*][a-zA-Z0-9]*)\\[@([a-zA-Z][a-zA-Z0-9_-]*)\\s*=\\s*[\"']([^\"']+)[\"']\\]\\)?(?:\\[\\d+\\])?\\s*$");

  private static List<ValidationFinding> ruleComplexLocators(
      Path root, String cls, Path file, String body, List<String> userHooks) {
    List<ValidationFinding> out = new ArrayList<>();
    Matcher m = BY_CALL.matcher(body);
    while (m.find()) {
      String strategy = m.group(1);
      String raw = m.group(2);
      String decoded = raw.replace("\\\"", "\"");
      if (!isAllowedByShape(strategy, decoded, userHooks)) {
        out.add(new ValidationFinding(
            "FW_RULE_LOCATOR_LADDER",
            "Locator By." + strategy + "(\"" + truncate(decoded, 80) + "\") does not match the allowed ladder "
                + "(user hook → id → name → simple CSS → indexed XPath)",
            ValidationSeverity.RECOMMENDED,
            cls,
            relativize(root, file),
            lineOf(body, m.start()),
            "strategy=" + strategy,
            "Use a user-preferred hook attribute (" + String.join(", ", userHooks.isEmpty() ? List.of("data-axis-test-id") : userHooks)
                + "), id, name, or simple attribute CSS; fall back to indexed XPath only when a child index is required."));
      }
    }
    return out;
  }

  private static boolean isAllowedByShape(String strategy, String value, List<String> userHooks) {
    return switch (strategy) {
      case "cssSelector" -> {
        Matcher sm = SIMPLE_ATTR_CSS.matcher(value);
        if (!sm.matches()) yield false;
        String attr = sm.group(2);
        yield isHookOrPlainAttr(attr, userHooks);
      }
      case "xpath" -> {
        Matcher xm = SIMPLE_XPATH.matcher(value);
        if (!xm.matches()) yield false;
        String attr = xm.group(2);
        yield isHookOrPlainAttr(attr, userHooks);
      }
      case "className", "tagName", "linkText", "partialLinkText" -> false;
      default -> true;
    };
  }

  private static boolean isHookOrPlainAttr(String attr, List<String> userHooks) {
    if (attr == null) return false;
    String lower = attr.toLowerCase(Locale.ROOT);
    if ("id".equals(lower) || "name".equals(lower)) return true;
    for (String hook : userHooks) {
      if (hook != null && hook.equalsIgnoreCase(attr)) return true;
    }
    return lower.startsWith("data-") || lower.equals("aria-label") || lower.startsWith("data-test");
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  private static Integer lineOf(String body, int offset) {
    if (body == null || offset < 0 || offset > body.length()) return null;
    int line = 1;
    for (int i = 0; i < offset; i++) {
      if (body.charAt(i) == '\n') line++;
    }
    return line;
  }

  private static List<ValidationFinding> helperHeuristics(
      Path workspaceRoot, Path javaFile, FrameworkTemplateCatalog.Catalog catalog) {
    List<ValidationFinding> out = new ArrayList<>();
    String body = readQuiet(javaFile);
    String rel = relativize(workspaceRoot, javaFile);
    if (body.contains("Thread.sleep(")) {
      out.add(new ValidationFinding(
          "FW_HELPER_THREADSLEEP",
          "Avoid Thread.sleep in generated code — prefer SmartWait / Settle utilities",
          ValidationSeverity.RECOMMENDED,
          javaFile.getFileName().toString(),
          rel,
          null,
          "",
          "Use project.utils.locators.SmartWait or settle helpers from template."));
    }
    if (body.contains("driver.findElement(") && !body.contains("KeelLocate")
        && catalog.mentionsUtility("KeelLocate")) {
      out.add(new ValidationFinding(
          "FW_HELPER_RAW_FIND",
          "Prefer KeelLocate over raw driver.findElement",
          ValidationSeverity.RECOMMENDED,
          javaFile.getFileName().toString(),
          rel,
          null,
          "",
          "Template provides KeelLocate for ranked locator resolution."));
    }
    if (body.contains("Properties") && !body.contains("PropertyReader")
        && catalog.mentionsUtility("PropertyReader")) {
      out.add(new ValidationFinding(
          "FW_HELPER_DUPLICATE_CONFIG",
          "Use PropertyReader instead of ad-hoc Properties for config",
          ValidationSeverity.RECOMMENDED,
          javaFile.getFileName().toString(),
          rel,
          null,
          "",
          "Reuse project.utils.dataReader.PropertyReader."));
    }
    return out;
  }

  private static String readQuiet(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      return "";
    }
  }
}