# Generate Authoring Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-repair known generate authoring mistakes (especially empty-email TestData) so Gemma jobs save instead of `QUALITY_GATE`, and make the optional second pass fix mistakes plus coverage.

**Architecture:** New pure `GenerateAuthoringRepair` rewrites `ManualTestCase` records using `GenerateAuthoringRules` detectors. `TcGenerateService.generateStory` runs repair after the quality gate (and again after the existing quality-retry LLM call). The optional review prompt asks the model to fix mistakes and add gaps. No new API.

**Tech Stack:** Java 21, TestNG, existing `ManualTestCase` record, Thymeleaf `generate.html`.

**Spec:** `docs/superpowers/specs/2026-08-31-generate-authoring-repair-design.md`

## Global Constraints

- Auto-repair is **always on** (paste, bulk, All in one); not behind the Second pass checkbox
- Second pass checkbox stays **optional**; extra Ollama call must **fix mistakes and add missing coverage**
- Never invent expected UI error text (vague asserts still fail)
- Repair returns **new** `ManualTestCase` records; do not mutate input lists
- If repair does not shrink the gate error list, keep the pre-repair cases and continue (quality-retry or throw)
- Log info when repair helped: `Repaired N generate authoring issue(s) for {storyLabel}`
- New API / Job status “repaired” badge: **out**
- Do not change `delivery.generate-timeout-seconds`
- Do not commit unless the user asks

### File map

| File | Responsibility |
|------|----------------|
| Create `src/main/java/delivery/excel/GenerateAuthoringRepair.java` | `repair(List<ManualTestCase>)` catalog from spec §5 |
| Modify `src/main/java/delivery/excel/GenerateAuthoringRules.java` | Package-visible detectors Repair reuses (no behavior change to `validate`) |
| Modify `src/main/java/delivery/portal/service/TcGenerateService.java` | Flow: gate → repair → retry → repair; review prompt text |
| Modify `src/main/resources/templates/generate.html` | Checkbox + hint copy |
| Create `src/test/java/delivery/excel/GenerateAuthoringRepairTest.java` | Repair catalog tests |
| Modify `src/test/java/delivery/portal/service/TcGenerateServiceQualityRetryTest.java` | Review prompt + stub generateStory repair (no extra LLM) |
| Modify `src/test/java/delivery/portal/service/FacebookLoginNegativeGenerateTest.java` | Phone-field JSON is now repairable — expect success on first call |

---

### Task 1: Repair blanks empty-field TestData (keep later lines)

**Files:**
- Create: `src/test/java/delivery/excel/GenerateAuthoringRepairTest.java`
- Create: `src/main/java/delivery/excel/GenerateAuthoringRepair.java`
- Modify: `src/main/java/delivery/excel/GenerateAuthoringRules.java` (only if Repair needs a package-visible helper already used by validate)

**Interfaces:**
- Consumes: `ManualTestCase` record; `GenerateAuthoringRules.mentionsEmpty`, `isEnterStepForField`, `isPlaceholder`, `splitNumberedSteps`, `splitTestDataLines`; `GenerateQualityGate.validate`
- Produces: `public static List<ManualTestCase> GenerateAuthoringRepair.repair(List<ManualTestCase> cases)` — null → empty list; empty → empty list; otherwise new list, same size, same order

- [ ] **Step 1: Write the failing test**

