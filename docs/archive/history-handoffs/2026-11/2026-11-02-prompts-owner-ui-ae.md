# Handoff — prompts Owner UI AE (Slice B Heal stamp) (2026-11-02)

**Status:** GREEN  
**Specialist:** Automation Engine (AE)  
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot`  
**Kickoff:** `docs/history/handoffs/2026-11/2026-11-02-prompts-owner-ui-kickoff.md`  
**Backend handoff:** `docs/history/handoffs/2026-11/2026-11-02-prompts-owner-ui-backend.md`  
**Base tip:** `8d7fe97` (verified HEAD before edits)  
**Tip SHA:** `4685bcf079c39d4519647ce206212f34f335cc84`  
**Backup:** `_backup/prompts-owner-ui-ae-20261102-2257/`  
**No push.**

## What changed

Stamp field **`promptsVersion`** = `PromptCatalog.version()` into existing Heal evidence JSON only (no second evidence format):

1. `delivery.heal.HealProposalWriter#toJson` — heal proposal `{proposalId}.json`
2. `delivery.evidence.FailureReplayWriter#write` — failure replay `meta.json`

Matches Generate stamp already landed by Backend in `GeneratedWorkbookService` → `latest-meta.json`. Classpath pack: `prompts/VERSION` = `2026.11.02`.

## Paths changed

- `src/main/java/delivery/heal/HealProposalWriter.java`
- `src/main/java/delivery/evidence/FailureReplayWriter.java`
- `src/test/java/delivery/heal/HealProposalWriterTest.java` (stamp asserts + `stampsPromptsVersionFromCatalog`)
- `src/test/java/delivery/evidence/FailureReplayWriterTest.java` (stamp asserts + `stampsPromptsVersionFromCatalog`)
- `docs/history/handoffs/2026-11/2026-11-02-prompts-owner-ui-ae.md` (this file)

## Verify

```
mvn -q -pl keel-portal -Dtest=delivery.heal.HealProposalWriterTest,delivery.evidence.FailureReplayWriterTest test
```

**Result:** GREEN (exit 0)  
**Counts:** HealProposalWriterTest 4/0/0/0; FailureReplayWriterTest 3/0/0/0 (tests/failures/errors/skipped)

## Out of scope

Generate stamp (Backend); admin prompts UI (FE); jar refresh / portal PID (Maestro); push; api-keys.local.bat; META-INF/patches/target/portal DB.

## Next

- Maestro: optional jar refresh if Heal evidence stamp must be live on PID; smoke heal proposal / failure meta if needed
- Leader: Slice B prompts Owner UI can treat Heal stamp DoD as closed once tip recorded
