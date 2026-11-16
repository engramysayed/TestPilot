# Bug Hunter Quality Pass 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented 2026-09-11.

**Goal:** Preferred-hook Axis login, safe `$TARGET_*` credential tokens for planners, settle/toast waits, strategy auto-advance, bug dedupe + end-of-hunt triage, login-feature oracle fix, plus coverage/JSON hardening.

**Architecture:** Extend `JobLoginService` and hunt context/executor for hooks + secret tokens; tighten prompts and `LiveHuntService` control loop; add `HuntBugDedupe` + optional planner triage pass before pack write; soft-fail triage on bad JSON.

**Tech Stack:** Java 21, Selenium 4.49, TestNG, existing `PreferredHooksStore`, `CursorHuntPlanner` / `OllamaHuntPlanner`, `HuntOracle`.

**Spec:** `docs/superpowers/specs/2026-09-10-bug-hunter-quality-pass2-design.md`

## Global Constraints

- Never put plaintext password in planner prompts or pack text files (`planner-prompt.txt`, journal, bug CSV).
- Password resolution only in-memory at `type` execute time via `HuntSecretResolver`.
- Prefer keep when triage is unsure.
- No Claude models.
- TDD where practical; commit only when user asks.

---

### Task 1: Preferred-hook login CSS in JobLoginService

**Files:**
- Modify: `src/main/java/delivery/job/JobLoginService.java`
- Test: `src/test/java/delivery/job/JobLoginServiceHooksTest.java` (new)

**Interfaces:**
- Produces: `loginIfNeeded(factory, request, List<String> preferredHooks)` overload; package-visible `usernameCss(hooks)`, `passwordCss(hooks)`, `submitCss(hooks)` for tests.

- [ ] **Step 1: Write failing tests** that `usernameCss(List.of("data-axis-test-id"))` contains `data-axis-test-id` user/email patterns and still includes legacy `name=username` fallback.

- [ ] **Step 2: Implement CSS builders + overload**; keep old `loginIfNeeded(factory, request)` calling overload with `List.of()`.

- [ ] **Step 3: Wire hunt open** — `LiveHuntService.openSiteThenMaybeLogin` loads hooks via `PreferredHooksStore.load(storeRoot, baseUrl)` and passes them (add `storeRoot` / hooks param if missing).

- [ ] **Step 4: Run** `mvn -q -Dtest=JobLoginServiceHooksTest test` — PASS.

---

### Task 2: HuntSecretResolver + type token expansion

**Files:**
- Create: `src/main/java/delivery/hunt/HuntSecretResolver.java`
- Modify: `src/main/java/delivery/hunt/HuntActionExecutor.java`
- Modify: `src/main/java/delivery/hunt/LiveHuntService.java`
- Test: `src/test/java/delivery/hunt/HuntSecretResolverTest.java`

**Interfaces:**
- Produces: `HuntSecretResolver.of(username, password, otpHint)` with `resolve(String value)` supporting `${TARGET_USERNAME}`, `$TARGET_USERNAME`, `{{TARGET_USERNAME}}` (PASSWORD, OTP).
- Executor: `setSecretResolver(HuntSecretResolver)`; `type` uses `resolver.resolve(value)` before `sendKeys`; actions-log stores pre-resolve value if it contained a token (keep token in log).

- [ ] **Step 1: Failing tests** for alias forms + blank password → empty string + no throw.

- [ ] **Step 2: Implement resolver + executor wiring** from `LiveHuntService` using `loginRequest` username/password and OTP parsed from `request.getUserStory()` (regex `\b(\d{4,8})\b` near `otp` case-insensitive, or explicit `245345` from story).

- [ ] **Step 3: Run tests** — PASS.

---

### Task 3: Planner context + prompt credentials section (tokens only)

