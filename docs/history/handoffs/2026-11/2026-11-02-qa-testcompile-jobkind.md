# QA: testCompile JobKind / JobRecord (2026-11-02)

## Verdict
**Not a source/API break.** `JobRecord.JobKind` exists on live tip; RED was **stale/truncated class files** under concurrent Maven.

## Facts
- **HEAD:** `eeb079e8b6fc38958852d7cb4aa8885e542b2322` (`eeb079e` docs(hunt) KnowledgePack HTTP-swap tip; parent `0f4f1af`)
- **Branch:** `main` ahead of `keel/main` by 4; dirty WIP tree (Phase 4 visual-diff / plain-cases / codegen) — **`JobRecord.java` clean vs HEAD**
- **Source:** `src/main/java/delivery/portal/model/JobRecord.java` line 12:
  `public enum JobKind { CONVERT, EXECUTE, GENERATE_BATCH, GENERATE_COMPARE, HUNT }`
  (present since `793a7a64`, 2026-09-10; not touched by Phase 4)
- **Failing tests (consumers only):** `CreditsGateLockTest`, `EngineTestJobs`, `ContractParityTest` — all use `JobRecord.JobKind` correctly

## Root cause
Concurrent `mvn` on the same live tree left:
`keel-portal/target/classes/delivery/portal/model/JobRecord$JobKind.class`
as **`class file truncated at offset 0`**.
javac then reported `cannot access delivery.portal.model.JobRecord.JobKind` and cascade `cannot find symbol: variable JobKind` across tests.

Observed failure window: **2026-11-02 17:56:31 Africa/Cairo (UTC+2)**.
At diagnosis time another QA Maven was still running KnowledgePack tests (`-pl keel-portal -am … test`) while portal jar also live on :8080.

## NOT the culprit
- Phase 4 plain-cases / visual-diff WIP (no JobRecord edits)
- Missing enum / half-applied rename / package move
- Tip commits `0f4f1af` / `eeb079e` (KnowledgePack) — unrelated to JobKind

## Fix / verify (ops, not product)
1. Serialize Maven on this tree (do not overlap portal `-Dmaven.test.skip=true` rebuild with full `test`/`test-compile`).
2. After other Maven exits: delete bad classes or `mvn -B -pl keel-portal -am clean test-compile` (or at least remove `keel-portal/target/classes/delivery/portal/model/JobRecord*.class` then recompile).
3. Confirm no `bad class file` / `truncated at offset 0`.

## FIXED?
**No source change applied** (nothing to invent). Recommend clean target + single-writer test-compile when the concurrent gate finishes.

## One-liner for Maestro
JobKind RED = truncated `JobRecord$JobKind.class` from concurrent Maven, not missing enum — clean portal target and re-testCompile once the other mvn exits.

## VERIFY (this run)
After concurrent KnowledgePack Maven exited:
- `JobRecord$JobKind.class` magic `CA FE BA BE`, javap OK (CONVERT…HUNT)
- `mvn -B -pl keel-portal -am test-compile -DskipTests` → **BUILD SUCCESS** at 2026-11-02 18:03:19 Africa/Cairo (UTC+2) (keel-portal 708 main + 625 test sources)
