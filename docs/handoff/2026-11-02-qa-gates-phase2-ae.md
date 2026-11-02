# QA gates — Phase 2 AE writers (2026-11-02)

**Status:** GREEN  
**Bot:** QA (executor under Maestro)  
**Tip:** `e9ee274` (AE wire `8e3af32`, Backend helpers `e96f648`)  
**Time (Africa/Cairo):** 2026-11-02 19:14 +02  

## Scope

Re-gate AE Phase 2 writers only: `FailureReplayWriterTest`, `HealProposalWriterTest`, `FlakyTagStoreTest`.

Aligned to: `docs/handoff/2026-11-02-phase2-ae.md`, `docs/handoff/2026-11-02-phase2-api-contract.md`

## Command

Folded into the same serialized reactor as Backend (no second `mvn`):

```bat
cd /d D:\Work\projects\Keel\keel\TestPilot
mvn -B -pl keel-portal -am "-Dtest=FailureEvidenceDiskStoreTest,HealProposalDiskStoreTest,FlakyTagDiskStoreTest,FailureReplayWriterTest,HealProposalWriterTest,FlakyTagStoreTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

AE-only equivalent (not re-run; same classes already GREEN in that pass):

```bat
mvn -B -pl keel-portal -am "-Dtest=FailureReplayWriterTest,HealProposalWriterTest,FlakyTagStoreTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Surefire summary (AE classes)

| Class | FQCN | Tests | Failures | Errors | Skipped |
|-------|------|------:|---------:|-------:|--------:|
| FailureReplayWriterTest | delivery.evidence.FailureReplayWriterTest | 2 | 0 | 0 | 0 |
| HealProposalWriterTest | delivery.heal.HealProposalWriterTest | 3 | 0 | 0 | 0 |
| FlakyTagStoreTest | delivery.job.FlakyTagStoreTest | 2 | 0 | 0 | 0 |
| **AE total** | | **7** | **0** | **0** | **0** |

Full suite including Backend disk stores: 14/0 (see Backend QA handoff).

## Counts vs claims

| Claim source | Claim | Observed | Match |
|--------------|-------|----------|-------|
| AE handoff / Maestro GREEN claim | 7/0 at tip e9ee274 / wire 8e3af32 | 7/0 | YES |

## Verdict

**GREEN** — AE Phase 2 writer gate 7/0 at tip `e9ee274` (wire `8e3af32`).

## Not done by QA

- No push / commit  
- No portal rebuild or `:8080` restart  
- No second Maven (folded into Backend pass)

## Related

- Backend gate note: `docs/handoff/2026-11-02-qa-gates-phase2-backend.md`