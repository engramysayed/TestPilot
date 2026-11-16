# Bug Hunter Quality Pass 2 — hooks login, safe creds, noise cut

**Date:** 2026-09-10  
**Status:** Implemented (2026-09-10) — revise notes below  
**Extends:** `2026-09-09-bug-hunter-design.md`, `2026-09-10-bug-hunter-quality-design.md`, restart_browser work  
**Evidence:** `hunt_edc8e5a58ed9` (Cursor) — 29 bug rows ≈ 3–4 unique findings; auto-login failed on Axis hooks; planner never got usable creds

## Post-implement revise — early defects found & mitigated

| Risk | Finding | Mitigation shipped |
|------|---------|-------------------|
| Empty preferred-hooks | Do not hardcode vendor attrs in login CSS | Use only project preferred hooks + legacy generics; Axis needs `data-axis-test-id` configured on the project |
| Hunter prompt noise | Told hunter “no hooks — use default” | Omit `## Locator preference` when unset; when set, clear RANK 1 steps (no vendor-specific callouts) |
| Password leak | Prompt / pack must never contain password | Tokens only; actions-log keeps token form |
| Triage drops real bugs | LLM may over-drop | Prompt: when unsure KEEP; soft-skip on bad JSON; `bug-triage.json` audit |
| Click settle slows unit tests | 1s sleep on every click | Acceptable; constant `SETTLE_AFTER_CLICK_MS` |
| OTP hint wrong digits | Story may have multiple numbers | Prefer digits near “otp”; else first 6-digit |
| `*=pass` too broad | Could match non-password | Still filtered by `input` + password type fallbacks |
| Strategy advance too eager | Advances happy after 2 login cycles | Only when still on login URL and never left login |

## Remaining watch-outs (manual)

1. Auto-login success on login-feature hunts may skip the login page — for login testing prefer **Open site only** + tokens, or accept soft-login then `restart_browser`.
2. End triage adds one Cursor/Ollama call — cost/latency; soft-skips if planner returns empty.
3. Confirm project has credential profile selected when expecting `$TARGET_*` happy path.

## Goal

Make live Bug Hunter (Cursor + Ollama) produce fewer false/noisy bugs and actually exercise login happy-path when project credentials exist — without putting the raw password in the LLM prompt.

## Decisions (locked)

| Topic | Choice |
|-------|--------|
| Planner password | **B — tokens only:** `$TARGET_USERNAME`, `$TARGET_PASSWORD`, optional `$TARGET_OTP` resolved at execute time |
| Auto-login selectors | Use **project preferred hooks** (if configured) before generic `JobLoginService` CSS — no hardcoded vendor attrs |
| End-of-hunt triage | Planner **recheck** all accumulated bugs once before pack write; mark keep / drop / merge |
| Password in prompt | **Never** plaintext password |

## Non-goals

- Storing passwords in pack artifacts / journal / bug CSV  
- Changing Execute/Prove login beyond shared `JobLoginService` improvements  
- Multi-pass visual Bugbot  
- Auto-merging candidate scenarios into the TC library  

---

## Phase A — Login that works on Axis / preferred hooks

### A.1 Preferred-hook-aware `JobLoginService`

When calling `loginIfNeeded`, pass optional `List<String> preferredHooks` (and/or overload that loads from `storeRoot` + `baseUrl`).

**Username CSS build order:**

1. For each preferred hook attr (e.g. `data-axis-test-id`):  
   `input[{attr}*='user' i], input[{attr}*='User' i], input[{attr}*='email' i], input[{attr}*='login' i]`  
2. Existing generic list (`data-test=username`, `name=username`, …)

**Password:** preferred-hook `*password*` / `*-input` patterns, then generics.

**Submit:** preferred-hook `*sign*In*`, `*login*`, `*submit*`, then existing button shortlist + Sign In xpath.

Hunt `LiveHuntService.openSiteThenMaybeLogin` and portal login paths that already have `storeRoot` must pass hooks loaded via `PreferredHooksStore.load`.

### A.2 Hunt credential tokens (option B)

Extend `HuntPlanner.Context` with:

- `hasCredentials` (boolean)  
- `credentialUsername` (string; may be blank)  
- `credentialOtpHint` (string; parsed from user story / brief when present, e.g. `245345`)  
- **Do not** put password on Context or into any prompt file written under hunt-runs if avoidable; password stays only on `ConversionJobRequest` / job record in memory

Prompt section when `hasCredentials`:

```
## Hunt credentials (use tokens — never invent other real passwords)
- Username token: ${TARGET_USERNAME}  (resolves to the project profile username)
- Password token: ${TARGET_PASSWORD}  (resolved at execute; value not shown here)
- OTP hint (if any): 245345 → use as ${TARGET_OTP} or literal when on OTP screen
Rules: happy-path / valid-login probes MUST use these tokens; negative tests use clearly fake values (invalid_*, empty).
```

`HuntActionExecutor` `type` (and optionally `execute_js` string args): replace  
`${TARGET_USERNAME}` / `$TARGET_USERNAME` / `{{TARGET_USERNAME}}` (same for PASSWORD, OTP) using secrets held by a small `HuntSecretResolver` injected into the executor from `LiveHuntService`.

Pack / journal / actions-log: write the **token form** or redact password values (never persist resolved password).

---

## Phase B — Kill Cursor mistakes (prompt + engine)

