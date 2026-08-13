# Heal Cascade Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make live heal recover more Excel steps before honest TODO/PARTIAL by adding action history, Thought+Action prompts, vision when the DOM shortlist is weak/empty, and a validated free-invent last hope — without changing AgentRouter’s post-prove client-delivery role.

**Architecture:** Keep prove → heal → IR → honesty → optional final revise → emit. Extend `HealCascade` only: shortlist path stays candidateId-only; when shortlist is empty/exhausted or Cursor fails, run a **FreeInventHealer** that may propose locators from slim HTML (+ screenshot), then **live-validate and re-execute once**. `ProvePhase` passes prior proven steps into heal. AgentRouter stays out of heal.

**Tech Stack:** Java 21, existing `AuthoringService` / `LocalLlmClient` (Ollama), `CursorHealClient` + `heal.mjs`, `LocatorValidator`, TestNG unit tests.

## Global Constraints

- Shortlist path (Ollama + Cursor pick): still **candidateId only** — no invented locators there.
- Free-invent is **last hope only** (empty candidates, empty/exhausted shortlist after vision widen, or Cursor pick path exhausted).
- Free-invent output **must** pass `LocatorValidator` (and prefer `HtmlLocatorPresence` when applicable) before execute.
- Free-invent success requires **one live re-execute** in `ProvePhase` (same as today’s heal success path).
- Never force PASSED without prove; invent failures stay TODO/PARTIAL with clear `healSkipReason`.
- **No Gemini / no Google LLM** anywhere in heal or invent.
- **Last-hope invent providers (locked):** `delivery.heal.invent.provider=cursor|agentrouter` (default **`cursor`**). Optional AgentRouter invent is a **heal last-hope** use, separate from final-revise audit.
- Invent calls are **stateless** (no session memory). Every request must be self-contained: Excel intent, failure reason, prior completed steps in this TC, slim HTML excerpt, and screenshot (PNG bytes for AgentRouter; path + note for Cursor sidecar — attach/read file when possible).
- Shortlist Ollama path stays local vision/text for **candidateId pick only** (not invent).
- Do not print API keys; do not expand invent into multi-step fantasy beyond the current Excel intent.
- New heal tiers: `vision` (shortlist widen + screenshot pick), `invent` (free locator invent). Rank: `invent` > `cursor` > `vision` > `ollama` > `none`.
- Site-agnostic: no domain hardcoding.
- Bug caps from plan: at most **one invent attempt per intent attempt**; disable invent on batch-heal synthetic intents; feature flags `delivery.heal.invent.enabled` / `delivery.heal.vision-widen.enabled`.

## AgentRouter role (locked)

| Layer | Owner | AgentRouter? |
|-------|--------|--------------|
| Prove / bind / execute | `ProvePhase` | **No** |
| Heal shortlist (Ollama → Cursor pick) | `HealCascade` | **No** |
| Heal free-invent last hope | **Cursor (default)** or **AgentRouter** if `delivery.heal.invent.provider=agentrouter` | **Optional invent only** |
| Honesty demote | `RevisePhase` / `SemanticPassGate` | **No** |
| Client delivery final revise | `FinalRevisePhase` + `AgentRouterClient` (Opus) | **Yes — audit/demote** (unchanged) |

Final-revise AgentRouter still must **not** invent locators. Invent-mode AgentRouter (if enabled) is a **separate one-shot heal call** with full context; it shares the API key/config but not the final-revise prompt or session.

---

## Target heal flow

```text
ProvePhase (per Excel intent)
  ├─ bind + execute
  └─ on fail → HealCascade.heal(intent, html, png, reason, priorSteps, …)
        │
        ├─ [A] candidates empty?
        │     └─ FreeInventHealer → validate → steps or FAIL
        │
        ├─ [B] distinctive shortlist empty? (DOM weak)
        │     └─ widen pool + Vision shortlist pick (Thought+Action, history, screenshot)
        │           └─ ok → return tier=vision|ollama|cursor
        │
        ├─ [C] normal shortlist: Ollama → Cursor (Thought+Action + history)
        │
        └─ [D] still exhausted?
              └─ FreeInventHealer (last hope) → validate → steps or HEAL_EXHAUSTED
```

---

### Task 0: Spec lock + heal API shape

**Files:**
- Create: `docs/superpowers/specs/2026-08-13-heal-cascade-hardening-design.md` (short; points here)
- Modify: `HealResult.java` (document new tiers)
- Modify: `HealCascade.java` (signature plan only in this task via failing tests next)

**Interfaces:**

