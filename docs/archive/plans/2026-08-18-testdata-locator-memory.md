# TestData, Locator Memory, Submit CTA, Visual Honesty — Implementation Plan

> **For agentic workers:** Use executing-plans in this session. TDD. Do **not** commit unless the user asks.

**Goal:** Fix the Facebook 153010 failures (wrong Submit, visual UNCERTAIN demotion), add optional per-step TestData with Faker when blank, and reuse proven locators per host+Excel path.

**Architecture:** Form-submit honesty in binder/heal/vision/semantic gate. Visual assert FAIL-with-evidence demotes; UNCERTAIN does not. `ManualTestCase.testData` line-aligned with Steps. `DomainLocatorMemory` tried before bind, updated after successful execute, persisted next to `locator-map.json`.

**Tech Stack:** Java 21, TestNG, Apache POI, existing DummyValueInventor / LocatorMapStore.

## Global Constraints

- Java 21 compile: `"-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21"`
- No site/product hardcoding (Facebook). `/reg/` is the Excel open-path, not a host special case.
- Vision remains a candidate generator. No `moveByOffset`.
- Do not commit unless asked.

---

### Task 1: Form Submit CTA honesty

**Files:**
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java`
- Modify: `src/main/java/delivery/heal/HealCascade.java`
- Modify: `src/main/java/delivery/job/SemanticPassGate.java`
- Modify: `src/main/java/delivery/vision/VisionProveHook.java`
- Modify: `src/main/java/delivery/authoring/AuthoringService.java`
- Test: `StepIntentBinderTest`, `SemanticPassGateTest`, `HealCascadeTest`

**Produces:** `looksLikeFormSubmitControl`, `looksLikeNonSubmitNavigation` public; bind skips distinctive-token refuse for form-submit when a submit control exists.

- [ ] Failing tests: Submit binds Sign Up / websubmit not `/reg/` href or “I already have an account”; semantic gate rejects submit→`/reg/` href; heal rejects those picks.
- [ ] Implement skip/reject in bind score, `validHealSteps`, vision hook, `stepsPreferringCandidate`.
- [ ] `mvn -q "-Dtest=StepIntentBinderTest,SemanticPassGateTest,HealCascadeTest" test` with Java 21.

### Task 2: Visual assert does not demote UNCERTAIN

**Files:**
- Modify: `src/main/java/delivery/job/ProvePhase.java` (`applyVisualAssertion`)
- Test: new `src/test/java/delivery/job/ProvePhaseVisualAssertPolicyTest.java` **or** package-visible helper on `VisionAssertionGate` / `ProvePhase`.

**Produces:** `ProvePhase.shouldDemoteForVisual(VisionAssertionResult, String assertionText, String url)` → true only for contradictory FAIL.

- [ ] Test: UNCERTAIN keeps PASSED; FAIL conf 0.0 keeps PASSED; FAIL 0.9 registration-on-login demotes; PASS unchanged.
- [ ] Implement.
- [ ] Run the new test class.

### Task 3: Optional TestData column + Faker when blank

**Files:**
- Modify: `ManualTestCase.java` (add `testData`, keep 7- and 8-arg ctors)
- Modify: `ExcelTcReader.java` (`TESTDATA` / `STEPDATA` headers)
- Modify: `StepIntentBinder.IntentLine` add `testData`; `parseIntents` aligns lines including skipped opens
- Modify: `DummyValueInventor.fromStepOrInvent` — column first; `Test`/`User` placeholders
- Modify: bind TYPE paths to pass `intent.testData()`
- Modify: `scripts/gen_facebook_reg_e2e.py` — Enter lines without literals; TestData column
- Test: `ExcelTcReaderTest`, `DummyValueInventorFakerTest`, `ManualTestCaseHashTest`, `StepIntentBinderTest`

**Produces:** `DummyValueInventor.fromStepOrInvent(stepText, columnValue, tag, inputType, name, label, placeholder)`

- [ ] Tests as in task body.
- [ ] Implement + regenerate facebook xlsx if openpyxl available.
- [ ] Run listed tests.

### Task 4: Domain locator memory

**Files:**
- Create: `src/main/java/delivery/store/DomainLocatorMemory.java`
- Create: `src/test/java/delivery/store/DomainLocatorMemoryTest.java`
- Modify: `ProvePhase` — load at prove, try before bind, remember on success, save at end of each TC
- Modify: `EmitPhase` already writes locator-map; also save memory file under project store

**Produces:**
```java
Optional<ProvenStep> recall(String host, String excelPath, IntentLine intent, String tcId);
void remember(String host, String excelPath, IntentLine intent, ProvenStep step);
void forget(String host, String excelPath, IntentLine intent);
void load(Path file); void save(Path file);
static String intentKey(IntentLine intent);
```

- [ ] Tests: remember/recall First name; failed slot forgotten; different path does not reuse; submit slot rejected if locator is `/reg/` href.
- [ ] Wire ProvePhase. Memory file: `storeRoot/{projectId}/domain-locator-memory.json`.
- [ ] Run `DomainLocatorMemoryTest`.

### Task 5: Verify

Run:
```
mvn -q "-Dtest=StepIntentBinderTest,SemanticPassGateTest,HealCascadeTest,DummyValueInventorFakerTest,ExcelTcReaderTest,ManualTestCaseHashTest,DomainLocatorMemoryTest,VisualAssertionGateTest,AuthoringServiceTest,RequiredControlFillerTest" test "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21"
```