### B.1 System / heal prompt updates (`OllamaHuntPlanner` + `heal.mjs`)

- Emit **at most 2–3 new scenarios per cycle**; do not fill `scenarioCap` in cycle 1.  
- After `click` on submit/sign-in, prefer `wait{ms:1000..2000}` before `assert_text`.  
- Never invent locators; never re-file the same bug title/theme.  
- Prefer **observed UI copy** over TC expected text when they differ — file one copy-drift bug, then assert actual copy.  
- When strategy mode is `happy` and credentials tokens exist, attempt valid login (+ OTP) before inventing more negatives.  
- When still on login after probing and happy is blocked, say so in rationale (engine advances).  

### B.2 Settle wait (item 4)

After successful `click` (and optionally `type` that looks like submit via following click), executor inserts a short settle (default **800–1200 ms**, cap 2s) before the next action in the same cycle **or** `LiveHuntService` sleeps once after `executeAll` before post-cycle oracle snapshot. Prefer **post-click settle inside executor** so mid-cycle asserts benefit.

### B.3 Toast/alert settle (item 8)

When network snapshot in the **current** cycle includes HTTP 401/403 on login APIs, before `assert_text` for credential errors (or after action batch): poll up to ~2s for `[role=alert], .ant-message, .ant-notification, [class*='toast']` text. Expose last seen alert text into page map / journal note. Does not invent “Invalid credentials” if still empty — then missing-UI bug is legitimate.

### B.4 Strategy auto-advance (item 5)

In `LiveHuntService` / `HuntStopRules`:

- If strategies enabled and mode=`happy` for **≥2 cycles** with URL still matching login/signin **and** (no credentials **or** no successful navigation past login), call `sequencer.advance()` and log `HUNT: strategy advance happy→empty reason=blocked`.  
- Same if grounded rejects / failed asserts streak high without URL change (reuse coverage failure streaks).  

### B.5 Bug fingerprint dedupe (item 6)

`HuntBugDedupe.fingerprint(bug)` = normalize(title) + severity bucket + first URL hint.  
When appending planner/oracle bugs to `allBugs`, skip if fingerprint already present (keep first; increment `dupCount` on first row optional).  
Write `bug-dedupe.json` stats into pack.

### B.6 Login-feature mode (item 7)

Detect login feature when base path / brief / selected TCs mention login/sign-in **or** current hunt URL host path is `/login`.  
`HuntOracle.bugsFromUnexpectedUrl`: **skip** when `loginFeature=true` and URL matches login/signin (still flag unrelated error/404/denied if clearly outside login flow).  

### B.7 End-of-hunt bug recheck (new — user request)

After the cycle loop, before `HuntPackWriter.writePack`:

1. Build compact list of unique bugs (post-dedupe) + short journal + coverage summary.  
2. Call planner once with a **triage** prompt (Cursor or Ollama — same `HuntPlanner` or dedicated `triageBugs` method):  
   Return JSON only: `{ "keep":[{"title","reason"}], "drop":[{"title","reason"}], "merge":[{"into","from[]","reason"}] }`.  
3. Apply mechanically: drop listed titles (fingerprint match); merge collapses into one row with combined repro.  
4. Write `bug-triage.json` (raw + applied) into hunt root for audit.  
5. Soft-fail: if triage JSON invalid, keep deduped list unchanged and note `triage=skipped`.  

Triage must **not** invent new bugs; only classify existing ones.

---

## Phase C — Later (same plan, lower priority tasks)

### C.1 Coverage → planner (item 10)

Extend `HuntCoverageMap.forPrompt()` with **Untested** bullets: selected TC intents / strategy goals not yet evidenced by actions (e.g. “OTP not attempted”, “empty username+password not tried”). Planner must prefer untested over repeats.

### C.2 Stronger JSON repair (item 11)

Harden `HuntPlannerDecision.parse`: strip fences/NULs; extract outermost `{…}`; retry once with “repair to schema” Cursor/Ollama call; validate required keys + action allowlist types before accept. Soft-skip cycle only after repair fails (already partially present — complete + unit tests).

---

## Pack / observability

| Artifact | Purpose |
|----------|---------|
| `login-prelude.txt` | Note hooks used / soft-fail (no password) |
| `bug-dedupe.json` | Fingerprints dropped |
| `bug-triage.json` | End recheck keep/drop/merge |
| `HUNT:` logs | Strategy advances, token resolves (redacted), triage summary |
| actions-log | Token placeholders, not plaintext password |

## Success criteria

1. Axis SIT auto-login succeeds when profile selected and hooks include `data-axis-test-id` (or fields match hook patterns).  
2. Cursor happy-path can `type` `$TARGET_USERNAME` / `$TARGET_PASSWORD` without password appearing in `planner-prompt.txt`.  
3. Re-run similar to `hunt_edc8e5a58ed9`: bug **row count** drops sharply via dedupe+triage; unique keep set ≈ real findings.  
4. Strategy modes progress beyond `happy` when blocked on login.  
5. No “Unexpected redirect to login” oracle on login-feature hunts.

## Risks

- Token typos → empty type; mitigate with normalize aliases and warn in action log.  
- Triage LLM drops a real bug; mitigate with `bug-triage.json` audit + prefer keep on uncertainty (prompt: when unsure, keep).  
- Preferred-hook CSS too broad matches wrong input; keep visibility + password-type filters.
