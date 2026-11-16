# Codegen Naming UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emit customer ZIP tests as `TC_01` (not `TC_01Test`), title-based `@Test` methods, smarter page stems (`LoginPage`, `NewOperationUser`), locator-first text asserts, short TODO reasons, and optional Ollama polish for **test method names only**.

**Architecture:** Extend `CodegenNaming` + `PageClusterer` for deterministic names; add `TestMethodNaming` (+ optional `LocalLlmClient` call from `CodeWriter` when config flag set). Tighten `PageAccumulator.isBodyTextAssert` and branch in `PageActions.java.ftl`. Wire config through `DeliveryPortalProperties` → `ConversionJobRequest` → `CodeWriter`. Surefire includes `**/TC_*.java`.

**Tech Stack:** Java 21, Spring Boot `@ConfigurationProperties`, FreeMarker templates, TestNG, Ollama via existing `LocalLlmClient`, Maven Surefire 3.5.

## Global Constraints

- No Claude / Anthropic models.
- Test class = sanitized TC id only: passed `TC_01`, partial `TC_06Todo` (no `Test` / `TodoTest` suffix).
- Ollama naming: **methods only**; pages stay deterministic; default off (`delivery.codegen.ollama-naming=false`).
- Ollama failure/timeout must never fail emit — fall back to deterministic method name.
- Do not commit unless the user explicitly asks.

---

## File map

| File | Responsibility |
|------|----------------|
| `CodegenNaming.java` | `testClassName(tcId, passed)`, fix `pageStem` to keep `LoginPage`-style stems |
| `TestMethodNaming.java` | Title → ≤5-word method; optional Ollama polish |
| `CodegenTodoReason.java` | Shorten failure reason for TODO emit (≤120 chars) |
| `PageClusterer.java` | Multi-segment path stems; login path → `LoginPage` |
| `PageNameNormalizer.java` | Login aliases → `LoginPage` |
| `PageAccumulator.java` | Locator-first vs body-text assert gate |
| `CodeWriter.java` | Class/method/reason wiring; accept naming options |
| `ConversionJobRequest.java` | Add `codegenOllamaNaming` flag |
| `DeliveryPortalProperties.java` | `codegenOllamaNaming` property |
| `ConversionWorker.java` / `DeliveryCli.java` | Pass flag into job request |
| `DomainCatalogWriter.java` / `RevisePhase.java` | Use new class names |
| `GeneratedTest.java.ftl` / `TodoTest.java.ftl` | `${methodName}` instead of `runCase`/`pendingCase` |
| `PageActions.java.ftl` | `textContains(field, …)` when field present |
| `customer-framework-template/pom.xml` | Surefire `**/TC_*.java` include |
| `application.properties` | `delivery.codegen.ollama-naming=false` |
| Tests | `CodegenNamingTest`, `TestMethodNamingTest`, `PageClustererTest`, `CodeWriterTest`, `PageAccumulatorTest` |

---

### Task 1: Test class naming (`TC_01` / `TC_06Todo`)

**Files:**
- Modify: `src/main/java/delivery/codegen/CodegenNaming.java`
- Modify: `src/main/java/delivery/codegen/CodeWriter.java:89`
- Modify: `src/main/java/delivery/codegen/DomainCatalogWriter.java:69`
- Modify: `src/main/java/delivery/job/RevisePhase.java:147`
- Modify: `src/test/java/delivery/codegen/CodeWriterTest.java`
- Test: `src/test/java/delivery/codegen/CodegenNamingTest.java`

**Interfaces:**
- Produces: `CodegenNaming.testClassName(String tcId, boolean passed) → String`
  - passed `true` → `CodeWriter.toClassName(tcId)` (e.g. `TC_01`)
  - passed `false` → `CodeWriter.toClassName(tcId) + "Todo"` (e.g. `TC_06Todo`)

- [ ] **Step 1: Write failing test**

```java
@Test
public void testClassNameIdOnly() {
    Assert.assertEquals(CodegenNaming.testClassName("TC_01", true), "TC_01");
    Assert.assertEquals(CodegenNaming.testClassName("TC_06", false), "TC_06Todo");
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `mvn -q test -Dtest=CodegenNamingTest#testClassNameIdOnly`
Expected: FAIL — method not found

- [ ] **Step 3: Implement**

Add to `CodegenNaming.java`:

```java
public static String testClassName(String tcId, boolean passed) {
    String base = CodeWriter.toClassName(tcId);
    return passed ? base : base + "Todo";
}
```