**Files:**
- Modify: `src/main/java/delivery/hunt/HuntPlanner.java` (Context fields)
- Modify: `src/main/java/delivery/hunt/OllamaHuntPlanner.java`
- Modify: `tools/cursor-heal/heal.mjs` (hunt prompt rules)
- Modify: `src/main/java/delivery/hunt/LiveHuntService.java` (build Context)
- Test: `src/test/java/delivery/hunt/HuntPlannerPromptCredsTest.java`

**Interfaces:**
- Context adds: `boolean hasCredentials`, `String credentialUsername`, `String credentialOtpHint` (no password field).

- [ ] **Step 1: Failing test** — `buildUserPrompt` with hasCredentials=true contains `$TARGET_PASSWORD` / `${TARGET_PASSWORD}` and username token, **does not** contain a sample password string `"s3cret"`.

- [ ] **Step 2: Implement prompt section + Cursor/Ollama system rules** (scenario drip, wait after submit, no duplicate bugs, use tokens for happy path).

- [ ] **Step 3: Run tests** — PASS.

---

### Task 4: Post-click settle wait

**Files:**
- Modify: `src/main/java/delivery/hunt/HuntActionExecutor.java`
- Test: `src/test/java/delivery/hunt/HuntActionSettleTest.java`

**Interfaces:**
- Produces: after successful `click`, `Thread.sleep(SETTLE_AFTER_CLICK_MS)` default **1000** (constant, cap 2000).

- [ ] **Step 1: Test** with mock driver — click then next action; verify settle constant exists and click path sleeps (spy/clock optional: assert constant + call `settleAfterClick()` package method).

- [ ] **Step 2: Implement** — PASS.

---

### Task 5: Toast/alert settle after 401

**Files:**
- Create: `src/main/java/delivery/hunt/HuntAlertPoller.java` (optional small helper)
- Modify: `src/main/java/delivery/hunt/LiveHuntService.java` (after executeAll / before oracle)
- Test: `src/test/java/delivery/hunt/HuntAlertPollerTest.java`

**Interfaces:**
- `HuntAlertPoller.poll(driver, Duration.ofMillis(2000))` → `List<String>` alert texts.
- If cycle `netFails` has status ≥401, poll once and merge into oracle alert list / page signals.

- [ ] **Step 1–3: TDD helper + wire LiveHuntService** — PASS.

---

### Task 6: Strategy auto-advance when happy blocked

**Files:**
- Modify: `src/main/java/delivery/hunt/LiveHuntService.java`
- Modify: `src/main/java/delivery/hunt/HuntStopRules.java` (if cleaner)
- Test: `src/test/java/delivery/hunt/HuntStrategyAdvanceTest.java`

**Interfaces:**
- After each cycle: if strategies on, mode=`happy`, cyclesInHappy≥2, URL still login-like, and (!hasCredentials || no leave-login), `sequencer.advance()` + `HuntRunLog`.

- [ ] **Step 1: Unit-test pure helper** `shouldAdvanceHappy(mode, happyStreak, url, hasCreds, leftLogin)` — PASS then wire.

---

### Task 7: Bug fingerprint dedupe

**Files:**
- Create: `src/main/java/delivery/hunt/HuntBugDedupe.java`
- Modify: `src/main/java/delivery/hunt/LiveHuntService.java`
- Modify: `src/main/java/delivery/hunt/HuntPackWriter.java` (optional `bug-dedupe.json`)
- Test: `src/test/java/delivery/hunt/HuntBugDedupeTest.java`

**Interfaces:**
- `fingerprint(Map bug)`, `addUnique(List all, Map bug) → boolean added`.

- [ ] **Step 1–3: TDD + replace raw `allBugs.add`** — PASS.

---

### Task 8: Login-feature mode — suppress unexpected login URL oracle

**Files:**
- Modify: `src/main/java/delivery/hunt/HuntOracle.java`
- Modify: `src/main/java/delivery/hunt/LiveHuntService.java` (pass flag)
- Test: `src/test/java/delivery/hunt/HuntOracleLoginFeatureTest.java`

