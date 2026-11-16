# P1-02 — Update reuse eligibility (F03)

**Date:** 2026-09-16  
**Contract:** [first-release contract](../../superpowers/specs/2026-09-16-first-release-contract-design.md) — REUSED is not automatically PASSED.

## Behavior

- Reuse only when stored IR is `PASSED` or already `REUSED` **and** has at least one proven step.
- Unchanged `TODO` / `PARTIAL` / empty proof are re-proven. `EmitPhase.toOutcome` maps empty `REUSED` to `TODO`, never PASS.
- Eligible reuse copies proven steps, login steps, evidence path, and verdict, and labels the reason `reused prior PASSED proof; not a fresh browser run`.
- Call-before invalidation is **transitive**: if A changes, B depends on A, and C depends on B, both B and C are re-proven.
- `prove-context.json` records base URL, authoring engine, IR schema, an opaque `credentialRevision` (`cred_*`, never a password hash), credentials-bound flag, Precision enabled/max-calls, and local LLM URL/model fingerprint. **Missing, corrupt, or mismatched context invalidates all reuse.** Username or password rotation, and local LLM URL/model changes, all force a fresh prove. A keyed MAC of the credential pair is stored only in the server `credential-binding.json` plus `.keel/credential-mac.key`; neither file is copied into customer ZIPs. An ordinary SHA-256 of the password is not used, because an exposed hash would allow offline guessing.
- Evidence is copied to `{project}/evidence/{tcId}` and skipped by retention (along with `ir`, `framework`, `versions`). Work-dir job folders may still age out; durable project evidence remains.

## Environment fingerprint

| Tracked (invalidates reuse) | Emit-only (not fingerprinted) |
|-----------------------------|-------------------------------|
| Base URL | Codegen Ollama naming |
| Authoring engine (Keel/Precision) | Final-revise / emit-only flags |
| IR schema | Hunt settings |
| Opaque `credentialRevision` (username+password binding) | |
| Local LLM URL + model | |
| Precision enabled + max calls per job | |

Original probe: [LaunchAuditProbe.java](LaunchAuditProbe.java) / [probe-results.txt](probe-results.txt). Regression: `ReuseEligibilityTest`, `EmitPhaseMappingTest`, `RetentionSweeperTest.neverTouchesProjectEvidenceOrIr`.
