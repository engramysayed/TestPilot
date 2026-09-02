# Delivery polish: UI-TARS honesty, Submit bind proof, emit hygiene — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.  
> **Do not start implementation until the product owner explicitly picks a task (or says “start Task N”).**  
> **Do not commit unless the user asks.**

**Goal:** Close the highest-risk gaps left after codegen identity + Submit scoring + gender `field=` work: make UI-TARS visual results trustworthy, prove Submit prefers buttons on Facebook-shaped DOM, and harden emit so bad packages fail closed before partial writes.

**Architecture:** Four independent workstreams that can ship alone. Prefer small TDD slices against existing gates (`VisionAssertionGate`, `StepIntentBinder`, `CodegenSmellCheck`, Facebook/SauceDemo IR fixtures). Live browser re-prove is optional and called out explicitly — never required to validate unit/integration tasks.

**Tech Stack:** Java 21, TestNG, Ollama `ui-tars` / `qwen`, existing prove/heal/codegen pipeline, Facebook IR under `delivery-store/facebook-com/prj_e9a9fc7313b2/`, SauceDemo evidence under `delivery-store/saucedemo-com/prj_12e1b27d7786/`.

## Global Constraints

- Site-agnostic rules only (no Facebook/SauceDemo hardcoding in production logic).
- Fail closed on dishonest vision PASS and on codegen smell patterns.
- Prefer fixtures + unit tests over full live re-prove unless Task 2B / 4 is explicitly chosen.
- Do not commit unless asked.
- Compile/test with: `mvn -q "-Dmaven.compiler.release=21" -Dtest=… test`

---

## Recommendation (read this first)

| Priority | Task | Value for you | Effort | Depends on live browser? |
|----------|------|---------------|--------|---------------------------|
| **P0 — start here** | **Task 1** UI-TARS honesty + prompt parity | **Highest.** Last SauceDemo run “PASS conf=0.9” with placeholder `what is visible` / `why`. Gate code exists but needs proof it is wired end-to-end and UiTars prompts match Qwen. Without this, green jobs can lie. | S–M | No (unit + fixture); optional live smoke |
| **P1** | **Task 2A** Submit bind Facebook-shaped candidate table | High. Unit tests already prefer button over bare Sign-up link; IR still records the link. A Facebook-shaped *candidate list* fixture locks the bind without a full re-prove. | S | No |
| **P2** | **Task 3** Smell check before write + naming leftovers | Medium. Avoids half-written Actions on smell fail; cleanup only. | S | No |
| **P3** | **Task 2B** Live Facebook Submit re-prove (optional) | Medium–high *if* you care about regenerating the customer package from truth. Expensive and flaky (CAPTCHA / Meta UI). | L | **Yes** |
| **P4** | **Task 4** UI-TARS heal stress run (optional) | Strategic learning. Last Facebook/SauceDemo greens used DOM retry / memory, **not** vision heal (`vision: 0`). Proves whether UI-TARS helps when DOM bind fails. | L | **Yes** |

**Product-owner default:** do **Task 1 → Task 2A → Task 3** in that order. Skip 2B/4 until you want empirical vision evidence or a refreshed Facebook ZIP.

**Already done (do not redo):**
- Codegen identity (`label` / `aria-label` / smell gate / Facebook IR distinct methods)
- Submit vs Sign-up **scoring** (`formSubmitPreference`, `SubmitVsSignUpBindTest`)
- Intent `field=` → `select_Gender` (`IntentFieldRationaleTest`, IR patches)

**Evidence that motivates this plan:**
- SauceDemo `visual-assert.json`: `provider=uitars`, `status=PASS`, `observation="what is visible"`, `evidence="why"`
- Log: `VISION_ASSERT: tc=TC_SD_E2E_01 status=PASS conf=0.9 provider=uitars`
- Facebook last run: 7/7 PASSED, heal `vision: 0`; Aug 21 logs show `HEAL_RETRY` only — no `provider=uitars` for FB
- Gate already knows placeholders: `VisionAssertionGate.isPromptPlaceholder` / `honestyCheck`

---

## File map