```java
// HealCascade — extended signature
HealResult heal(
    String tcId,
    StepIntentBinder.IntentLine intent,
    String slimHtml,
    byte[] pngOrNull,
    String failureReason,
    Path screenshotPathOrNull,
    boolean tryOllama,
    List<String> priorStepSummaries  // NEW: human-readable, e.g. "type → #username"
);

// FreeInventHealer
Optional<List<ProvenStep>> invent(
    String tcId,
    StepIntentBinder.IntentLine intent,
    String slimHtml,
    byte[] pngOrNull,
    String failureReason,
    List<String> priorStepSummaries,
    String whyInvoked  // "empty_candidates" | "post_cursor"
);
```

- [ ] **Step 1: Write design stub** listing tiers, AgentRouter boundary, invent allow/deny.
- [ ] **Step 2: Commit** design stub only.

---

### Task 1: Action history plumbing (`ProvePhase` → `HealCascade`)

**Files:**
- Modify: `src/main/java/delivery/job/ProvePhase.java`
- Modify: `src/main/java/delivery/heal/HealCascade.java`
- Modify: `src/main/java/delivery/heal/CursorHealClient.java`
- Modify: `tools/cursor-heal/heal.mjs`
- Modify: `src/main/java/delivery/authoring/AuthoringService.java` (`healIntentWithOllama`)
- Test: `HealCascadeTest.java`, `CursorHealClientTest.java`

**Behavior:**
- While proving a TC, keep `List<ProvenStep> provenSoFar` (already accumulated in intent loop).
- Before each `healCascade.heal(...)`, build summaries: `action + strategy + value` (truncate), max ~12 lines.
- Pass into Ollama user prompt and Cursor stdin JSON as `priorSteps`.

- [ ] **Step 1: Write failing test** — fake LLM/Cursor receives user/payload containing a prior-step line when provided.
- [ ] **Step 2: Run test** — confirm fail.
- [ ] **Step 3: Implement** signature + `ProvePhase` call sites (bind-fail heal, execute-fail heal, batch heal).
- [ ] **Step 4: Update** `healIntentWithOllama` + `heal.mjs` to include `## Already completed in this TC`.
- [ ] **Step 5: Run tests** — pass.
- [ ] **Step 6: Commit**

---

### Task 2: Thought + Action prompts (shortlist path only)

**Files:**
- Modify: `LocatorPolicy.visionHealRules()` / heal system strings in `AuthoringService`
- Modify: `tools/cursor-heal/heal.mjs`
- Modify: parsers in `AuthoringService.parseCandidateId` / `CursorHealClient.parseCandidateId` (accept Thought prose + final JSON)
- Test: unit tests for parsing `Thought: ...\n{"candidateId":"c2"}`

**Behavior:**
- Prompt asks for short Thought then Action JSON `{"candidateId":"..."}`.
- Runtime **ignores Thought** for control flow; only validates candidateId ∈ shortlist.
- Keep invent forbidden on this path.

- [ ] **Step 1: Failing parser tests** for noisy Thought+JSON responses.
- [ ] **Step 2: Implement** prompt + robust extract (last JSON object — already mostly there).
- [ ] **Step 3: Run tests + Commit**

---

### Task 3: Vision heal when DOM is weak / shortlist empty

**Files:**
- Modify: `HealCascade.java` (remove/soft early fail on empty distinctive pool for CLICK)
- Modify: `AuthoringService.shortlistForIntent` / heal pool selection
- Test: `HealCascadeTest` cases that today exhaust on distinctive tokens but have real buttons in HTML

**Behavior:**
1. If distinctive pool empty but `candidates` non-empty → **widen** to ranked full candidates (cap 12–20), require `pngOrNull` when available.
2. Run Ollama vision pick (Thought+Action) on widened shortlist → tier `vision` if screenshot used, else `ollama`.
3. On fail → Cursor on same widened shortlist.
4. If still fail → Task 4 invent (not TODO yet).

- [ ] **Step 1: Failing test** — HTML with submit button, intent tokens that current distinctive filter clears; expect heal to attempt LLM (not immediate distinctive-token exhaust).
- [ ] **Step 2: Implement** widen + logging `HEAL_VISION_WIDEN: candidates=N shortlist=M`.
- [ ] **Step 3: Wire** `healTier` `vision` in `ProvePhase.mergeHealTier` / rank.
- [ ] **Step 4: Tests + Commit**

---

### Task 4: Free-invent last hope (empty candidates + post-Cursor)

