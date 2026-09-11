# Authoring Precision Engine — Keel vs Cursor (Option B)

**Date:** 2026-09-11  
**Status:** Implemented (2026-09-11)  
**Surfaces:** Automate (upload), Execute  
**Runtime:** Cursor sidecar (`tools/cursor-heal/heal.mjs`, `CURSOR_API_KEY`)

## Goal

Let users choose **Keel engine** (current deterministic bind + heal ladder) or **Precision** (one multimodal Cursor call per intent to rank/pick locators from a DOM shortlist, with a single heal/invent escalation). Precision is opt-in, higher cost, intended for flaky or complex UIs.

## Decisions (locked)

| Topic | Choice |
|-------|--------|
| Product mode | **Option B** — Precision primary bind via one multimodal `groundRank` call |
| User toggle | Per job: `authoringEngine: keel \| precision` on Automate + Execute |
| Default | **Keel** — Precision opt-in |
| Happy path (Precision) | **1 multimodal call**: screenshot + shortlist → ranked pick + `candidateId` |
| Shortlist | **A+B hybrid**: `DomCandidateExtractor` shortlist; invent/solve **only** if low confidence or bind rejected |
| Heal (Precision) | **One** `solve` call max per intent retry (reuse existing sidecar mode) |
| Vision on Precision path | **Inside** multimodal Cursor call — skip Ollama UI-TARS Layer 1.5 on Precision intents |
| Fallback | If Cursor unavailable or job cap hit → **auto-fallback to Keel** + warn in UI/logs/draft |
| Cost cap | **50 model calls / job** (server-configurable; not in job UI v1) |
| Execute workbook | **Yes** — same heal patch behavior when Precision fixes locators |
| Provider v1 | **Cursor sidecar only** — abstract interface optional later; Grok is for implementation/testing, not runtime |
| Hunt / Generate | **Out of scope** v1 |

## Non-goals (v1)

- Grok/xAI API at runtime  
- Parallel dual-engine run (Keel + Precision on same intent)  
- User-visible per-TC caps  
- Replacing `StepIntentBinder` scoring for Keel mode  
- Changing Emit/codegen beyond tagging drafts with engine metadata  

---

## Architecture

```text
Job request (authoringEngine)
        │
        ▼
ProvePhase — per intent
        │
        ├─ [keel] ──► StepIntentBinder.bind
        │              ├─ optional VisionProveHook (UI-TARS)
        │              ├─ execute
        │              └─ HealCascade (Ollama pick → Cursor solve → invent)
        │
        └─ [precision] ──► DomCandidateExtractor → shortlist table
                           ├─ PrecisionCallBudget.check (job cap)
                           ├─ CursorHealClient.groundRank (multimodal)  [+1 call]
                           │     → { candidateId, confidence, rationale }
                           ├─ if confidence ≥ threshold → bind candidate → execute
                           ├─ else ONE Cursor solve/invent               [+1 call max]
                           ├─ on provider error / cap → fallback Keel path + tag
                           └─ TcDraft: authoringEngine, precisionCallsUsed, fallbackReason
```

### New sidecar mode: `groundRank`

**Request (stdin JSON):**

```json
{
  "mode": "groundRank",
  "intent": "CLICK Sign In",
  "shortlistTable": "| id | strategy | value | label |\n...",
  "slimHtmlExcerpt": "<body>...</body>",
  "screenshotPath": "/path/to/evidence.png",
  "priorSteps": ["navigate ok", "type username ok"]
}
```

**Response (stdout JSON):**

```json
{
  "candidateId": "c3",
  "confidence": "high|medium|low",
  "rationale": "Sign In button matches intent",
  "ranked": [
    {"candidateId": "c3", "score": 0.92, "reason": "button label Sign In"},
    {"candidateId": "c1", "score": 0.41, "reason": "generic submit"}
  ]
}
```

**Rules:**

- `candidateId` **must** come from shortlist — never invent ids in `groundRank`  
- If model returns invalid id → treat as **low confidence** → allow **one** `solve` call  
- `confidence: low` or missing → **one** `solve` before execute  
- `confidence: high|medium` + valid id → bind and execute without Ollama pick  