```java
package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class GenerateAuthoringRepairTest {

    private static final String BASE = "https://www.facebook.com/";

    static ManualTestCase emptyEmailTypedData() {
        return new ManualTestCase(
                "TC_02",
                "Login with empty email and valid password",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Leave the Email or phone field empty
                3. Enter in the Password field
                4. Click the Log in button
                5. Confirm the message 'Please enter your email or phone number' is visible""",
                """
                1. Login page is shown
                2. Email or phone is empty
                3. Password is accepted
                4. Submit is clicked
                5. Error message 'Please enter your email or phone number' is shown""",
                "P1",
                "negative,missing-credentials",
                "",
                """

                user@example.com
                ValidPass123!

                """,
                "EXECUTE");
    }

    @Test
    public void repair_blanksEmptyEmailTestData_keepsPasswordLine() {
        ManualTestCase raw = emptyEmailTypedData();
        Assert.assertFalse(GenerateQualityGate.validate(List.of(raw), BASE).isEmpty());

        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(raw));
        Assert.assertEquals(out.size(), 1);
        List<String> errors = GenerateQualityGate.validate(out, BASE);
        Assert.assertTrue(errors.isEmpty(), errors.toString());

        List<String> data = GenerateAuthoringRules.splitTestDataLines(out.get(0).testData(), 5);
        Assert.assertEquals(data.get(1).trim(), "", "empty-email step TestData must be blank");
        Assert.assertEquals(data.get(2).trim(), "ValidPass123!");
    }

    @Test
    public void repair_idempotentOnAlreadyBlankEmptyEmail() {
        ManualTestCase good = emptyEmailTypedData();
        List<String> lines = GenerateAuthoringRules.splitTestDataLines(good.testData(), 5);
        lines.set(1, "");
        ManualTestCase already = new ManualTestCase(
                good.tcId(), good.title(), good.preconditions(), good.steps(),
                good.expectedResult(), good.priority(), good.tags(), good.visualAssertion(),
                String.join("\n", lines), good.keelPath());
        Assert.assertTrue(GenerateQualityGate.validate(List.of(already), BASE).isEmpty());
        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(already));
        Assert.assertEquals(out.get(0).testData(), already.testData());
        Assert.assertEquals(out.get(0).steps(), already.steps());
    }

    @Test
    public void repair_nullOrEmpty_doesNotThrow() {
        Assert.assertTrue(GenerateAuthoringRepair.repair(null).isEmpty());
        Assert.assertTrue(GenerateAuthoringRepair.repair(List.of()).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=GenerateAuthoringRepairTest" test`

Expected: COMPILE FAIL — `GenerateAuthoringRepair` cannot be found.

- [ ] **Step 3: Write minimal implementation**

Create `GenerateAuthoringRepair` with `repair` that copies cases, and for each case whose title+expected `mentionsEmpty` for email/phone/password, walks `splitNumberedSteps` + `splitTestDataLines`, blanks non-placeholder data lines on matching `isEnterStepForField` steps, rebuilds `testData` with `String.join("\n", dataLines)`, returns a new `ManualTestCase`.

Order for this task: only step (3) of the spec catalog (blank TestData). Other rewrites can no-op until later tasks.

Null/empty: return `List.of()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=GenerateAuthoringRepairTest" test`

Expected: PASS.

- [ ] **Step 5: Do not commit** (global constraint)

---

### Task 2: Repair combined-login labels and Leave-empty wording

**Files:**
- Modify: `src/test/java/delivery/excel/GenerateAuthoringRepairTest.java`
- Modify: `src/main/java/delivery/excel/GenerateAuthoringRepair.java`
- Modify: `src/main/java/delivery/excel/GenerateAuthoringRules.java` — add package-visible:

```java
static boolean isStandalonePhoneFieldStep(String stepText) {
    return stepText != null && STANDALONE_PHONE_FIELD.matcher(stepText).find();
}
static boolean isStandaloneEmailFieldStep(String stepText) {
    return stepText != null && STANDALONE_EMAIL_FIELD.matcher(stepText).find();
}
static boolean alreadyLeaveEmpty(String stepText) {
    if (stepText == null) return false;
    String step = stepText.toLowerCase(java.util.Locale.ROOT);
    return LEAVE_EMPTY.matcher(stepText).find() || step.contains("empty value") || step.contains("leave");
}
```

**Interfaces:**
- Consumes: `mentionsCombinedLoginIdentifier(title + " " + expected, steps)`, helpers above, `isEnterStepForField`, `mentionsEmpty`
- Produces: same `repair(...)`; per case apply order: (1) combined label rewrite (2) Leave-empty rewrite (3) blank TestData (already in Task 1)

- [ ] **Step 1: Write the failing tests** (append to `GenerateAuthoringRepairTest`)