**Files:**
- Create: `src/main/java/delivery/heal/FreeInventHealer.java`
- Create: `src/test/java/delivery/heal/FreeInventHealerTest.java`
- Modify: `HealCascade.java` call invent at [A] and [D]
- Modify: `LocatorPolicy.java` — add `freeInventRules()` (allowed invent, single intent only)
- Modify: `AuthoringService` helpers to parse invent JSON → `ProvenStep` via `LocatorValidator`
- Optional: invent mode in `heal.mjs` when `mode:"invent"` and shortlist empty/absent
- Modify: `EmitPhase` heal tier summary to count `invent` / `vision`
- Modify: `ProvePhase` healTierRank

**Invent JSON contract:**

```json
{
  "thought": "short reason",
  "steps": [
    {
      "action": "click|type|select|assert",
      "locatorStrategy": "id|name|css|xpath|...",
      "locatorValue": "...",
      "value": "",
      "assertionType": "",
      "assertionExpected": ""
    }
  ]
}
```

**Hard rules for invent:**
- Max 1–3 steps for **this intent only** (no login rewrite, no navigation fantasy unless intent says so).
- Every locator step: `LocatorValidator.validate` must be valid.
- Prefer strategies already used in project (`id`, `name`, `css`, `xpath`, data-test*).
- Reject UUID-like / highly volatile values when a stabler alternative exists (reuse extractor heuristics where cheap).
- On validate fail or empty steps → fail invent (caller continues to TODO).
- Provider: **`cursor` (default)** or **`agentrouter`** via `delivery.heal.invent.provider`. Never Gemini. Never Ollama for invent (Ollama stays shortlist-only).
- Every invent prompt is **one-shot / no memory** and must include:
  1. Exact Excel intent (kind + text)
  2. Failure reason / why invent was invoked
  3. Prior completed steps in this TC (action history)
  4. Slim HTML excerpt (capped, e.g. 12–16k chars)
  5. Screenshot (required when available; if missing, invent may still try HTML-only but log `HEAL_INVENT_NO_SCREENSHOT`)
  6. Strict JSON output schema (Thought + steps with locators)
- Cursor invent: extend `heal.mjs` with `mode:"invent"`; return invent JSON (not candidateId). Sidecar should read screenshot file into the prompt context when path exists.
- AgentRouter invent: one `AgentRouterClient.completeJson` (or dedicated invent helper) with system+user; if vision API unsupported, still send HTML+history+intent; attach image when client supports it.

**Call sites in `HealCascade`:**
1. After `candidates.isEmpty()` → invent(`empty_candidates`) once.
2. After shortlist Ollama+Cursor **pick** exhausted → invent(`post_cursor`) once.

- [ ] **Step 1: Failing unit tests** — invent returns valid click for HTML with only free-text button (simulate empty extractor via injectable extract OR dedicated HTML that extractor skips if any; else stub extractor path by testing FreeInventHealer directly with HTML and fake LLM).
- [ ] **Step 2: Implement `FreeInventHealer`** + policy prompts.
- [ ] **Step 3: Integrate** into `HealCascade`; return `HealResult.success(steps, "invent")`.
- [ ] **Step 4: Update** `ProvePhase` to treat `invent` like other success tiers (re-execute already in caller).
- [ ] **Step 5: Emit/docs tier counters.
- [ ] **Step 6: Integration-style HealCascade test** — Cursor returns blank → invent succeeds.
- [ ] **Step 7: Commit**

---

### Task 5: Honesty / final revise awareness of new tiers

**Files:**
- Modify: `FinalRevisePhase` risk ordering (prefer `invent` / `vision` / healed PASSED)
- Modify: `EmitPhase` AUTOMATION_SCORE / heal summary strings
- Test: light unit test on risk sort if one exists; else assert summary counts

**Behavior:**
- Invent/vision successes remain eligible for honesty demote and Opus scrutiny.
- Final revise prompt already lists `healTier` — ensure new values documented in reviser system text.

- [ ] **Step 1: Update** risk ranking / prompt wording.
- [ ] **Step 2: Test + Commit**

---

### Task 6: Verification + ambiguity checklist pass

- [ ] **Step 1: Run** `mvn -Dmaven.compiler.release=21 -Dtest=HealCascadeTest,FreeInventHealerTest,CursorHealClientTest,AuthoringService* test` (adjust names to match).
- [ ] **Step 2: Manual smoke** — one TC that previously distinctive-exhausted; confirm logs `HEAL_VISION_WIDEN` or `HEAL_INVENT`.
- [ ] **Step 3: Confirm** AgentRouter not invoked during prove (no AgentRouter HTTP in prove logs).
- [ ] **Step 4: Commit** remaining docs (`docs/ops` heal note).

---

