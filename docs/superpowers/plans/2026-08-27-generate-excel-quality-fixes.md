# Generate Excel Quality Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or execute inline task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Generate from producing Excel rows that fail Execute (ambiguous Phone field, empty-email cases that type values, vague assert steps).

**Architecture:** Tighten the LLM system prompt (JSON + CSV) with login/empty/assert rules, then enforce the same rules in `GenerateQualityGate` via a new `GenerateAuthoringRules` helper. `TcGenerateService` already retries once on gate failure — semantic errors trigger the same retry path. No Execute/heal changes.

**Tech Stack:** Java 21, TestNG, existing `TcGenerateService` + `GenerateQualityGate`, prompt files under `static/prompts/`.

## Global Constraints

- Do not commit unless the user asks.
- Prefer extending existing gate/retry flow over new services.
- Rules must be testable without Ollama (unit tests on sample `ManualTestCase` rows).
- Keep Facebook fix generic where possible; use `baseUrl` hint for site-specific combined-field label.

---

## File map

| File | Responsibility |
|------|----------------|
| `GenerateAuthoringRules.java` | Semantic checks: empty-field consistency, phone/email label, vague asserts |
| `GenerateQualityGate.java` | Call semantic rules; accept optional `baseUrl` |
| `TcGenerateService.java` | Pass `project.getBaseUrl()` into gate |
| `keel-tc-generate-from-stories-to-json.txt` | Authoring rules + examples |
| `keel-tc-generate-from-stories-to-csv.txt` | Same rules (UI copy prompt) |
| `GenerateAuthoringRulesTest.java` | Regression cases from `exec_fe79414458b3` |
| `GenerateQualityGateTest.java` | Wire baseUrl through gate |
| `GenerateJsonPromptResourceTest.java` | Assert new prompt phrases exist |

---

### Task 1: Semantic authoring rules (TDD)

**Files:**
- Create: `src/main/java/delivery/excel/GenerateAuthoringRules.java`
- Create: `src/test/java/delivery/excel/GenerateAuthoringRulesTest.java`

**Rules to implement:**

1. **Vague assert** — reject steps containing phrases like `clear validation/error state`, `validation/error state is shown` without quoted message.
2. **Phone field label** — reject `Enter in the Phone field` (use `Email or phone field`). On `baseUrl` containing `facebook.com`, also reject standalone `Enter in the Email field` for the identifier step.
3. **Empty-field consistency** — if title or expected mentions `empty email|phone|password`, matching Enter step must say `Leave … empty` and aligned `testData` line must be blank.

**Regression fixtures:** TC_02, TC_03, TC_04 shapes from run `exec_fe79414458b3`.

Run: `mvn -q -Dtest=GenerateAuthoringRulesTest test`

---

### Task 2: Wire gate + service

**Files:**
- Modify: `src/main/java/delivery/excel/GenerateQualityGate.java`
- Modify: `src/main/java/delivery/portal/service/TcGenerateService.java`
- Modify: `src/test/java/delivery/excel/GenerateQualityGateTest.java`
- Modify: `src/test/java/delivery/portal/service/TcGenerateServiceQualityRetryTest.java`

- Add `validate(cases, baseUrl)` overload.
- `generateStory` passes `project.getBaseUrl()`.
- Extend retry user message with hints for semantic error prefixes.

Run: `mvn -q -Dtest=GenerateQualityGateTest,TcGenerateServiceQualityRetryTest test`

---

### Task 3: Prompt updates (JSON + CSV)

**Files:**
- Modify: `src/main/resources/static/prompts/keel-tc-generate-from-stories-to-json.txt`
- Modify: `src/main/resources/static/prompts/keel-tc-generate-from-stories-to-csv.txt`
- Create: `src/test/java/delivery/portal/web/GenerateJsonPromptResourceTest.java`
- Modify: `src/test/java/delivery/portal/web/GeneratePromptResourceTest.java`

Add **LOGIN & ASSERT AUTHORING** section:
- Combined identifier → `Email or phone field`
- Empty fields → `Leave the … field empty` + blank testData line
- Assert → quote exact UI message; forbid vague “clear validation/error state”
- Add second tiny example: empty email login case

Run: `mvn -q -Dtest=GenerateJsonPromptResourceTest,GeneratePromptResourceTest test`

---

### Task 4: Verification

Run: `mvn -q -Dtest=GenerateAuthoringRulesTest,GenerateQualityGateTest,TcGenerateServiceQualityRetryTest,GenerateJsonPromptResourceTest,GeneratePromptResourceTest test`

User action after deploy: **Re-run Generate** on Facebook project to replace `latest.xlsx`.
