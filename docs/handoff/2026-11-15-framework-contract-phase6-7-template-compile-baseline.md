# Phase 6.7 — Customer framework template compile baseline

**Date:** 2026-11-15

## 1. Root cause

| Issue | Location | Introduced by |
|-------|----------|----------------|
| Duplicate `com.github.automatedowl:allure-environment-writer:1.0.0` | `customer-framework-template/pom.xml` lines ~97 and ~159 | **Template itself** — identical blocks; `FrameworkPackager.copyTemplate` copies `pom.xml` verbatim (no POM mutation) |
| Invalid TestNG scope `java` | same POM | Typo; main sources import TestNG → requires `compile` scope |
| Illegal multi-catch `WebDriverException \| RuntimeException` | `Assertion.java` | JDK rejects subclass + superclass in one multi-catch (surfaced once POM was valid) |

## 2. Files changed

- `customer-framework-template/pom.xml` — remove duplicate Allure env writer; `testng` scope `compile`
- `customer-framework-template/src/main/java/project/validations/Assertion.java` — catch `RuntimeException` only (2 sites)
- `src/test/java/delivery/packager/CustomerFrameworkTemplatePomTest.java` — POM regression
- `src/test/java/delivery/agent/validation/automate/FrameworkRepairCompileBaselineTest.java` — strict pass (packaged template + codegen)

## 3. Why safe

- Single `allure-environment-writer` retains Allure environment reporting; `allure-testng` + `aspectjweaver` unchanged.
- TestNG `compile` scope matches existing `main/java` listeners/retry utilities (required for `mvn compile`).
- Assertion catch narrowing preserves behavior (`WebDriverException` is a `RuntimeException`).
- No repair/reconcile/contract/flag changes.

## 4. Regression tests

```text
mvn -pl keel-portal "-Dtest=CustomerFrameworkTemplatePomTest,FrameworkRepairCompileBaselineTest" test
```

**Result:** 4/4 pass; `EMIT_COMPILE_CHECK ok (test-compile)` on packager-seeded + codegen project.

## 5. Clean compile (actual template)

```text
cd customer-framework-template
mvn -q "-Dmaven.compiler.release=21" test-compile
```

Record exit code in verification run (template tree with sample tests only).

Packaged path (qualification):

```text
FrameworkPackager.copyTemplate(customer-framework-template, dest) → CodeWriter.write → EmitCompileCheck.runIfEnabled
```

## 6. Compile-on live matrix

**Unblocked for re-run** (POM + baseline compile green). Does not imply production readiness — re-run `FrameworkAdaptationCursorCompileOnLiveIT` with `-Dkeel.agent.cursor.compile.on=true`.