# Visual assertions + heal vision hints — design (Phase 1)

**Date:** 2026-08-18  
**Status:** Approved  
**Depends on:** `docs/superpowers/specs/2026-08-17-vision-grounding-design.md` (Approved, implemented)  
**Scope:** Opt-in VLM **visual assertions** after a case succeeds, plus compact **VLM attempt history** into Ollama and Cursor heal. Not a Midscene rewrite. Not Figma. Not dual-VLM voting.

## Problem

Vision grounding (Layer 1.5 / heal 2.5) can find a widget and map it to a locator. It does **not** answer “does this page look like the expected outcome?” `ExpectedResult` already becomes DOM `ASSERT_VISIBLE` / body-text checks. Overloading that column would mix two different honesty gates.

Separately: Ollama and Cursor heal already see **prior proven Selenium steps** (action + locator + value) and the **current** screenshot. They do **not** see what the VLM already tried this intent (bbox, grounded candidateId, hit/miss). They re-guess as if vision never ran.

## Goals

- Optional Excel column `VisualAssertion` — empty or missing = today’s behavior.
- After a TC’s intents **succeed**, run one structured VLM check: PASS / FAIL / UNCERTAIN.
- Persist evidence (JSON + the screenshot used).
- Feed compact **vision attempt traces** (text only, no PNG bytes) into Ollama, Cursor, and invent so they know what grounding already did.
- Keep Qwen / UI-TARS behind the existing provider switch. One VLM per call, not an ensemble.

## Non-goals (Phase 1)

- Figma / `referenceImage` (always `null`).
- Dual-VLM vote (Qwen + UI-TARS + DOM on every step).
- iframe walk-in (grounded node is `<iframe>` → switch + remap + `elementFromPoint` inside). Track as follow-up.
- Canvas / WebGL **action** via `moveByOffset` or POM locators on painted pixels.
- User-story generation, Jira, automation-readiness scoring.
- Per-step VisualAssertion cells (Excel remains one row per TC).
- Changing Layer 1.5 fusion into a second VLM call.

## Answer already locked: heal history today vs Phase 1

| Channel | Today | Phase 1 |
|---|---|---|
| Ollama heal user text | Last 12 proven steps (`CLICK css #login = …`). Screenshot **image** of current page. | Same + `## Vision attempts this intent` (compact lines). |
| Cursor sidecar JSON | `priorSteps` + `screenshotPath`. | Same + `visionAttempts: string[]`. |
| Invent (Cursor/AgentRouter) | Sanitized `priorSteps` (cap 12 × 240 chars). | Same + the same `visionAttempts` lines in the history block. |
| PNG bytes in prompt text | Never. | Never. Current screenshot stays a separate attachment. |

**Do send:** provider, model, bbox, confidence, grounded candidateId or `none`, whether Selenium used it, one-token outcome (`grounded` / `miss` / `filtered` / `unavailable`).  
**Do not send:** full candidate tables, slim HTML dumps, base64 images, raw VLM JSON blobs.

Cap: **8** vision-attempt lines per intent (matches sweep cap). Each line ≤ **200** characters.

Example line:

```text
VISION qwen/qwen2.5vl:3b bbox=412,88,96,40 conf=0.82 grounded=cV2 used=no outcome=filtered
```

If vision never ran this intent, omit the section (do not send `(none)` noise unless invent already requires a history placeholder — then existing `(none)` for prior steps stays; vision block is absent).

---

## Locked decisions

| Topic | Decision |
|---|---|
| Excel column | Optional `VisualAssertion`. Aliases after header normalize: `VISUALASSERTION`, `VISUAL ASSERTION`, `VISUAL_ASSERTION`. Missing header = empty. |
| Required columns | Unchanged: `TCID`, `TITLE`, `STEPS`, `EXPECTEDRESULT`. |
| `ExpectedResult` | Unchanged. Still Layer 1 `ASSERT_VISIBLE` / body text. Never parsed as a VLM assertion. |
| When visual assert runs | **Once per TC**, after **all** body intents of that TC have proven successfully. If any intent failed, **skip** visual assert (do not judge a broken page). |
| Screenshot | Capture **one** viewport PNG of the live session at assert time. Do not reuse a stale heal PNG from an earlier failed attempt. |
| VLM output | Strict JSON only. Schema below. |
| `UNCERTAIN` | Treat as **visual fail**. TC is not fully proven. No locator invent. No bug invent. Case stays honest (failed visual / TODO — see outcomes). |
| `referenceImage` | Always `null` in Phase 1. Prompt must not ask the model to compare to a missing image. |
| Provider | Same `delivery.vision.provider` / `delivery.vision.model` as grounding. New method on the provider, not a second HTTP stack. |
| Grounding flag | Assertions do **not** require `delivery.vision.grounding.enabled`. They require their own flag + a working provider. |
| Canvas / WebGL | Visual **assert** is allowed (pixels are visible). Visual **action** remains miss → heal / TODO. No POM locator on canvas children. |
| Offset click | Still forbidden in generated POM. |
| Site hardcoding | Forbidden. |

