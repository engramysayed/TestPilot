# QA gate: AE Http reader (hunt + knowledge pack reader)

- **Date:** 2026-11-02 ~18:00 Africa/Cairo (UTC+2)
- **Slice:** AE Http reader gate (KnowledgePack + Hunt + HttpKnowledgePackReader)
- **Live tree:** `D:\Work\projects\Keel\keel\TestPilot` (machine Ramy, `96a3c81a-96a9-490d-a835-f6bf263f986b`)
- **Tip at gate start / successful surefire:** `eeb079e` (`eeb079e8b6fc38958852d7cb4aa8885e542b2322`) — matches Maestro tip `eeb079e` (not `0f4f1af`; `0f4f1af` is parent: HTTP-swap wire)
- **Tip after handoff write (drift):** `8c486cc` (`8c486cc785f4ba247ff0a9a3075dface5f536b73`) — concurrent `feat(portal): visual baseline…` landed while/after this gate; ahead 5 of `keel/main`
- **Dirty:** yes — many modified tracked files + untracked WIP. No push/commit by this gate.
- **Command:**
  ```
  mvn -B -pl keel-portal -am "-Dtest=KnowledgePackGuidanceTest,HuntKnowledgePackBriefTest,HttpKnowledgePackReaderTest,HuntApiTest,KnowledgePackApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  ```
- **Portal/engine:** not started. Sonic ignored. No push/commit.
- **VERDICT:** **GREEN**

## Surefire summary

| Metric | Value |
|--------|-------|
| Exit code | 0 |
| BUILD | SUCCESS |
| Tests run | 18 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Elapsed (surefire suite) | ~15.93 s |
| Total Maven time (successful run) | ~28.95 s |
| Finished at | 2026-11-02 18:00:26 Africa/Cairo |

### Classes (18 methods)

- `delivery.knowledge.KnowledgePackGuidanceTest` (3)
- `delivery.hunt.HuntKnowledgePackBriefTest` (3)
- `delivery.knowledge.HttpKnowledgePackReaderTest` (4)
- `delivery.portal.api.HuntApiTest` (5)
- `delivery.portal.api.KnowledgePackApiTest` (3)

## Notes / how to verify

1. First raw run failed **testCompile**: truncated `JobRecord$JobKind.class` (offset 0) in `keel-portal/target/classes`.
2. `mvn … clean test` blocked: `keel-contract-1.0-SNAPSHOT.jar` locked by another process.
3. Removed truncated `delivery/portal/model` classes under `keel-portal/target/classes`; main recompiled cleanly.
4. Next run failed **testCompile** on untracked WIP `src/test/java/delivery/codegen/CodegenW1EmitTest.java` (`TcStatus.PASS` missing). File temporarily parked for the gate run, then **restored** to the same path (untracked).
5. After (3)+(4), the exact Maestro command: **BUILD SUCCESS / 18/0/0**.
6. Handoff only; tree left dirty as found (WIP test restored).

## What’s blocked / next

- None for this gate. Dirty-tree WIP (`CodegenW1EmitTest`) still breaks a full `testCompile` if left as-is — codegen owner should fix or exclude before a clean-tree gate.