| Area | Files |
|------|--------|
| Vision honesty | `VisionAssertionGate.java`, `VisionAssertionGateTest.java`, `ProvePhase.java` (wiring), `UiTarsVisionProvider.java`, `QwenVisionProvider.java`, `VisionPromptTemplate.java` (if shared) |
| Assert evidence | SauceDemo fixture copy under `src/test/resources/delivery/vision/` (optional) |
| Submit bind | `StepIntentBinder.java`, `SubmitVsSignUpBindTest.java` (extend) |
| Emit hygiene | `CodeWriter.java`, `CodegenSmellCheck.java`, `CodegenSmellCheckTest.java` |
| Optional live | Facebook project `prj_e9a9fc7313b2`, portal/CLI conversion job |
| Optional heal stress | Heal cascade + vision grounding config; synthetic page or SauceDemo with forced weak locators |

---

### Task 1: UI-TARS honesty — prove gate + prompt parity (P0)

**Why:** A PASS with placeholder text is worse than UNCERTAIN — it teaches false confidence. Gate logic exists; confirm it is applied on the prove path and that UiTars assert prompts forbid placeholder echo the same way Qwen does.

**Files:**
- Modify / verify: `src/main/java/delivery/vision/VisionAssertionGate.java`
- Modify / verify: `src/test/java/delivery/vision/VisionAssertionGateTest.java`
- Modify / verify: `src/main/java/delivery/job/ProvePhase.java` (must call `honestyCheck` / `evaluate` — not raw provider PASS)
- Modify: `src/main/java/delivery/vision/UiTarsVisionProvider.java` (assert prompt)
- Reference: `src/main/java/delivery/vision/QwenVisionProvider.java` (`SYSTEM_PROMPT` / assert prompt — “Do not copy placeholder…”)
- Optional fixture: copy SauceDemo evidence JSON fields into a unit test input (do not depend on `delivery-store` path existing in CI)

**Interfaces:**
- Consumes: `VisionAssertionResult` with `status=PASS`, placeholder observation/evidence
- Produces: `VisionAssertionStatus.UNCERTAIN` (or non-PASS) with reason containing `placeholder`
- Prove path must not treat placeholder PASS as suite success for visual gate

- [x] **Step 1: Confirm / extend failing-or-missing tests for SauceDemo-shaped payload**
- [x] **Step 2: Run** VisionAssertionGateTest
- [x] **Step 3: Trace ProvePhase** — uses `VisionAssertionGate.evaluate`; placeholder demote covered
- [x] **Step 4: Align UiTars assert prompt with Qwen** — shared `ASSERT_SYSTEM_PROMPT` + stronger ban text
- [x] **Step 5: Optional offline fixture test** — Fake provider + evaluate
- [x] **Step 6: Regression** — gate + ProvePhase policy tests green
- [ ] **Step 7: Commit only if asked**

**Done when:** Placeholder PASS cannot survive `honestyCheck`; ProvePhase uses the gate; UiTars assert prompt explicitly forbids placeholder echo; tests green. **Status: DONE (2026-08-21).** See scorecard.

---

### Task 2A: Submit bind — Facebook-shaped candidate table (P1, no live browser)

**Why:** Scoring already prefers Sign-up **button** over bare `<a>Sign up</a>`. Customer IR still has the link because it was proven earlier. Lock the bind against a candidate table that mirrors Facebook’s strategies (xpath Sign-up link + buttonish submit/sign-up) so future proves don’t regress.

**Files:**
- Modify: `src/test/java/delivery/authoring/SubmitVsSignUpBindTest.java`
- Touch prod only if a new gap appears: `src/main/java/delivery/authoring/StepIntentBinder.java`

**Interfaces:**
- Consumes: `List<DomCandidate>` shaped like live extract (css/xpath, tags `button`/`a`, labels)
- Produces: bind locator for “Click the Submit button” ≠ Sign-up **anchor** xpath when a button CTA exists

- [x] **Step 1: Facebook-shaped Submit bind test**
- [x] **Step 2: Literal Submit + Sign-up link**
- [x] **Step 3: Javadoc — IR needs Task 2B**
- [x] **Step 4: Regression tests green**
- [ ] **Step 5: Commit only if asked**

**Done when:** Facebook-shaped Submit bind is locked in tests. **Status: DONE.**

---

### Task 2B: Live Facebook Submit re-prove (P3, optional)

**Why:** Only way to refresh IR + customer package so generated tests call a real Submit/sign-up **button** instead of `click_Sign_Up_Button` from the anchor.

**Files / artifacts:**
- Live: portal job or CLI against `https://www.facebook.com/reg/` (or current base URL)
- Outputs: `delivery-store/facebook-com/prj_e9a9fc7313b2/ir/*.json`, framework pages/tests
- Verify: `FacebookIrCodegenIdentityTest` still green after regen