---

## Architecture

```
Excel row
  → existing bind / Layer 1.5 / heal / invent (unchanged authority)
  → each intent: record VisionAttempt traces (Layer 1.5 + heal 2.5)
  → Ollama / Cursor / invent receive priorSteps + visionAttempts
  → TC intents all proven?
       → assertions flag on AND VisualAssertion non-blank?
            → screenshot now
            → VisionGroundingProvider.assertVisual(png, assertionText, referenceImage=null)
            → PASS → keep TC proven; write evidence
            → FAIL or UNCERTAIN → TC not proven; write evidence; no invent
            → provider unavailable → UNCERTAIN path (fail visual, do not fake PASS)
```

Vision remains a **perception** layer. Selenium still executes locators. Visual assert never clicks.

---

## Feature flags

| Key | Default | Meaning |
|---|---|---|
| `delivery.vision.assertions.enabled` | `false` | Master for Phase 1 visual asserts. Off = ignore the Excel column (still parse it for hash). |
| `delivery.vision.heal-hints.enabled` | `true` | When grounding ran, append `visionAttempts` to Ollama/Cursor/invent. Independent of assertions. |
| `delivery.vision.provider` | (existing) | `qwen` \| `uitars` |
| `delivery.vision.model` | (existing) | Ollama tag |

v1: if assertions flag is false, never call `assertVisual`. If heal-hints flag is false, heal payloads stay as today.

---

## Excel and `ManualTestCase`

Add optional field `visualAssertion` (String, default `""`).

`contentHash()` **must** include normalized `visualAssertion` so UPDATE reuse invalidates when only the visual check changed:

```text
normalize(steps) + "|" + normalize(expectedResult) + "|" + normalize(visualAssertion)
```

Empty visual cell is equivalent to no column.

Do not generate an `IntentKind` for visual assertion. It is not a bindable step.

---

## Provider interface

Extend `VisionGroundingProvider` (same Qwen / UI-TARS implementations):

```text
VisionAssertionResult assertVisual(
    byte[] screenshotPng,
    String assertionText,
    String referenceImagePathOrNull   // Phase 1: always null
)
```

Do **not** overload `analyze(...)`. Grounding JSON (`candidates` + bbox) and assertion JSON (`status`) must not share a parser.

`VisionAssertionResult`:

| Field | Type | Rule |
|---|---|---|
| `status` | `PASS` \| `FAIL` \| `UNCERTAIN` | Required. Unknown / missing → `UNCERTAIN`. |
| `confidence` | `double` | 0..1. Missing / NaN / negative → `0.5`. Do **not** treat schema-example `0.0` as a real score: prompt example must use `0.9` (same lesson as grounding). |
| `observation` | `String` | What is visible. Trim; cap 500 chars in evidence. |
| `evidence` | `String` | Why that status. Trim; cap 500 chars. |
| `error` | `String` or null | Transport / parse failure. If set, status is `UNCERTAIN`. |

Fail closed: HTTP/parse/timeout → `UNCERTAIN` + log. Never throw out of prove uncaught. Never PASS on empty model output.

### Assertion JSON (model must return only this)

```json
{
  "status": "PASS",
  "confidence": 0.9,
  "observation": "Welcome heading and enabled Submit button are visible.",
  "evidence": "Top of viewport shows Welcome; primary button is not greyed out."
}
```

Prompt rules:

- Pass the Excel `VisualAssertion` text and the PNG.
- Instruct: judge **this screenshot only**; no Figma; no inventing locators; no CSS selectors in the verdict.
- `status` must be one of the three enums.
- Echoed example confidence in the prompt is **0.9**, never 0.0.

---

## Prove outcomes (honesty)

After a successful intent loop:

| Visual result | TC outcome | Generated Java | Heal / invent |
|---|---|---|---|
| Flag off or cell blank | Unchanged | Unchanged | Unchanged |
| `PASS` | Proven (healTier unchanged) | Unchanged | N/A |
| `FAIL` | Not proven. Reason prefix `VISUAL_ASSERT:FAIL`. | Do not emit a fake passing assert. Optional later: a commented TODO in codegen — **Phase 1: evidence + draft status only**, no new POM method. | Do not invent locators to “fix” a visual fail. |
| `UNCERTAIN` | Same as FAIL with reason `VISUAL_ASSERT:UNCERTAIN`. | Same | Same |
| Provider down | `UNCERTAIN` path | Same | Same |