```java
    @Test
    public void repair_rewritesStandalonePhoneFieldOnCombinedLogin() {
        ManualTestCase raw = new ManualTestCase(
                "TC_03",
                "Invalid password login with valid-looking phone number",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Enter in the Phone field
                3. Enter in the Password field
                4. Click the Log in button
                5. Confirm the message 'Incorrect email or phone number' is visible""",
                """
                1. Login page is shown
                2. Phone number is accepted
                3. Password is accepted
                4. Submit is clicked
                5. Error message 'Incorrect email or phone number' is shown""",
                "P1",
                "negative",
                "",
                """
                5551234567
                WrongPass123!

                """,
                "EXECUTE");
        Assert.assertFalse(GenerateQualityGate.validate(List.of(raw), BASE).isEmpty());
        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(raw));
        List<String> errors = GenerateQualityGate.validate(out, BASE);
        Assert.assertTrue(errors.isEmpty(), errors.toString());
        Assert.assertTrue(out.get(0).steps().toLowerCase().contains("email or phone field"));
        Assert.assertFalse(out.get(0).steps().matches("(?is).*enter in the phone field.*"));
    }

    @Test
    public void repair_rewritesEnterToLeaveEmptyOnEmptyEmailCase() {
        ManualTestCase raw = new ManualTestCase(
                "TC_02",
                "Login with empty email and valid password",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Enter in the Email or phone field
                3. Enter in the Password field
                4. Click the Log in button
                5. Confirm the message 'Please enter your email or phone number' is visible""",
                """
                1. Login page is shown
                2. Email or phone is empty
                3. Password is accepted
                4. Submit is clicked
                5. Error message 'Please enter your email or phone number' is shown""",
                "P1",
                "negative",
                "",
                """

                ValidPass123!

                """,
                "EXECUTE");
        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(raw));
        Assert.assertTrue(GenerateQualityGate.validate(out, BASE).isEmpty(),
                GenerateQualityGate.validate(out, BASE).toString());
        String step2 = GenerateAuthoringRules.splitNumberedSteps(out.get(0).steps()).get(1);
        Assert.assertTrue(step2.toLowerCase().contains("leave the"));
        Assert.assertTrue(step2.toLowerCase().contains("email or phone"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=GenerateAuthoringRepairTest" test`

Expected: FAIL — phone-field / enter-step cases still have gate errors.

- [ ] **Step 3: Write minimal implementation**

On each numbered step, in order:

1. If `mentionsCombinedLoginIdentifier` and standalone phone/email field (helpers) and step does not already contain `email or phone`, replace `Phone field` / `Email field` with `Email or phone field` (case-insensitive replace of those two-word phrases only).
2. If `mentionsEmpty(title+expected, kind)` and `isEnterStepForField` and not `alreadyLeaveEmpty`, set that numbered step text to `Leave the {label} field empty` where `{label}` is `Email or phone` if the (post-step-1) step contains `email or phone`, else `Email` / `Phone` / `Password` from `kind`.
3. Existing TestData blanking.

Rebuild numbered steps as `(i+1) + ". " + text` joined by `\n`. Preserve `title`, `expectedResult`, `keelPath`, etc.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=GenerateAuthoringRepairTest,GenerateAuthoringRulesTest" test`

Expected: PASS (gate tests still reject unrepaired rows).

- [ ] **Step 5: Do not commit** (global constraint)

---

### Task 3: Repair literal backslash-n in steps

**Files:**
- Modify: `src/test/java/delivery/excel/GenerateAuthoringRepairTest.java`
- Modify: `src/main/java/delivery/excel/GenerateAuthoringRepair.java`

**Interfaces:**
- Consumes: `GenerateQualityGate.hasLiteralBackslashNWithoutRealNewlines(String)`
- Produces: `repair` applies catalog step (4) **first** on `steps` (and `expectedResult` if it has the same defect) so later numbered-step rewrites see real lines

- [ ] **Step 1: Write the failing test**

```java
    @Test
    public void repair_turnsLiteralBackslashNIntoNewlines() {
        String jammed = "1. Open login\\n2. Leave the Email or phone field empty\\n"
                + "3. Leave the Password field empty\\n4. Click Log in\\n"
                + "5. Confirm the message 'Please enter your email or phone number' is visible";
        ManualTestCase raw = new ManualTestCase(
                "TC_01",
                "Login with empty email and empty password",
                "",
                jammed,
                "1. Login page is shown\\n2. Email or phone is empty\\n3. Password is empty\\n"
                        + "4. Submit is clicked\\n5. Error message 'Please enter your email or phone number' is shown",
                "P1",
                "negative",
                "",
                "\n\n\n",
                "EXECUTE");
        Assert.assertTrue(GenerateQualityGate.hasLiteralBackslashNWithoutRealNewlines(raw.steps()));
        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(raw));
        Assert.assertFalse(GenerateQualityGate.hasLiteralBackslashNWithoutRealNewlines(out.get(0).steps()));
        Assert.assertTrue(out.get(0).steps().contains("\n"));
        Assert.assertTrue(GenerateQualityGate.validate(out, BASE).isEmpty(),
                GenerateQualityGate.validate(out, BASE).toString());
    }
```

In Java source the fixture string must contain the two characters `\` and `n` (use `"\\n"` in ordinary Java strings, not a text block that turns them into real newlines).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=GenerateAuthoringRepairTest#repair_turnsLiteralBackslashNIntoNewlines" test`