Replace in `CodeWriter.java` line 89:

```java
String className = CodegenNaming.testClassName(outcome.tcId(), passed);
```

Same pattern in `DomainCatalogWriter.java` and `RevisePhase.java`.

Update `CodeWriterTest` paths/assertions: `TC_001Test.java` → `TC_001.java`, `TC_002TodoTest.java` → `TC_002Todo.java`, etc.

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=CodegenNamingTest,CodeWriterTest`
Expected: PASS

- [ ] **Step 5: Commit** (only if user asked)

---

### Task 2: Title-based test method names (deterministic)

**Files:**
- Create: `src/main/java/delivery/codegen/TestMethodNaming.java`
- Create: `src/test/java/delivery/codegen/TestMethodNamingTest.java`
- Modify: `customer-framework-template/templates/GeneratedTest.java.ftl:49`
- Modify: `customer-framework-template/templates/TodoTest.java.ftl:52`
- Modify: `src/main/java/delivery/codegen/CodeWriter.java` (compute `methodName`, put in model)

**Interfaces:**
- Produces: `TestMethodNaming.deterministic(String title, String tcId) → String`
  - Tokenize title on non-alphanumeric; drop empty tokens.
  - Take first **5** tokens max.
  - Format: first word capitalized, rest lower, joined with `_` (e.g. `Successful_login_test`).
  - Fallback if empty/invalid: `case_<sanitizedTcId>` via `CodegenNaming.sanitizeJavaIdentifier`.

- [ ] **Step 1: Write failing tests**

```java
@Test
public void trimsToFiveWords() {
    Assert.assertEquals(
        TestMethodNaming.deterministic("Confirm the operation user is created successfully", "TC_06"),
        "Confirm_the_operation_user_is");
}