`ExpectedResult` DOM asserts still run as intents **before** this gate. A TC can pass DOM expected-result and still fail visual assert.

Logging (same discipline as grounding):

```text
VISION_ASSERT: tc=… status=PASS conf=0.91 provider=qwen model=qwen2.5vl:3b
```

---

## Evidence

Write under the existing TC evidence directory (do not create a parallel tree):

- `visual-assert.png` — the PNG sent to the VLM (copy of the capture).
- `visual-assert.json` — `{ tcId, assertionText, provider, model, status, confidence, observation, evidence, timestamp, screenshotFile }`.

Do not embed PNG bytes in the JSON.

---

## Heal / Cursor / Ollama vision hints

### Collection

`HealCascade` / `VisionProveHook` already log `VISION:` / `GROUNDING:`. Phase 1 adds an in-memory `List<VisionAttempt>` **per intent attempt**, discarded after the intent succeeds or the TC moves on.

Each `VisionAttempt`:

- `provider`, `model`
- `bbox` (x,y,w,h) or omitted if unavailable
- `confidence`
- `groundedCandidateId` or `none`
- `used` (Selenium executed that candidate)
- `outcome`: `grounded` \| `miss` \| `filtered` \| `unavailable`

Format to a single line via one helper (`VisionAttempt.formatLine`) used by Java heal **and** the Cursor JSON array (identical strings).

### Injection

- `AuthoringService.healIntentWithOllama`: new section `## Vision attempts this intent` after `## Already completed in this TC`.
- `CursorHealClient` pick / invent / revise payloads: `visionAttempts` array. Sidecar prompt must include those lines in the user message. If the sidecar ignores unknown fields today, **update it** so Auto sees the hints.
- `FreeInventHealer`: append the same lines to the history block (still sanitize: cap 8 extra lines, 200 chars).

Hints are **context**, not orders. Models still must pick from the shortlist / not invent ids unless invent mode.

`delivery.vision.heal-hints.enabled=false` restores today’s payloads.

---

## Fusion (document only — already shipped)

When Layer 1 is **weak**, Layer 1.5 grounds bbox → `elementFromPoint` → `DomCandidate` (`cV*` or match). Heal 2.5 does the same widen. That **is** fusion: DOM table + one VLM pass.

Phase 1 does **not** add:

- Qwen and UI-TARS on the same screenshot
- Score averaging across VLMs
- Running vision when Layer 1 has a clear winner

No new fusion code except logging those attempts into `visionAttempts`.

---

## iframe / canvas (follow-up, not this implementation)

**iframe:** snapshot HTML cannot see cross-origin inner DOM; `switchTo().frame(i)` already works for in-DOM search. If grounding hits an `<iframe>` element, a later spec will: switch in, remap screenshot coords into the frame, `elementFromPoint` inside, switch out. **Do not mix into Phase 1 tasks.**

**canvas / WebGL:** no reliable child locator. Visual assertion on the canvas **pixels** is in Phase 1 if the Excel text describes what is painted. Action against canvas stays miss / TODO. No `moveByOffset` in generated tests.

---

## Error handling

- Excel: unknown extra columns ignored; missing `VisualAssertion` is not an error.
- VLM assertion: catch transport and parse failures at the prove boundary; log ERROR with stack; status `UNCERTAIN`. Do not swallow without a status.
- Heal hints: formatting must not throw; skip malformed attempts.
- No site-specific branches.

---

## Testing (Phase 1 plan will expand)

- Excel: sheet without the column still reads; with column populates `visualAssertion`; `contentHash` changes when only that cell changes.
- Parser: valid PASS/FAIL/UNCERTAIN; missing status → UNCERTAIN; example `confidence: 0.0` in prompt must not appear; missing confidence → 0.5.
- Prove: blank cell + flag on → no VLM call; flag off + filled cell → no VLM call; FAIL/UNCERTAIN → TC not proven and no invent; PASS → proven.
- Heal payload unit tests: `visionAttempts` present when grounding ran; absent when hints flag off; no base64 in the JSON/text.
- Cursor sidecar: request with `visionAttempts` appears in the model user text.

Live smoke is optional and flag-gated (same pattern as `VisionGroundingLiveSmokeTest`).

---

## Out of scope reminders

- Phase 2: Figma `referenceImage`, per-step visual cells, iframe walk-in, gated offset for canvas (still not POM).
- GPT north-star extras: auto user stories, Jira, “automation readiness” scores.

---

## Self-review

- No TBD placeholders.
- `UNCERTAIN` has one meaning: visual fail, no invent.
- Assertions and grounding share provider config but not the JSON schema.
- Heal hints answer the product question: **yes, send compact VLM choice history to Ollama and Cursor; we do not do that today.**