**Interfaces:**
- `PageSignals` add `boolean loginFeature` **or** overload `collect(..., boolean loginFeature)`.
- Detect: brief/TCs/baseUrl/path contain login|sign-?in.

- [ ] **Step 1: Failing test** — URL `.../login` + loginFeature=true → no unexpected-URL bug.

- [ ] **Step 2: Implement** — PASS.

---

### Task 9: End-of-hunt bug triage (planner recheck)

**Files:**
- Create: `src/main/java/delivery/hunt/HuntBugTriage.java`
- Modify: `src/main/java/delivery/hunt/HuntPlanner.java` (optional `triage` default method or separate client call via Cursor/Ollama)
- Modify: `src/main/java/delivery/hunt/CursorHuntPlanner.java` / `OllamaHuntPlanner.java`
- Modify: `src/main/java/delivery/hunt/LiveHuntService.java`
- Test: `src/test/java/delivery/hunt/HuntBugTriageTest.java`

**Interfaces:**
- `HuntBugTriage.apply(List bugs, TriageDecision)` mechanical keep/drop/merge.
- `HuntBugTriage.parse(String json)`.
- Planner: `triage(String prompt) → String raw` or reuse heal client with triage system prompt.
- Soft-skip on parse failure; write `bug-triage.json`.

- [ ] **Step 1: Unit-test apply/parse** without network.

- [ ] **Step 2: Wire LiveHuntService** after loop, before pack; log keep/drop counts.

- [ ] **Step 3: Prompt text** — when unsure, keep; do not invent bugs.

---

### Task 10: Coverage “untested” hints (item 10)

**Files:**
- Modify: `src/main/java/delivery/hunt/HuntCoverageMap.java`
- Modify: `src/main/java/delivery/hunt/OllamaHuntPlanner.java` (ensure section shown)
- Test: `src/test/java/delivery/hunt/HuntCoverageUntestedTest.java`

**Interfaces:**
- `coverage.noteGap(String)` / derive from missing OTP, empty-field, invalid-login actions.
- `forPrompt()` appends `## Untested` bullets.

- [ ] **Step 1–3: TDD** — PASS.

---

### Task 11: Stronger planner JSON repair (item 11)

**Files:**
- Modify: `src/main/java/delivery/hunt/HuntPlannerDecision.java` (or parse helper)
- Test: `src/test/java/delivery/hunt/HuntPlannerDecisionRepairTest.java`

**Interfaces:**
- Extract outermost JSON object; strip NULs/fences; validate `decision` + `actions` array types against allowlist; one repair retry hook already in Ollama — ensure Cursor path shares it.

- [ ] **Step 1–3: Failing fixtures from past bad Cursor JSON → PASS after repair.**

---

### Task 12: Verification + pack smoke

- [ ] **Step 1:** Run  
  `mvn -q "-Dtest=JobLoginServiceHooksTest,HuntSecretResolverTest,HuntPlannerPromptCredsTest,HuntActionSettleTest,HuntAlertPollerTest,HuntStrategyAdvanceTest,HuntBugDedupeTest,HuntOracleLoginFeatureTest,HuntBugTriageTest,HuntCoverageUntestedTest,HuntPlannerDecisionRepairTest,HuntRestartBrowserTest,HuntActionNormalizerTest" test`

- [ ] **Step 2:** Confirm no password appears in a sample `buildUserPrompt` output under test.

- [ ] **Step 3:** Manual: rebuild portal, hunt Axis login with credential profile + Cursor — expect prelude success or soft-fail with axis selectors attempted; happy path uses tokens; bug count ≪ 29 with triage file present.

---

## Out of scope (this plan)

- UI toggle for triage on/off (always on when planner available; soft-skip if fails)
- Plaintext password in prompt (rejected — option B)
- Parallel multi-browser hunts