Expected: FAIL — steps still jammed or gate still errors.

- [ ] **Step 3: Write minimal implementation**

If `hasLiteralBackslashNWithoutRealNewlines(steps)`, `steps = steps.replace("\\n", "\n")`. Same for `expectedResult`. Do this **before** numbered-step rewrites.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=GenerateAuthoringRepairTest" test`

Expected: PASS.

- [ ] **Step 5: Do not commit** (global constraint)

---

### Task 4: Wire repair into generateStory (no extra LLM when repairable)

**Files:**
- Modify: `src/main/java/delivery/portal/service/TcGenerateService.java` (`generateStory` after parse)
- Modify: `src/test/java/delivery/portal/service/TcGenerateServiceQualityRetryTest.java`
- Modify: `src/test/java/delivery/portal/service/FacebookLoginNegativeGenerateTest.java`

**Interfaces:**
- Consumes: `GenerateAuthoringRepair.repair`, `GenerateQualityGate.validate(cases, project.getBaseUrl())`
- Produces: private `applyAuthoringRepair(List<ManualTestCase> cases, String baseUrl, String storyLabel)` returning cases whose gate error count is **strictly smaller**, else the original list; info log `Repaired N generate authoring issue(s) for {storyLabel}` (`{storyLabel}` use `storyLabel` or `"story"` if null/blank)

`generateStory` flow after first parse + `GeneratedTcScopeFilter.apply`:

```
List<String> gateErrors = GenerateQualityGate.validate(cases, project.getBaseUrl());
if (!gateErrors.isEmpty()) {
    cases = applyAuthoringRepair(cases, project.getBaseUrl(), storyLabel);
    gateErrors = GenerateQualityGate.validate(cases, project.getBaseUrl());
}
if (!gateErrors.isEmpty()) {
    // existing quality-retry Ollama call, parse, scope filter
    cases = applyAuthoringRepair(cases, project.getBaseUrl(), storyLabel);
    gateErrors = GenerateQualityGate.validate(cases, project.getBaseUrl());
    if (!gateErrors.isEmpty()) {
        throw GenerateQualityGate.failureException(gateErrors);
    }
}
```

Add `private static final Logger log = LogManager.getLogger(TcGenerateService.class);` if missing.

- [ ] **Step 1: Write the failing tests**

In `TcGenerateServiceQualityRetryTest`, add JSON that is **only** the TestData defect (well-formed leave-empty steps, quoted error, `user@example.com` on TestData line 2). Assert `llmCallCount == 1` and result cases non-empty.

Keep `generateStory_retriesOnceOnGateFailThenThrows` (blank steps) — still `llmCallCount == 2` and `QUALITY_GATE`.

Replace `FacebookLoginNegativeGenerateTest.generateStory_rejectsBadPhoneFieldOutput` with:

```java
    @Test
    public void generateStory_repairsBadPhoneFieldWithoutRetry() throws Exception {
        StubTcGenerateService service = new StubTcGenerateService();
        service.llmResponses.add(badPhoneFieldJson());
        ProjectRecord project = facebookProject();
        TcGenerateService.StoryGenerateResult result = service.generateStory(
                project, readFixtureUs(), false, "US-1", null);
        Assert.assertFalse(result.cases().isEmpty());
        Assert.assertEquals(service.llmCallCount, 1);
        Assert.assertTrue(result.cases().get(0).steps().toLowerCase().contains("email or phone"));
    }
```

Stub `callOllama(String, String, String)` remains the override (3-arg); `invokeOllama` with null cancel still hits it.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q "-Dtest=TcGenerateServiceQualityRetryTest,FacebookLoginNegativeGenerateTest" test`

Expected: new stub test FAIL (quality-retry still called or QUALITY_GATE thrown); Facebook test FAIL until renamed/rewired (old method still expects throw).

- [ ] **Step 3: Write minimal implementation**

Implement `applyAuthoringRepair` and the flow above. Do not change quality-retry prompt.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q "-Dtest=TcGenerateServiceQualityRetryTest,FacebookLoginNegativeGenerateTest,GenerateAuthoringRepairTest,GenerateAuthoringRulesTest" test`

Expected: PASS. Blank-steps case still two LLM calls. Repairable cases one LLM call.

- [ ] **Step 5: Do not commit** (global constraint)

---

### Task 5: Second-pass prompt + Generate checkbox copy