## Ambiguities & likely bugs (resolve during implementation)

### Product / policy

| # | Ambiguity | Decision in this plan |
|---|-----------|------------------------|
| A1 | Is invent allowed to violate “never invent”? | **Yes, last hope only**, new tier `invent`, flagged for revise |
| A2 | Should AgentRouter invent? | **Optional** via `delivery.heal.invent.provider=agentrouter`; default Cursor. Final-revise remains audit-only |
| A3 | Invent when Cursor pick skipped (no key)? | If provider=cursor and key missing → invent fails → TODO; if provider=agentrouter try that instead |
| A4 | Canvas-only UI | Invent may still fail → honest TODO (document limitation) |

### Technical bugs to watch

| # | Risk | Mitigation |
|---|------|------------|
| B1 | **Double invent / cost blowups** — heal called on bind fail and again on execute fail | Cap: at most **one invent attempt per intent attempt**; pass flag `inventTried` or cascade-internal once |
| B2 | **ProvePhase healTier rank** ignores `vision`/`invent` → wrong telemetry | Update `healTierRank` |
| B3 | **Thought+Action breaks strict JSON parsers** expecting only `{"candidateId"}` | Parse last JSON object; tests for Thought prefix |
| B4 | **Invent returns multi-step login** when intent is one click | Prompt + post-filter: drop steps that don’t match intent kind; max steps |
| B5 | **Invent locator validates syntactically but not findable** (wrong frame) | Keep single re-execute; on fail do not mark PASSED; optional note iframe in invent prompt |
| B6 | **Empty candidates but invent “succeeds” with body xpath** clicking wrong thing | Prefer interactive tags; reject `/html/body` ultra-broad xpath; distinctive token check soft-warn |
| B7 | **Widen shortlist** reintroduces wrong-control heals that distinctive filter prevented | Keep named-action / distinctive checks on **success** path (`validHealSteps`); invent path separate |
| B8 | **Cursor invent vs pick API** confusion in one sidecar | Explicit `mode: "pick" | "invent"` in stdin JSON |
| B9 | **Batch heal** in ProvePhase uses synthetic intent — invent may hallucinate** | Disable invent for batch-heal path or pass real blocker intent text only |
| B10 | **Screenshot path for Cursor invent** still “path note only” (D10) | Prefer Ollama invent with **PNG bytes** first; Cursor invent text/HTML second |
| B11 | **Honesty demote** may demote invent-PASSED that executed live | Acceptable; final revise should explain — do not special-case invent as immune |
| B12 | **Concurrent portal jobs** sharing Ollama** | Existing issue; invent adds load — keep one invent call |
| B13 | **Signature churn** — many `heal(` call sites miss `priorSteps` | Compile-fail all call sites; default `List.of()` overload for tests |
| B14 | **Free invent when HTML blank** (nav mid-fail) | Skip invent if slimHtml blank; reason `empty_html` |

### Open questions (defaults if unanswered)

1. **Invent provider:** `cursor` (default) or `agentrouter`. No Gemini. No Ollama invent.  
2. **Max invent steps:** 3.  
3. **Feature flag:** `delivery.heal.invent.enabled=true` (default true once shipped; allow kill-switch).  
4. **Vision widen flag:** `delivery.heal.vision-widen.enabled=true`.  
5. **Stateless invent:** always send intent + failure + prior steps + HTML + screenshot.

---

## Spec coverage

| Requested item | Task |
|----------------|------|
| Action history in heal | Task 1 |
| Thought + Action prompts | Task 2 |
| Vision when DOM weak / shortlist empty | Task 3 |
| Invent when candidates empty | Task 4 site [A] |
| Invent after Cursor exhausted | Task 4 site [D] |
| AgentRouter role clarified | Global + Task 5 |
| Ambiguity / bug hunt | This section + Task 6 |

## Out of scope

- Replacing Selenium ZIP with Midscene/UI-TARS runtime agents  
- AgentRouter during prove/heal  
- Visual-only assertions productization (color/layout) — later  
- Changing Excel schema or portal checkbox copy  

---

## What you will see after this ships

| Before | After |
|--------|--------|
| Early `HEAL_EXHAUSTED: no DOM candidate matches distinctive tokens` | Widen + vision/Cursor pick; often recovers |
| `empty DOM candidate table` → instant TODO | Invent attempt; TODO only if invent/validate/execute fail |
| Cursor exhausted → TODO | One invent last hope, then TODO |
| Heal prompts blind to prior steps | Prompts include already-proven actions |
| AgentRouter unrelated to heal | Still only client-delivery audit; more `invent` tiers for it to review |