**Risks:** CAPTCHA, login walls, UI churn, slow. Do not treat as CI.

- [ ] **Step 1: Snapshot current IR** — not needed (no live rewrite)
- [ ] **Step 2: Run conversion UPDATE** — **SKIPPED this session** (Meta CAPTCHA / unsafe unattended)
- [x] **Step 3: Diff IR click steps** — still Sign-up **anchor** xpath (documented in scorecard)
- [x] **Step 4: FacebookIrCodegenIdentityTest** still green without IR rewrite
- [x] **Step 5: Record** — see `2026-08-21-delivery-polish-uitars-scorecard.md`

**Status: DEFERRED LIVE.** Unit bind preference (2A) is in place; package IR unchanged until you run portal re-prove.

---

### Task 3: Emit hygiene — smell before write + naming leftovers (P2)

**Why:** `CodeWriter` currently writes then `CodegenSmellCheck.verify`. A smell throws after partial files exist. Move verify earlier (build models → verify → write) or write to temp then publish.

**Files:**
- Modify: `src/main/java/delivery/codegen/CodeWriter.java`
- Modify / extend: `src/test/java/delivery/codegen/CodegenSmellCheckTest.java`
- Optional: `PageAccumulator` / naming only if leftover weak tokens remain

**Interfaces:**
- Consumes: `Map<String, PageAccumulator.PageModel>`
- Produces: no Actions/Locators/test files written if smell fails

- [x] **Step 1: Failing/extended test** — smell before write
- [x] **Step 2: Implement** — verify then `writeVerified`
- [x] **Step 3: Run** CodegenSmellCheckTest + FacebookIrCodegenIdentityTest
- [x] **Step 4: Optional naming** — skipped (no leftover collapse)
- [ ] **Step 5: Commit only if asked**

**Status: DONE.**

---

### Task 4: UI-TARS heal stress run (P4, optional / learning)

**Why:** Last greens do not prove UI-TARS heals locators. Scoreboards show `vision: 0`. Need a deliberate scenario where DOM bind is weak/wrong and vision bbox/shortlist can win.

**Files / config:**
- `application.properties`: `delivery.vision.provider=uitars`, `delivery.vision.model=ui-tars`
- Heal cascade path (vision tier) in prove/heal
- Prefer a **synthetic local HTML** fixture app or SauceDemo with intentionally ambiguous twins — avoid Meta for heal stress

**Suggested protocol:**
1. Pick 1–3 steps known to be AMBIGUOUS or wrong under DOM-only.
2. Enable vision grounding/heal.
3. Log: `HEAL_VISION_*`, provider id, chosen candidate id, before/after locator.
4. Write a short results note under `docs/superpowers/plans/` or `docs/` (only if user wants docs): hit rate, latency, false picks.
5. Do **not** change product defaults based on one flaky run.

- [x] **Step 1: Ollama check** — attempted; unattended shell limited — see scorecard
- [x] **Step 2: Design fixture** — `UiTarsHealStressTest` (Fake = ui-tars stand-in)
- [x] **Step 3: Run prove/heal fixture** — vision tier fired, grounded `continue-control`
- [x] **Step 4: Scorecard** — `2026-08-21-delivery-polish-uitars-scorecard.md`
- [x] **Step 5: Follow-ups** — live Ollama ui-tars latency still manual

**Status: FIXTURE DONE.** Live model stress remains optional manual.

---

## Verification (definition of done for the recommended trio)

1. Task 1: Placeholder visual PASS → UNCERTAIN; ProvePhase wired; UiTars prompt aligned; `VisionAssertionGateTest` green.
2. Task 2A: Facebook-shaped Submit bind tests green; documented that IR needs 2B to refresh package.
3. Task 3: Smell fails before durable write; identity tests green.
4. Optional 2B/4: only if explicitly requested; results recorded.

## Self-review

- Spec coverage: honesty gap → Task 1; Submit package truth → 2A/2B; emit safety → 3; vision heal unknown → 4.
- No TBD placeholders; live work isolated as optional.
- Tasks independently shippable; recommended order maximizes truthfulness of “green” before cosmetics.

## Execution handoff

Plan saved to `docs/superpowers/plans/2026-08-21-delivery-polish-uitars-emit.md`.

**When ready, say e.g.:**
- `start Task 1` (recommended)
- `start Task 1–3`
- `start Task 2B` / `start Task 4` (live)

**Do not implement until then.**
