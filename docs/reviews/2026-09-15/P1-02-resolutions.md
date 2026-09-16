# P1-02 — Update reuse eligibility (F03)

**Date:** 2026-09-16  
**Contract:** [first-release contract](../../superpowers/specs/2026-09-16-first-release-contract-design.md) — REUSED is not automatically PASSED.

## Behavior

- Reuse only when stored IR is `PASSED` or already `REUSED` **and** has at least one proven step.
- Unchanged `TODO` / `PARTIAL` / empty proof are re-proven. `EmitPhase.toOutcome` maps empty `REUSED` to `TODO`, never PASS.
- Eligible reuse copies proven steps, login steps, evidence path, and verdict, and labels the reason `reused prior PASSED proof; not a fresh browser run`.
- Call-before invalidation is **transitive**: if A changes, B depends on A, and C depends on B, both B and C are re-proven.
- `prove-context.json` records base URL, authoring engine, IR schema, credential username fingerprint (not the password), credentials-bound flag, and Precision enabled/max-calls. **Missing, corrupt, or mismatched context invalidates all reuse.**
- Evidence is copied to `{project}/evidence/{tcId}` and skipped by retention (along with `ir`, `framework`, `versions`). Work-dir job folders may still age out; durable project evidence remains.

## Environment fingerprint

| Tracked (invalidates reuse) | Unsupported (not fingerprinted — do not reuse across) |
|-----------------------------|------------------------------------------------------|
| Base URL | Password rotation with the same username |
| Authoring engine (Keel/Precision) | Local LLM URL/model |
| IR schema | Codegen Ollama naming |
| Credential username fingerprint + bound flag | Final-revise / emit-only flags |
| Precision enabled + max calls per job | Hunt settings |

Original probe: [LaunchAuditProbe.java](LaunchAuditProbe.java) / [probe-results.txt](probe-results.txt). Regression: `ReuseEligibilityTest`, `EmitPhaseMappingTest`, `RetentionSweeperTest.neverTouchesProjectEvidenceOrIr`.
