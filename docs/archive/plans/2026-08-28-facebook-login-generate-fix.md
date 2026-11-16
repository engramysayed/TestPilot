# Facebook Login Generate Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Generate succeed for the Facebook Login negative-testing user story below, with clear failure messages when it does not, and automated regression tests so the fix stays fixed.

**Architecture:** First reproduce and capture the real per-story error (today hidden by batch summary). Fix root causes in prompt/gate alignment for combined Email/Mobile identifier fields. Improve batch job failure UX to surface the last story error. Lock behavior with stubbed-LLM integration tests using the exact user story fixture, then verify live with Ollama.

**Tech Stack:** Java 21, TestNG, Spring Boot portal, `TcGenerateService`, `GenerateBatchJobRunner`, `GenerateQualityGate`, `GenerateAuthoringRules`, Ollama (live smoke only).

## Global Constraints

- Do not commit unless the user asks.
- Prefer extending existing gate/retry/batch flow over new services.
- Unit tests must not require Ollama; live smoke is a separate manual gate.
- Rules stay generic (content-based), not Facebook-URL-only hacks.
- Security / maintainability: do not swallow exceptions; preserve stack traces in logs.

## User story fixture (exact input for all tests)

Save as `src/test/resources/generate/facebook-login-negative-us.txt`:

```text
# User Story: Facebook Login — Negative Testing

**As a** Facebook user,
**I want** the login system to reject invalid login attempts and handle abnormal input correctly,
**So that** unauthorized users cannot access an account and the application remains stable.

#Scope

The login page contains:

* Email / Mobile number field
* Password field
* Log In button

#Negative Scenarios to Cover

1. **Missing required credentials**
   Attempt to log in with one or both required fields empty.

2. **Invalid credentials**
   Attempt to log in using an incorrect password or an invalid/unregistered account identifier.

3. **Malformed / extreme input**
   Attempt to log in using invalid formats, special characters, whitespace-only input, or values exceeding reasonable field boundaries.

4. **Abnormal application conditions**
   Attempt to log in while the network is unavailable, the authentication request times out, or the user rapidly submits the login request multiple times.
```

**Project context for tests:** `baseUrl = https://www.facebook.com/`, project name e.g. `Facebook Login Negative`.

**Acceptance coverage checklist** (live or stubbed output must include at least one TC per group):

| Group | Must appear in generated TCs |
|-------|------------------------------|
| 1 Missing credentials | Empty Email/Mobile and/or empty Password |
| 2 Invalid credentials | Wrong password and/or unregistered identifier |
| 3 Malformed input | Special chars, whitespace-only, oversized values |
| 4 Abnormal conditions | Network off / timeout / double-submit (typically `MANUAL` or `EXECUTE`) |

---

## Root-cause hypotheses (confirm in Task 1)

| # | Hypothesis | How to confirm |
|---|------------|----------------|
| H1 | **Quality gate** rejects model output (Phone/Email field labels, vague asserts, empty-field drift) | Message flashes `Failed …: QUALITY_GATE:` or stub test fails gate |
| H2 | **Prompt contradiction** — AUTHORING RULES line 43 says `Enter in the Email field` while LOGIN section forbids it for combined boxes | Read prompt; model output uses wrong labels |
| H3 | **Ollama** timeout/unavailable | Message `OLLAMA_TIMEOUT` / `OLLAMA_UNAVAILABLE` |
| H4 | **Unparseable LLM JSON** | Parse exception in logs |
| H5 | **UX only** — generation actually failed for H1–H4 but status page shows generic batch message | Compare progress message vs final message |

---

## File map

| File | Responsibility |
|------|----------------|
| `GenerateBatchJobRunner.java` | Track `lastStoryFailure`; include in thrown exception |
| `GenerateBatchWorker.java` | Persist last story error on job `message`/`error` |
| `status.html` | Show `error` / last story failure prominently for GENERATE_BATCH |
| `keel-tc-generate-from-stories-to-json.txt` | Fix Email/Mobile combined-field guidance; add negative-US example |
| `keel-tc-generate-from-stories-to-csv.txt` | Mirror JSON prompt |
| `GenerateAuthoringRules.java` | Optional: accept `Email / Mobile number` phrasing in combined-field detection |
| `FacebookLoginNegativeGenerateTest.java` | Stub-LLM end-to-end: bad output fails, good fixture passes gate + coverage |
| `GenerateBatchJobRunnerFailureTest.java` | Batch with one failing story surfaces real error |
| `facebook-login-negative-us.txt` | Shared fixture |
| `facebook-login-negative-golden.json` | Gate-passing stub LLM response covering 4 scenario groups |

---

### Task 1: Reproduce and capture the real failure

**Files:**
- Read: `test-output/default-run/logs/logs.log`
- Read: H2 job row for `genb_0c264bf9bea3` (if portal stopped)
- Create: `docs/superpowers/plans/2026-08-28-facebook-login-generate-fix-notes.md` (findings only)