@Test
public void fallsBackWhenTitleBlank() {
    Assert.assertEquals(
        TestMethodNaming.deterministic("", "TC_01"),
        "case_TC_01");
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `mvn -q test -Dtest=TestMethodNamingTest`
Expected: FAIL

- [ ] **Step 3: Implement `TestMethodNaming.java`**

```java
public final class TestMethodNaming {
    private TestMethodNaming() {}

    public static String deterministic(String title, String tcId) {
        String[] words = tokenize(title);
        if (words.length == 0) {
            return fallback(tcId);
        }
        int n = Math.min(5, words.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('_');
            String w = words[i].toLowerCase(Locale.ROOT);
            if (i == 0 && !w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
            } else {
                sb.append(w);
            }
        }
        return CodegenNaming.sanitizeJavaIdentifier(sb.toString(), fallback(tcId));
    }

    private static String[] tokenize(String title) {
        if (title == null || title.isBlank()) return new String[0];
        return Arrays.stream(title.trim().split("[^A-Za-z0-9]+"))
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    private static String fallback(String tcId) {
        String id = tcId == null ? "TC" : tcId.replaceAll("[^A-Za-z0-9_]", "_");
        return CodegenNaming.sanitizeJavaIdentifier("case_" + id, "case_Tc");
    }
}
```

In `CodeWriter.writeVerified`, before template process:

```java
String methodName = TestMethodNaming.deterministic(outcome.title(), outcome.tcId());
model.put("methodName", methodName);
```

In both FTL templates replace `runCase` / `pendingCase` with `${methodName}`.

- [ ] **Step 4: Extend `CodeWriterTest`**

```java
Assert.assertTrue(java.contains("public void Valid_login"), java); // title "Valid login"
```

Adjust to match deterministic output for fixture titles.

- [ ] **Step 5: Run tests**

Run: `mvn -q test -Dtest=TestMethodNamingTest,CodeWriterTest`
Expected: PASS

---

### Task 3: Optional Ollama method naming (config)

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/delivery/portal/DeliveryPortalProperties.java`
- Modify: `src/main/java/delivery/job/ConversionJobRequest.java`
- Modify: `src/main/java/delivery/portal/worker/ConversionWorker.java`
- Modify: `src/main/java/delivery/cli/DeliveryCli.java` (default false)
- Modify: `src/main/java/delivery/codegen/TestMethodNaming.java`
- Modify: `src/main/java/delivery/codegen/CodeWriter.java`
- Modify: `src/main/java/delivery/job/EmitPhase.java:105`
- Modify: `src/main/java/delivery/job/DryRunConversionService.java:79`
- Create: `src/test/java/delivery/codegen/TestMethodNamingOllamaTest.java`

**Interfaces:**
- Produces: `TestMethodNaming.resolve(String title, String tcId, LocalLlmClient clientOrNull, boolean ollamaEnabled) → String`
- Consumes: `LocalLlmClient.completeChat(system, user, false)` (plain text, not JSON)
- Prompt: `"Return ONE Java test method identifier only, max 5 English words, underscores between words, no explanation."`

Validation after Ollama response:
- Strip quotes/whitespace; split on non-alphanumeric → ≤5 tokens.
- Must pass `CodegenNaming.isValidJavaIdentifier`.
- Else return deterministic.

- [ ] **Step 1: Add property**

`application.properties`:

```properties
delivery.codegen.ollama-naming=false
```

`DeliveryPortalProperties`: field + getter/setter `codegenOllamaNaming`.

- [ ] **Step 2: Extend `ConversionJobRequest`**

Add `boolean codegenOllamaNaming` as last record component; back-compat constructors default `false`.

- [ ] **Step 3: Write test with fake client stub**

Use a package-visible or test-only stub implementing a narrow interface, OR test `TestMethodNaming.parseOllamaResponse(String)` separately:

```java
@Test
public void acceptsValidOllamaIdentifier() {
    Assert.assertEquals(
        TestMethodNaming.parseOllamaResponse("successful_login_test"),
        "Successful_login_test");
}

@Test
public void rejectsTooManyWords() {
    Assert.assertNull(TestMethodNaming.parseOllamaResponse("one_two_three_four_five_six"));
}
```

- [ ] **Step 4: Implement resolve + wire**

`CodeWriter` constructor overload:

```java
public CodeWriter(Path templateDir, CodegenOptions options)
```

`CodegenOptions` record: `(boolean ollamaNaming, String llmBaseUrl, String llmModel)`.

In `writeVerified`:

```java
LocalLlmClient client = options.ollamaNaming()
    ? new LocalLlmClient(options.llmBaseUrl(), options.llmModel(), Duration.ofSeconds(15), 64)
    : null;
String methodName = TestMethodNaming.resolve(outcome.title(), outcome.tcId(), client, options.ollamaNaming());
```

Wrap Ollama call in try/catch — any exception → deterministic.

`EmitPhase`:

```java
CodegenOptions opts = new CodegenOptions(
    request.codegenOllamaNaming(), request.localLlmBaseUrl(), request.localLlmModel());
new CodeWriter(request.templateRoot().resolve("templates"), opts).write(projectDir, toCodegen);
```

- [ ] **Step 5: Run tests**

Run: `mvn -q test -Dtest=TestMethodNamingTest,TestMethodNamingOllamaTest,CodeWriterTest`
Expected: PASS; no network when flag false

---

### Task 4: Smarter page stems

**Files:**
- Modify: `src/main/java/delivery/codegen/PageClusterer.java`
- Modify: `src/main/java/delivery/codegen/PageNameNormalizer.java`
- Modify: `src/main/java/delivery/codegen/CodegenNaming.java` (remove blanket `Page` suffix strip)
- Modify: `src/test/java/delivery/codegen/PageClustererTest.java`
- Modify: `src/test/java/delivery/codegen/CodegenNamingTest.java`

**Interfaces:**
- Produces: updated `PageClusterer.pageNameFromUrl(String url) → String`
  - `/operations-users/new` → `NewOperationUser` (verb `new` prefix + singularized entity from prior segment)
  - paths containing `/login` (case-insensitive) → `LoginPage`
  - host-only root unchanged for non-login (existing brand logic)
- Produces: `PageNameNormalizer.canonical` maps login aliases → `LoginPage` (not bare `Login`)

- [ ] **Step 1: Write failing tests**

```java
@Test
public void newEntityPathUsesVerbPrefix() {
    Assert.assertEquals(
        PageClusterer.pageNameFromUrl("https://opssit.axispay.app/operations-users/new"),
        "NewOperationUser");
}

@Test
public void loginPathUsesLoginPage() {
    Assert.assertEquals(
        PageClusterer.pageNameFromUrl("https://example.com/practice-test-login/"),
        "LoginPage");
}

@Test
public void pageStemKeepsLoginPage() {
    Assert.assertEquals(CodegenNaming.pageStem("LoginPage"), "LoginPage");
    Assert.assertEquals(CodegenNaming.actionsClassName("LoginPage"), "LoginPage_Actions");
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `mvn -q test -Dtest=PageClustererTest,CodegenNamingTest`
Expected: FAIL on new assertions

- [ ] **Step 3: Implement path logic**

In `pageNameFromUrl`, after parsing path segments (non-blank):

```java
if (path.toLowerCase(Locale.ROOT).contains("/login")) {
    return "LoginPage";
}
List<String> segments = meaningfulPathSegments(path); // drop empty, "api", version nums
if (segments.size() >= 2) {
    String leaf = segments.get(segments.size() - 1).toLowerCase(Locale.ROOT);
    String prev = segments.get(segments.size() - 2);
    if (Set.of("new", "create", "edit").contains(leaf)) {
        String entity = singularizeEntity(prev); // operations-users → OperationUser
        return toPageClassStem(capitalize(leaf) + entity);
    }
}
// existing leaf / host fallback
```

`singularizeEntity`: split on `-`/`_`, capitalize each part, drop trailing `s` on last part if plural (`users` → `User`).

Remove from `pageStem`:

```java
if (stem.endsWith("Page") && stem.length() > 4) {
    stem = stem.substring(0, stem.length() - 4);
}
```

Update `PageNameNormalizer.canonical`: when alias and stem blank/Home/Page → `LoginPage`; when alias and stem is login path stem → `LoginPage`.

Update existing `PageClustererTest.reclusterMapsFrozenLoginToUrlStem` expectation: `PracticeTestLogin` may become `LoginPage` if URL contains login — **update test** to match new rule.

- [ ] **Step 4: Run tests + fix regressions**

Run: `mvn -q test -Dtest=PageClustererTest,CodegenNamingTest,CodeWriterTest`
Expected: PASS (update fixtures: `Login_Actions` → `LoginPage_Actions` where URL is login)

---

### Task 5: Locator-first text asserts

**Files:**
- Modify: `src/main/java/delivery/codegen/PageAccumulator.java`
- Modify: `customer-framework-template/templates/PageActions.java.ftl:39-40`
- Create: `src/test/java/delivery/codegen/PageAccumulatorAssertTest.java`
- Modify: `src/test/java/delivery/codegen/CodeWriterTest.java` (`textContainsUsesBodyGetTextContains`)

**Interfaces:**
- Produces: `PageAccumulator.isBodyTextAssert(ProvenStep)` — true only when:
  - `textContains` assert AND
  - (no locator OR locator is body-wide / brittle xpath: `//body`, `normalize-space(.)`, or xpath longer than threshold with no stable id/test-id)

- [ ] **Step 1: Write failing tests**

```java
@Test
public void solidIdUsesLocatorPath() {
    ProvenStep step = new ProvenStep("TC", "Home", "assert", "assert",
        "id", "toast-msg", "", "textContains", "Created", true, "ok");
    Assert.assertFalse(PageAccumulator.isBodyTextAssertForTest(step));
}

@Test
public void bodyXpathUsesBodyText() {
    ProvenStep step = new ProvenStep("TC", "Home", "assert", "assert",
        "xpath", "//body", "", "textContains", "Welcome", true, "ok");
    Assert.assertTrue(PageAccumulator.isBodyTextAssertForTest(step));
}
```

Expose package-private test hook or test via emitted actions file.

- [ ] **Step 2: Implement `isSolidTextLocator` helper**

```java
private static boolean isBodyTextAssert(ProvenStep step) {
    if (!"textContains".equalsIgnoreCase(nullToEmpty(step.assertionType()))) return false;
    String loc = nullToEmpty(step.locatorValue());
    if (loc.isBlank()) return true;
    String strat = nullToEmpty(step.locatorStrategy()).toLowerCase(Locale.ROOT);
    if ("id".equals(strat) || loc.contains("data-axis-test-id") || loc.contains("data-test")) return false;
    String lower = loc.toLowerCase(Locale.ROOT);
    if (lower.contains("//body") || lower.contains("normalize-space(.)")) return true;
    if ("xpath".equals(strat) && loc.length() > 120) return true;
    return false;
}
```

When `isBodyTextAssert` is false but assertion is textContains, fall through normal field path (existing lines 47–80).

Update `PageActions.java.ftl`:

```ftl
<#elseif assertion.assertionType == "textContains">
<#if assertion.fieldName?has_content>
        driver.validation().textContains(${assertion.fieldName}, "${assertion.expected?j_string}");
<#else>
        driver.validation().bodyTextContains("${assertion.expected?j_string}");
</#if>
```

- [ ] **Step 3: Update `CodeWriterTest.textContainsUsesBodyGetTextContains`**

Add second step with `id` locator expecting `textContains(` in actions; keep body xpath expecting `bodyTextContains(`.

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=PageAccumulatorAssertTest,CodeWriterTest#textContainsUsesBodyGetTextContains`
Expected: PASS

---

### Task 6: Short TODO reasons

**Files:**
- Create: `src/main/java/delivery/codegen/CodegenTodoReason.java`
- Modify: `src/main/java/delivery/codegen/CodeWriter.java:97,105-106`
- Modify: `customer-framework-template/templates/TodoTest.java.ftl:65-71`
- Create: `src/test/java/delivery/codegen/CodegenTodoReasonTest.java`

**Interfaces:**
- Produces: `CodegenTodoReason.summarize(String raw, int maxLen) → String`
  - Strip `HEAL_EXHAUSTED:` prefix
  - Take first line only
  - Collapse whitespace
  - Truncate to 120 chars with `…`
  - Empty → `"conversion incomplete"`

- [ ] **Step 1: Write failing tests**

```java
@Test
public void stripsHealExhaustedPrefix() {
    String in = "HEAL_EXHAUSTED: No DOM candidate for intent CLICK: Click Finish\nmore junk";
    Assert.assertEquals(
        CodegenTodoReason.summarize(in, 120),
        "No DOM candidate for intent CLICK: Click Finish");
}
```

- [ ] **Step 2: Implement and wire**

In `CodeWriter`:

```java
String shortReason = CodegenTodoReason.summarize(outcome.failureReason(), 120);
model.put("reason", shortReason);
model.put("reviewComments", List.of()); // drop REVIEW wall for todo emit
```

In `TodoTest.java.ftl`, remove `reviewComments` block (or keep guarded empty). Keep single STOPPED HERE + Assert.fail.

- [ ] **Step 3: Update `CodeWriterTest.writesPartialTodoWithProvenStepsAndStopComment`**

Assert reason does not contain `HEAL_EXHAUSTED`; still contains `Click Finish`.

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=CodegenTodoReasonTest,CodeWriterTest#writesPartialTodoWithProvenStepsAndStopComment`
Expected: PASS

---

### Task 7: Surefire discovers `TC_*.java`

**Files:**
- Modify: `customer-framework-template/pom.xml`

- [ ] **Step 1: Add surefire includes**

Inside `maven-surefire-plugin` `<configuration>`:

```xml
<includes>
  <include>**/TC_*.java</include>
  <include>**/*Test.java</include>
  <include>**/*Tests.java</include>
  <include>**/Test*.java</include>
</includes>
```

- [ ] **Step 2: Smoke verify**

After a local emit into a temp project dir (or unit test copies template pom), run:

`mvn -q -f customer-framework-template/pom.xml test -Dtest=TC_*`

Expected: Surefire recognizes `TC_*.java` pattern (may find zero tests in bare template — that's OK; pattern must not error).

---

### Task 8: Full regression

- [ ] **Step 1: Run codegen test suite**

Run: `mvn -q test -Dtest=CodegenNamingTest,TestMethodNamingTest,PageClustererTest,CodeWriterTest,PageAccumulatorAssertTest,CodegenTodoReasonTest`
Expected: all PASS

- [ ] **Step 2: Run broader delivery tests**

Run: `mvn -q test -Dtest=delivery.codegen.*,delivery.job.EmitPhaseMappingTest,TwoPhaseConversionGateTest`
Expected: PASS; fix any class-name path references

- [ ] **Step 3: Manual Automate smoke (operator)**

Restart portal; run Automate on opssit suite; confirm ZIP contains:
- `TC_01.java` with title-based method
- `LoginPage_Actions` / `NewOperationUser_Actions` (or equivalent)
- Todo file uses short STOPPED HERE line

---

## Spec coverage checklist

| Requirement | Task |
|-------------|------|
| Class `TC_01` / `TC_06Todo` | Task 1 |
| Title method ≤5 words | Task 2 |
| Optional Ollama methods only | Task 3 |
| Smarter page stems | Task 4 |
| Locator-first asserts | Task 5 |
| Short TODO reason | Task 6 |
| Surefire `TC_*.java` | Task 7 |
| Ollama off by default | Task 3 |

## Self-review

- No TBD placeholders.
- `pageStem` Page-stripping called out explicitly (required for `LoginPage_Actions`).
- Ollama scoped to methods; 15s timeout; emit never fails on LLM error.
- All type names consistent: `CodegenOptions`, `TestMethodNaming.resolve`, `CodegenNaming.testClassName`.
