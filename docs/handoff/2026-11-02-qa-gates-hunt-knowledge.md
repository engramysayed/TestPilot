# QA gate: hunt-pack-guided (hunt-knowledge)

- **Date:** 2026-11-02 (Africa/Cairo, UTC+2)
- **Slice:** hunt-pack-guided
- **Live tree:** `D:\Work\projects\Keel\keel\TestPilot` (machine Ramy)
- **Tip SHA:** `b422366` (`b422366de94529e578e10da35eda975283dec418`) — matches AE claim
- **Command (AE exact classes):**
  ```
  mvn -B -pl keel-portal -am "-Dtest=KnowledgePackGuidanceTest,HuntKnowledgePackBriefTest,HuntApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  ```
- Recipe #3 fallback **not needed** (all three AE classes present under `src/test/java`).
- **Portal/engine:** not started. Sonic/Appium ignored. No push/commit.
- **VERDICT:** **GREEN**

## Surefire summary

| Metric | Value |
|--------|-------|
| Exit code | 0 |
| BUILD | SUCCESS |
| Tests run | 11 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Elapsed (surefire suite) | ~11.32 s |
| Total Maven time | ~15.2 s |
| Finished at | 2026-11-02 17:29:32 Africa/Cairo |

### Classes

- `delivery.knowledge.KnowledgePackGuidanceTest`
- `delivery.hunt.HuntKnowledgePackBriefTest`
- `delivery.portal.api.HuntApiTest`

Matches AE claim: tip `b422366`, gate **11/0/0**.