- [ ] **Step 1: Restart portal** (user or agent) so latest code is running.

Run (PowerShell):
```powershell
cd D:\priv\testpilot\TestPilot
mvn -q -DskipTests spring-boot:run
```

- [ ] **Step 2: Run Generate with the fixture US**

1. Open `/generate`, select Facebook project (`baseUrl` = `https://www.facebook.com/`).
2. Paste the full user story fixture.
3. Model: default (`gemma4:e2b` or configured default).
4. Review pass: **off** (faster first repro).
5. Click **Generate** → status page.

- [ ] **Step 3: Watch Message while polling**

Before job ends, note if Message shows:
`Failed US_…: QUALITY_GATE: …` or `OLLAMA_*` or parse error.

- [ ] **Step 4: Record findings in notes file**

Template:
```markdown
## Repro run YYYY-MM-DD
- jobId:
- Final status:
- Progress message before overwrite:
- Likely hypothesis: H1|H2|H3|H4|H5
- Gate errors (if any):
```

**Expected:** At least one hypothesis confirmed. If H5 only, proceed to Task 2 immediately.

---

### Task 2: Surface per-story failure in batch jobs (TDD)

**Files:**
- Modify: `src/main/java/delivery/job/GenerateBatchJobRunner.java`
- Modify: `src/main/java/delivery/portal/worker/GenerateBatchWorker.java`
- Modify: `src/main/resources/templates/status.html`
- Create: `src/test/java/delivery/job/GenerateBatchJobRunnerFailureTest.java`

**Interfaces:**
- Produces: `GenerateBatchJobRunner` throws `IllegalStateException` with message like `No test cases were generated from bulk stories — last failure: Failed US-1: QUALITY_GATE: …`
- Produces: `JobRecord.message` retains last story failure when batch fails

- [ ] **Step 1: Write failing test**

```java
@Test
public void allStoriesFail_throwsWithLastStoryError() throws Exception {
    // One-story bulk CSV from fixture US
    // Stub TcGenerateService throws IllegalArgumentException("QUALITY_GATE: tcId 'TC_01': vague assert")
    // Assert thrown message contains "QUALITY_GATE" and "vague assert"
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `mvn -q -Dtest=GenerateBatchJobRunnerFailureTest test`

- [ ] **Step 3: Implement last-failure tracking**

In `GenerateBatchJobRunner` catch block, save `lastFailure = entry.usId() + ": " + e.getMessage()`.
When `allCases.isEmpty()`, throw:
```java
throw new IllegalStateException(
    "No test cases were generated from bulk stories — last failure: " + lastFailure);
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Update status.html**

Show `data.error` or full message in `#fail-js` for GENERATE_BATCH FAILED jobs (not just generic line).

---

### Task 3: Fix prompt contradiction for Email / Mobile combined field

**Files:**
- Modify: `src/main/resources/static/prompts/keel-tc-generate-from-stories-to-json.txt`
- Modify: `src/main/resources/static/prompts/keel-tc-generate-from-stories-to-csv.txt`
- Modify: `src/test/java/delivery/portal/web/GenerateJsonPromptResourceTest.java`
- Modify: `src/test/java/delivery/portal/web/GeneratePromptResourceTest.java`

- [ ] **Step 1: Write failing prompt tests**

Assert JSON prompt:
- Does **not** recommend standalone `Enter in the Email field` for combined identifier forms
- **Does** mention `Email / Mobile number` → use `Email or phone field`
- Includes negative-only scope example aligned with fixture US

- [ ] **Step 2: Fix AUTHORING RULES section (line ~43)**

Replace:
```
For login flows use field labels in Enter steps (e.g. "Enter in the Email field", "Enter in the Password field")
```
With:
```
For login flows use exact field labels from requirements. Combined identifier boxes (Email / Mobile number, Email or phone): use "Email or phone field" in steps — never standalone "Email field" or "Phone field" unless requirements describe separate inputs. Password always uses "Password field".
```

- [ ] **Step 3: Add TINY SHAPE example for malformed-input case**

Example TC with special characters in testData, quoted validation message, `keelPath: EXECUTE`.

- [ ] **Step 4: Mirror changes in CSV prompt**

- [ ] **Step 5: Run prompt tests — expect PASS**

Run: `mvn -q -Dtest=GenerateJsonPromptResourceTest,GeneratePromptResourceTest test`

---

### Task 4: Golden stub-LLM integration test for fixture US

**Files:**
- Create: `src/test/resources/generate/facebook-login-negative-us.txt`
- Create: `src/test/resources/generate/facebook-login-negative-golden.json`
- Create: `src/test/java/delivery/portal/service/FacebookLoginNegativeGenerateTest.java`

**Golden JSON requirements:** 8–12 TCs covering all 4 scenario groups; every TC passes `GenerateQualityGate.validate(cases, "https://www.facebook.com/")`.

