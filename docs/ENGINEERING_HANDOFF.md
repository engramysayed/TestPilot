# Keel — Engineering handoff

Product overview and quick start live in the root [`README.md`](../README.md). This document points engineers at deeper material already in the tree.

## Core flows

| Topic | Where to look |
|-------|----------------|
| End-to-end conversion | `docs/ops/end-to-end-flow.md` |
| Local Ollama | `docs/ops/ollama-local.md`, `docs/ops/local-live-ollama.md` (if present) |
| Portal restart / Windows | `docs/ops/portal-restart.md`, `docs/ops/windows-vps-setup.md` |
| Architecture notes | `docs/ops/architecture-audit.md` |
| Customer TAF template | `customer-framework-template/README.md` |

## Recent design specs (authoring, import, heal)

| Spec | Path |
|------|------|
| Unified TC import | `docs/superpowers/specs/2026-09-02-unified-tc-import-design.md` |
| Authoring + heal recovery | `docs/superpowers/specs/2026-09-02-authoring-heal-recovery-design.md` |
| Defect fix pass | `docs/superpowers/specs/2026-09-02-defect-fix-authoring-heal-design.md` |
| Generate authoring repair | `docs/superpowers/specs/2026-08-31-generate-authoring-repair-design.md` |

Matching plans/tasks/notes sit beside each spec under `docs/superpowers/plans/`.

## Heal contract (summary)

1. Bind Excel intents to live DOM candidates (no invented locators at bind time).
2. Ollama picks from a shortlist when bind is weak.
3. Cursor invent/solve may write locators **or** a structured recovery plan (`mode: "recovery"`).
4. Recovery steps are validated against the page, executed, evidenced, then the **same** intent is retried.
5. Caps: invent budget per TC; one recovery attempt per intent; allowlisted recovery actions only.

## Quality gate (summary)

Generate / Import / case edit / Automate & Execute Excel load run `GenerateQualityGate` + `GenerateAuthoringRules`:

- Leave-empty / do-not-fill steps require blank TestData lines.
- Vague asserts without a quoted UI message are rejected.
- Angle-bracket placeholders are not typed as literal values.

## Conventions

- Portal UI name: **Keel**. API header: `X-Keel-Requested-With: Keel`.
- Store: `delivery-store/<domain>/<projectId>/` (gitignored).
- Do not commit API keys or `cursor-api-key.local.bat`.
- After Generate/prompt/gate/UI changes: restart portal + hard-refresh.

## Tests (focused)

```bat
mvn -q "-Dtest=RecoveryPlanParserTest,HealCascadeTest,FreeInventHealerTest,RequiredControlFiller*Test,StepIntentBinderTest,GenerateAuthoringRulesTest,ProvePhase*Test" test
```
