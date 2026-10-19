# Keel — Engineering handoff

Product overview and quick start live in the root [`README.md`](../README.md).  
**Full product feature list:** [`PRODUCT.md`](PRODUCT.md).  
**Public “what we shipped” writeup:** [`CHANGELOG.md`](../CHANGELOG.md) — add a dated section when you publish.

This document points engineers at deeper material already in the tree.

## Core flows

| Topic | Where to look |
|-------|----------------|
| Record → framework (skip the browser prove pass) | `docs/superpowers/plans/2026-10-15-record-to-framework.md` |
| 2026-10-15 portal session (Record, alerts, layout, unit prices, owner-only admin, editable price copy, project tabs, Execute punctuation, compact credit bar, named-label clicks, overview restyle, screenshot occ folders, expected-per-step, page locator catalog, history copy, attention colors) | `docs/handoff/2026-10-15-portal-session.md` |
| Candidate scorer + DOM shortlist (bind before Ollama/vision; proof-gated catalog; fixture metrics) | spec `docs/superpowers/specs/2026-10-16-candidate-scorer-design.md`, plan `docs/superpowers/plans/2026-10-16-candidate-scorer.md` |
| OutcomeKind confirm-line classify before score (enabled/disabled execute types; nameless skip-echo; toast tokens; OTP/Username/Logout without field/button) | `src/main/java/delivery/authoring/OutcomeKind.java`, `EnabledDisabledAssertBindTest` |
| End-to-end conversion | `docs/ops/end-to-end-flow.md` |
| Local Ollama | `docs/ops/ollama-local.md`, `docs/ops/local-live-ollama.md` (if present) |
| Heal/LLM API keys (env wins then Settings publish props then DEV LocalApiKeyLoader; clear drops System props; env wins for URL/model props; optional Ollama Bearer) | `delivery.heal.ApiKeySource`, `LocalLlmClient`, Backend `/admin/llm-settings` |
| Live Ollama models on Generate (chat tags + disk cache; Account vision radios unchanged) | `docs/handoff/2026-10-18-live-ollama-generate-models.md`, spec `docs/superpowers/specs/2026-10-18-live-ollama-generate-models-design.md` |
| Add library TC + pre-run review (Cursor or Keel; Accept saves library then starts reviewed ids; review not charged) | `docs/handoff/2026-10-18-library-add-and-prerun-review.md`, spec `docs/superpowers/specs/2026-10-18-library-add-and-prerun-review-design.md`, plan `docs/superpowers/plans/2026-10-18-library-add-and-prerun-review.md` |
| Nested preferred hook + leftover Expected quoting (inner unique hook on named bind; review leftover quotes; Execute unique live heading wait; OutcomeKind unchanged) | `docs/handoff/2026-10-19-nested-hook-and-quote-heading.md`, spec `docs/superpowers/specs/2026-10-19-nested-hook-and-quote-heading-design.md`, plan `docs/superpowers/plans/2026-10-19-nested-hook-and-quote-heading.md` |
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
- Heal API keys: env wins, then System props published by LLM Settings, then DEV-only LocalApiKeyLoader (api-keys.local.*). Clear flags call clearProperty when env unset (no restart). applyToRuntime skips DeliveryPortalProperties URL/model setters when matching env is set. Do not commit api-keys.local.bat. Ollama Bearer is optional when OLLAMA_API_KEY is set.
- After Generate/prompt/gate/UI changes: restart portal + hard-refresh.

## Tests (focused)

```bat
mvn -q "-Dtest=RecoveryPlanParserTest,HealCascadeTest,FreeInventHealerTest,RequiredControlFiller*Test,StepIntentBinderTest,GenerateAuthoringRulesTest,ProvePhase*Test" test
```

## Candidate scorer pack

- Spec: docs/superpowers/specs/2026-10-16-candidate-scorer-design.md
- Plan: docs/superpowers/plans/2026-10-16-candidate-scorer.md
- Gate: CandidateScorerFixtureTest + bind-quality.json before live AxisPay Execute
- Wired: bind/ollama/vision/heal counters flush on fail; both Ollama and Cursor TIE picks get HealPickPayload (whyTied + neighborhood + scored shortlist; screenshotPath optional/empty-ok); DOM grounding, no Midscene vision-first; preferStable via CandidateScorer.stability
- bind-quality.json is **job totals** (one BindQualityMetrics per ProvePhase job; proveOne must not zero counters; pageCatalog.clear() stays per TC)
- ASSERT_VISIBLE “{Field} is accepted”: unique editable field on TIE, or spent fill locator via preferIdsForSpentFills. Click twins stay AMBIGUOUS. FieldAcceptedAssertBindTest.
