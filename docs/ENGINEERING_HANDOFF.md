# Keel — Engineering handoff

Product overview and quick start live in the root [`README.md`](../README.md).  
**Full product feature list:** [`PRODUCT.md`](PRODUCT.md).  
**Public “what we shipped” writeup:** [`CHANGELOG.md`](../CHANGELOG.md) — add a dated section when you publish.

This document points engineers at deeper material already in the tree.

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
| Project TC library + mix upload | `docs/superpowers/specs/2026-09-07-project-tc-library-design.md` |
| Generate authoring preflight review | `docs/superpowers/specs/2026-09-06-authoring-preflight-review-design.md` |
| Bug Hunter (HUNT) | `docs/superpowers/specs/2026-09-09-bug-hunter-design.md` |
| Bug Hunter quality (page map + strategies) | `docs/superpowers/specs/2026-09-10-bug-hunter-quality-design.md` |
| Bug Hunter quality pass 2 (hooks login, tokens, dedupe/triage) | `docs/superpowers/specs/2026-09-10-bug-hunter-quality-pass2-design.md` |
| Precision authoring engine (Keel vs Cursor groundRank) | `docs/superpowers/specs/2026-09-11-authoring-precision-engine-design.md` |

Matching plans/tasks/notes sit beside each spec under `docs/superpowers/plans/`. Review fix checklist: `docs/superpowers/plans/2026-09-11-precision-engine-fixes.md`.

## Bug Hunter (summary)

1. Job kind `HUNT` — UI `/bug-hunter`, worker `HuntWorker`, pack download via jobs API.
2. Live loop: page map (B) + slim fallback (A) → Ollama/Cursor planner → grounded actions → journal + coverage.
3. Phase 2: strategy sequencer (happy→…→invent), `HuntOracle` drafts, stop reasons `STUCK` / `COMPLETE` / finish / cycle cap.
4. **Quality pass 2** — preferred-hook `JobLoginService` login; `HuntSecretResolver` token expansion at execute; post-click settle; strategy auto-advance on login block; `HuntBugDedupe` + `HuntBugTriage`; login-feature oracle fix; `restart_browser` via `HuntBrowserSession`.
5. Phase 3 (two-pass DOM neighborhoods) is **out of scope** until AxisPay proves map+slim still too large.
   - Gaps backlog: `docs/superpowers/plans/2026-09-10-bug-hunter-phase12-gaps.md`
   - Deferred Phase 3: `docs/superpowers/plans/2026-09-10-bug-hunter-phase3-twopass-dom.md`
6. Package: `delivery.hunt.*` under `src/main/java/delivery/hunt/`. Never put plaintext passwords in planner prompts or pack artifacts.

```bat
mvn -q "-Dtest=HuntCoreTest,HuntBugDedupeTest,HuntBugTriageTest,HuntSecretResolverTest,HuntOracleLoginFeatureTest,HuntPlannerPromptCredsTest,JobLoginServiceHooksTest,HuntStopRulesTest,HuntOracleTest" test
```

## Precision authoring engine (summary)

1. `AuthoringEngine` enum: `keel` (default) | `precision` on Automate, Execute, and All-in-one pipeline jobs.
2. **Keel path** — unchanged: `StepIntentBinder` → heal cascade on failure.
3. **Precision path** — `PrecisionBindService`: preflight (HTML, API key, server flag) before budget charge → `CursorHealClient.groundRank` → bind or one `solve` (`FreeInventHealer.parseInventResponse` for `steps[]`) → Keel fallback on cap / errors.
4. **Job-scoped config** — `PrecisionJobConfig` on `ConversionJobRequest` (no `System.setProperty` in workers).
5. **Shared heal budget** — `HealCascade.attachPrecisionBudget` so post-fail Cursor heal shares the per-job cap on Precision jobs.
6. **Telemetry** — per-TC `precisionFallback`, call delta, `groundRank`/`solve` in `healTier` merge; `PRECISION_FALLBACK` banner on status page.
7. **Portal** — radio on `/automate`, `/execute`, `/generate` (All in one); `authoringEngine` on job API; `request.json` under `automate-runs/` / `execute-runs/`.
8. **CLI** — `DeliveryCli --authoring-engine keel|precision`.

Key classes: `delivery.authoring.PrecisionBindService`, `PrecisionCallBudget`, `PrecisionJobConfig`, `delivery.job.AuthoringJobRequestFiles`, `ProvePhase.PrecisionJobTracker`.

```bat
mvn -q "-Dtest=AuthoringEngineTest,PrecisionBindServiceTest,PrecisionJobConfigTest,PrecisionCallBudgetTest,GroundRankResultTest,CursorHealClientGroundRankTest,AuthoringEngineApiTest" test
```

## Heal contract (summary)

1. Bind Excel intents to live DOM candidates (no invented locators at bind time).
2. **Precision jobs** may bind via Cursor `groundRank` / `solve` before heal (see above).
3. Ollama picks from a shortlist when bind is weak.
4. Cursor invent/solve may write locators **or** a structured recovery plan (`mode: "recovery"`).
5. Recovery steps are validated against the page, executed, evidenced, then the **same** intent is retried.
6. Caps: invent budget per TC; one recovery attempt per intent; allowlisted recovery actions only; Precision jobs also share `delivery.authoring.precision.max-calls-per-job`.

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
