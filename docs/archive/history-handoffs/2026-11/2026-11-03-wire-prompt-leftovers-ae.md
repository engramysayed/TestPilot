# Handoff - Slice D AE: wire prompt leftovers (2026-11-03)

**Status:** GREEN
**Specialist:** AE (Maestro assign - wire leftovers onto PromptCatalog)
**Base tip:** `cc041152179eaa2c3519262d6a1f60207191a3d6` (Slice C tip recorded)
**Feat tip:** `3c387e03854f172a9b695b95fcd24c53d933b944`
**Working tip:** docs handoff on top of feat (message: record Slice D AE tip 3c387e0); verify with `git log -1 --oneline`
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot` (Ramy)
**Backup:** `_backup/wire-prompt-leftovers-ae-20261103-024738/`
**Author:** rsayed <rsayed@axisapp.com> - **no push** / no Co-authored-by Cursor
**heal.mjs:** **not touched** (no new heal/* / Node-consumed ids)

---

## Moved (wired / extracted)

| Id | Action | Call site |
|-----|--------|----------|
| `vision/design-compare` | **Wire-only** - PromptCatalog.get trim; dropped inline SYSTEM_PROMPT body | `delivery.vision.DesignCompareGate` |
| `vision/qwen` | **Wire-only** - ASSERT_SYSTEM_PROMPT = PromptCatalog.get(vision/qwen).trim() | `delivery.vision.QwenVisionProvider` |
| `vision/ui-tars-assert-retry` | **Wire-only** - assertRetryNudge() to catalog | `delivery.vision.UiTarsPrompt` |
| `hunt/bug-triage` | **Extract+wire** - new prompts/hunt/bug-triage.md; Candidates/Journal/Coverage via format vars | `delivery.hunt.HuntBugTriage.triagePrompt` |
| `planner/llm-planner` | **Extract+wire** - new prompts/planner/llm-planner.md; maxSteps/maxCycles/plannerMessageJson vars | `llmLayer.LLMPlanner.buildPrompt` |

Boot golden fence updated: PromptsBootInventoryRunnerTest.GOLDEN_IDS 27 -> 29 (adds hunt/bug-triage, planner/llm-planner). Classpath scan resolves both; incomplete-override fail-fast unchanged.

Dynamic one-line user strings left inline (by design): DesignCompare USER_PROMPT, UiTars groundingUser, Qwen assert user line.

---

## File SoT vs prior inline (wire-only note)

Classpath files were already present from Slice extract; file text used as SoT (no wording rewrites). Prior inline Java had extra trailing lines not present in the md files:

| Id | Extra lines that were only in inline Java (now unused) |
|----|-----------------------------------------------------|
| `vision/design-compare` | observation and evidence must be concrete; never use angle-bracket placeholders. |
| `vision/qwen` | blank-when-PASS/FAIL observation/evidence line + confidence 0.7-0.99 guidance line |

Retry nudge file matched UiTars inline (trim only). Maestro may choose a follow-up to append those two extras into the md files if production behavior should keep them - out of scope for this wire-only slice.

---

## Intentionally left

| Item | Reason |
|------|-------|
| static/prompts/* + TcGenerateService.loadPrompt | Public Guide/Generate paste assets - must not route through PromptCatalog |
| FreeInvent / Authoring user scaffolds | Per-call dynamic user scaffolding; systems already on catalog |
| DomDiffAssertSuggestor / NlLocatorCache | Tiny non-AI strings |
| Dynamic user lines (UiTars groundingUser, DesignCompare one-line user, Qwen assert user) | OK inline |
| tools/cursor-heal/heal.mjs | Already catalog-aligned; no new shared Node ids |

---

## Gate

```text
mvn -B -pl keel-portal -am "-Dsurefire.failIfNoSpecifiedTests=false" `
  "-Dtest=PromptCatalogTest,UiTarsPromptParityTest,DesignCompareGate*,Qwen*,HuntBugTriage*,PromptsBootInventory*,LLMPlanner*" test
-> Tests run: 23, Failures: 0, Errors: 0, Skipped: 1  BUILD SUCCESS
(Africa/Cairo 2026-11-03 ~02:49)
```

Skipped: QwenBothLiveSmokeTest.qwenGroundsLoginAndAssertsWithHardenedPrompt (live smoke).
DesignCompareGate* / LLMPlanner* matched no dedicated unit tests (failIfNoSpecifiedTests=false).
Manual grep: no large Return JSON only /inline SYSTEM bodies left in DesignCompareGate / Qwen assert / UiTars assertRetry.

---

## Constraints honored

- Extract/wire only - no prompt prose rewrites; file SoT for existing ids
- No edit to application.properties, api-keys.local.bat; _backup/_scratch not staged
- No portal restart; no push
- Serialize Maven (one reactor)

## Next

- Maestro: review SoT deltas table; optional follow-up to merge missing trailing lines into design-compare.md / qwen.md if desired
- Portal jar refresh only when Maestro schedules post-GREEN