- [ ] **Step 1: Author golden JSON fixture**

Minimum TCs:
- TC_01: both fields empty
- TC_02: empty Email/Mobile only
- TC_03: wrong password
- TC_04: unregistered email
- TC_05: special characters in identifier
- TC_06: whitespace-only password
- TC_07: oversized identifier (boundary)
- TC_08: double-click Log In (`EXECUTE`)
- TC_09: network unavailable (`MANUAL` — document in coverageNotes)

All login steps use `Email or phone field` / `Password field`.
Empty cases use `Leave the … empty` + blank testData lines.
Assert steps quote exact Facebook-style messages where known.

- [ ] **Step 2: Write failing integration test**

```java
@Test
public void goldenFixture_passesQualityGate() throws Exception {
    List<ManualTestCase> cases = parseGoldenJson();
    List<String> errors = GenerateQualityGate.validate(cases, "https://www.facebook.com/");
    Assert.assertTrue(errors.isEmpty(), errors.toString());
    assertCoverageGroups(cases); // package-private helper
}

@Test
public void generateStory_acceptsGoldenOnFirstCall() throws Exception {
    StubTcGenerateService service = new StubTcGenerateService();
    service.llmResponses.add(readGoldenJson());
    ProjectRecord project = facebookProject();
    StoryGenerateResult result = service.generateStory(
        project, readFixtureUs(), false, "US-1", null);
    Assert.assertTrue(result.cases().size() >= 8);
    assertCoverageGroups(result.cases());
}
```

- [ ] **Step 3: Run tests — expect PASS** (golden authored to pass)

Run: `mvn -q -Dtest=FacebookLoginNegativeGenerateTest test`

- [ ] **Step 4: Add regression test — bad model output still fails**

Reuse `facebookPhoneCase()`-style JSON (standalone Phone field) → expect `QUALITY_GATE` on second call.

---

### Task 5: Optional gate tweak for Mobile wording

**Files:**
- Modify: `src/main/java/delivery/excel/GenerateAuthoringRules.java`
- Modify: `src/test/java/delivery/excel/GenerateAuthoringRulesTest.java`

Only if Task 1 shows false positives on valid `Email / Mobile number` phrasing.

- [ ] **Step 1: Write test** — steps saying `Email or mobile number field` pass gate.

- [ ] **Step 2: Extend `mentionsCombinedLoginIdentifier`** to detect `mobile number`, `email / mobile`.

- [ ] **Step 3: Run** `mvn -q -Dtest=GenerateAuthoringRulesTest test`

---

### Task 6: Live Ollama verification (manual gate)

**Files:**
- Update: `docs/superpowers/plans/2026-08-28-facebook-login-generate-fix-notes.md`

- [ ] **Step 1: Preconditions**

- Ollama running at configured `delivery.llm-base-url`
- Model pulled (e.g. `gemma4:e2b`)
- Portal restarted after Tasks 2–4

- [ ] **Step 2: Generate live**

Same steps as Task 1 but after fixes. Expect:
- Status **COMPLETED**
- Passed ≥ 8 TCs
- Download CSV / check `delivery-store/{projectId}/generated/latest.csv`

- [ ] **Step 3: Validate output against checklist**

For each of the 4 scenario groups, confirm ≥ 1 TC title/steps match.

- [ ] **Step 4: Spot-check gate**

No rows with `Enter in the Phone field`, vague asserts, or empty-email + typed testData.

- [ ] **Step 5: Record in notes**

```markdown
## Live verification
- jobId:
- TC count:
- Groups covered: 1✓ 2✓ 3✓ 4✓
- Residual gaps:
```

If live fails after 2 retries: capture gate errors from Task 2 message, add targeted prompt line, re-run (loop until green or document model limitation).

---

### Task 7: Full automated verification bundle

- [ ] **Run unit/integration bundle**

```powershell
mvn -q test "-Dtest=GenerateBatchJobRunnerFailureTest,FacebookLoginNegativeGenerateTest,GenerateAuthoringRulesTest,GenerateQualityGateTest,TcGenerateServiceQualityRetryTest,GenerateJsonPromptResourceTest,GeneratePromptResourceTest,GenerateMvcTest"
```

- [ ] **Expected:** all green

- [ ] **User action:** Re-run Generate in UI to replace `latest.xlsx` for Facebook project

---

## Self-review (spec coverage)

| Requirement | Task |
|-------------|------|
| Confirm root cause | Task 1 |
| Fix hidden error UX | Task 2 |
| Fix generation for Email/Mobile US | Tasks 3–5 |
| Automated regression with exact US | Task 4 |
| Live proof | Task 6 |
| All fixed / verified | Task 7 |

No placeholders remain; each task has files, commands, and expected outcomes.

---

## Execution handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-28-facebook-login-generate-fix.md`.**

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks
2. **Inline Execution** — run tasks in this session with checkpoints

**Which approach?**
