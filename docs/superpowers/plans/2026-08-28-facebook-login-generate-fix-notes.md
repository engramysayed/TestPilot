# Facebook Login Generate Fix — Investigation Notes

## Repro run 2026-08-28 (job genb_0c264bf9bea3)

- **jobId:** `genb_0c264bf9bea3`
- **Final status:** FAILED
- **Progress:** 1 / 1 story
- **Passed TCs:** 0
- **Failed stories:** 1
- **Final message (UI):** `No test cases were generated from bulk stories`
- **Message during run (inferred):** Per-story error overwritten — likely `Failed US_…: QUALITY_GATE: …`
- **Likely hypothesis:** **H1 (quality gate)** + **H2 (prompt contradiction)** + **H5 (UX hides error)**
- **Evidence:**
  - Log stack trace ends at `GenerateBatchJobRunner.run:65` (empty `allCases` after story exception)
  - Recent quality gate rules reject Facebook-style `Phone field`, `Email field`, vague asserts, empty-field drift
  - JSON prompt line 43 still says `Enter in the Email field` while LOGIN section forbids it for combined boxes
  - User story scope uses **Email / Mobile number field** → must map to `Email or phone field` in steps

## Classification (T002)

| Hypothesis | Verdict |
|------------|---------|
| H1 Quality gate | **Primary** — model output likely fails gate after retry |
| H2 Prompt contradiction | **Contributing** — trains wrong field labels |
| H3 Ollama timeout | Unlikely (job completed in ~3 min, not hung) |
| H4 Parse error | Possible but less likely than gate |
| H5 UX-only | **Confirmed** — generic message hides root cause |

## Live verification

(Pending T015 — run after prompt + UX fixes)