### Confidence thresholds (v1)

| confidence | Action |
|------------|--------|
| `high` | Bind + execute |
| `medium` | Bind + execute (log medium) |
| `low` / invalid id / parse fail | One `solve` call, then execute or fail |
| `solve` still fails | Standard prove retry envelope (max 2 outer retries); no extra invent beyond existing caps |

### Job call budget

- Counter: `PrecisionCallBudget` per job (in-memory in `ProvePhase`, persisted on `TcDraft` / job message)  
- Each `groundRank` = 1 call; each `solve` on Precision path = 1 call  
- Default cap: **50** via `delivery.authoring.precision.max-calls-per-job`  
- At cap: log warn, set `fallbackReason=CAP_EXCEEDED`, run **Keel bind** for remaining intents  

### Fallback triggers

| Trigger | Behavior |
|---------|----------|
| `CURSOR_API_KEY` missing / sidecar disabled | Keel + `fallbackReason=PROVIDER_UNAVAILABLE` |
| Sidecar timeout / invalid JSON | Keel + `fallbackReason=PROVIDER_ERROR` |
| Job cap exceeded | Keel + `fallbackReason=CAP_EXCEEDED` |
| User selected Keel | Never call `groundRank` |

UI: show banner on status page when any fallback occurred (read from job message or pack summary).

---

## Data model changes

### `ConversionJobRequest` / job JSON

Add field:

```json
"authoringEngine": "keel"
```

Values: `keel` (default), `precision`.

Stored in automate `request.json` / execute equivalent and passed through `ProvePhase`.

### `TcDraft` metadata (extend)

| Field | Type | Notes |
|-------|------|-------|
| `authoringEngine` | string | `keel` \| `precision` |
| `precisionCallsUsed` | int | Running total for job |
| `precisionFallback` | boolean | True if any intent fell back |
| `fallbackReason` | string | Last or aggregated reason |
| `groundRankTier` | string | On healed steps: `groundRank` \| `solve` \| `keel-fallback` |

Existing `healTier` values remain for Keel path.

---

## UI

### Automate (`upload.html`)

Radio or select:

- **Keel engine** (default) — fast, deterministic DOM grounding  
- **Precision (Cursor)** — slower, uses Cursor API; better on complex UIs  

Help text: requires `CURSOR_API_KEY`; falls back to Keel if unavailable; ~50 API calls per job.

### Execute (`execute.html`)

Same control, same API field.

### API

Extend automate/execute start bodies with `authoringEngine` optional string; normalize to `keel` if blank/unknown.

---

## Configuration (`application.properties`)

```properties
delivery.authoring.precision.enabled=true
delivery.authoring.precision.max-calls-per-job=50
delivery.authoring.precision.confidence-medium-ok=true
```

---

## Testing strategy

| Test | Scope |
|------|-------|
| `GroundRankResponseTest` | Parse sidecar JSON, confidence routing |
| `PrecisionCallBudgetTest` | Cap at 50, reset per job |
| `PrecisionBindServiceTest` | Mock `CursorHealClient` — high conf binds, low conf triggers solve |
| `ProvePhasePrecisionTest` | Integration with fake client — fallback to Keel on disabled |
| Sidecar contract test | Node test or Java test reading golden JSON for `groundRank` prompt |
| UI smoke | upload/execute POST includes `authoringEngine` |

---

## Rollout

1. Ship behind `delivery.authoring.precision.enabled=true` (default true once stable)  
2. Keel remains default in UI  
3. Document in `docs/ops/end-to-end-flow.md`  
4. No migration — old jobs without field = `keel`  

## Success criteria

1. User can start Automate/Execute with Precision selected.  
2. Precision intents use `groundRank` before execute; Keel intents unchanged.  
3. Job with disabled Cursor completes via Keel fallback with visible warning.  
4. Cap at 50 stops Precision calls; remaining intents use Keel.  
5. Execute still patches workbook on Precision heal.  
6. Drafts record engine + call counts for audit.