**Files:**
- Modify: `src/main/java/delivery/portal/service/TcGenerateService.java` — `buildReviewUserMessage` (make `static` package-visible like `buildQualityRetryUserMessage`)
- Modify: `src/test/java/delivery/portal/service/TcGenerateServiceQualityRetryTest.java`
- Modify: `src/main/resources/templates/generate.html` (paste checkbox ~63–66, bulk ~200–201)

**Interfaces:**
- Consumes: existing JSON vs CSV detection in `buildReviewUserMessage`
- Produces: review hint must include both mistake-fix and add-missing-coverage instructions

JSON `formatHint` (exact intent, wording may match this):

```
Fix authoring mistakes in existing cases first (empty-field TestData must be blank on that step; use 'Leave the … field empty'; combined login uses 'Email or phone field'; quote exact UI error messages). Then add ONLY missing test cases vs the original stories. Reply with the full merged JSON object (all cases) and updated coverageNotes.
```

CSV fallback:

```
Fix authoring mistakes in existing rows first (empty-field TestData blank on that step; Leave the … field empty; Email or phone field on combined login; quote exact UI errors). Then add ONLY missing test cases as additional CSV rows (same header). Reply with full merged CSV (all rows) then ---KEEL_COVERAGE--- and updated coverage notes.
```

(`COVERAGE_MARKER` is already `"---KEEL_COVERAGE---"`.)

UX copy (exact):

| Place | Copy |
|-------|------|
| Paste `<span>` next to `#review-pass` | `Second pass — fix mistakes and add missing coverage (slower)` |
| Paste hint under that label | `Runs Ollama twice: first generates TCs, then asks the model to fix authoring mistakes and add only gaps vs your stories. Roughly doubles wait time. Keel also auto-repairs known empty-field TestData mistakes even when this is off.` |
| Bulk `<span>` next to `#bulk-review-pass` | `Second pass per story — fix mistakes and coverage (slower)` |

- [ ] **Step 1: Write the failing test**

```java
    @Test
    public void buildReviewUserMessage_asksToFixMistakesAndAddCoverage() {
        String json = "{\"testCases\":[{\"tcId\":\"TC_01\",\"title\":\"t\",\"steps\":\"1. Open\",\"expectedResult\":\"Shown\",\"keelPath\":\"EXECUTE\"}],\"coverageNotes\":\"\"}";
        String msg = TcGenerateService.buildReviewUserMessage("As a user I can login", json);
        String lower = msg.toLowerCase();
        Assert.assertTrue(lower.contains("fix"), msg);
        Assert.assertTrue(lower.contains("missing"), msg);
        Assert.assertTrue(lower.contains("leave the") || lower.contains("email or phone"), msg);
        Assert.assertTrue(msg.contains("As a user I can login"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=TcGenerateServiceQualityRetryTest#buildReviewUserMessage_asksToFixMistakesAndAddCoverage" test`

Expected: COMPILE FAIL (`buildReviewUserMessage` private) or assertion FAIL (coverage-only hint).

- [ ] **Step 3: Write minimal implementation**

Change `private static String buildReviewUserMessage` to `static String buildReviewUserMessage`. Replace `formatHint` strings. Update the three `generate.html` copy strings. Do not change checkbox ids or JS that posts `reviewPass`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q "-Dtest=TcGenerateServiceQualityRetryTest,GenerateAuthoringRepairTest,FacebookLoginNegativeGenerateTest,GenerateAuthoringRulesTest" test`

Expected: PASS.

Grep `generate.html` for the new paste span text to confirm copy.

- [ ] **Step 5: Do not commit** (global constraint)

---

## Spec coverage

| Spec section | Task |
|--------------|------|
| §4 generate flow (repair, retry, repair) | Task 4 |
| §5 blank TestData + later lines + null | Task 1 |
| §5 combined login + Leave empty | Task 2 |
| §5 literal `\n` | Task 3 |
| §5 still-fail vague/blank (quality retry) | Task 4 keeps blank-steps test |
| §6 review prompt + UX copy | Task 5 |
| §7 new class + no mutate + info log | Tasks 1, 4 |
| §8 no-op if error count not smaller | Task 4 `applyAuthoringRepair` |
| §9 stub llmCallCount==1 | Task 4 |
| Phone-field generateStory now succeeds | Task 4 Facebook test rewrite |
| Gate still rejects unrepaired rows | Task 2 runs `GenerateAuthoringRulesTest` |

## Placeholder scan

No TBD / implement later. Commit steps are explicitly skipped per global constraint.
