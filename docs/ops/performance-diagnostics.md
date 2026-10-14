# Performance diagnostics and first optimization pass

Updated 2026-09-27. Working-tree changes; no live provider benchmark or release certification implied.

## Enabled improvements

Precision batch preparation extracts the DOM once and memoizes selector liveness within that batch only. Each new batch/snapshot starts with an empty cache. Intent-specific spent/failed-control filtering and the live executor remain active. The controlled three-intent/three-selector regression now makes three probes instead of the prior nine. This is a probe-count reduction, not a measured whole-job speedup.

Precision also checks an exhausted call budget before DOM extraction/browser probing. Provider availability and fallback behavior remain explicit.

## Timing records

Search the existing job/application logs for `PERF`:

- `precision_prepare`: intent count, candidate count, distinct live probes, elapsed milliseconds.
- `cursor_spawn`: Java process-start overhead only; excludes later Node/SDK initialization.
- `cursor_prepare`: sidecar request/prompt preparation after module import.
- `cursor_agent`: elapsed `Agent.prompt` operation, emitted on stderr by the sidecar.
- `cursor_call`: outer call total, including startup/wait/cleanup; skipped calls may be near zero. Do not add it to its nested timers.
- `ollama_http`: HTTP elapsed milliseconds and request-byte count, including unsuccessful sends.
- `ollama_model`: provider-reported total/load/prompt/generation nanoseconds and token counts. Missing fields are -1, not zero. Returned `ChatOutcome` retains these measurements.
- `browser_execute`: elapsed execution/evidence time and step count.
- `settle`: elapsed post-action settling time for the driver-aware path.

Telemetry does not include prompt bodies, input values, credentials, screenshots, URLs or model answers. Existing surrounding operational logs retain their existing behavior; this change is not a complete logging-redaction audit. Cursor stderr is bounded by the existing client log handling; these are diagnostic logs, not yet a persistent per-job metrics dashboard. Aggregate by job/thread context where available; do not infer exact concurrent job attribution from timestamps alone.

## Adaptive settling: opt-in qualification

`DELIVERY_ADAPTIVE_SETTLE` (system property or environment) defaults to false. Enabling it on driver-aware settling waits for document completion, no `aria-busy=true`, and a 500 ms observed DOM quiet period, with at least 750 ms observation subject to the configured budget. It uses the existing `DELIVERY_POST_ACTION_WAIT_MS` as its time budget. Same-URL changes do not require a navigation event. A deadline reports readiness unconfirmed; the following locator/action/assertion still has to validate. Session/transport/script errors surface.

The old fixed-wait behavior remains the default until representative application validation passes. Calls without a driver retain the fixed-wait behavior. DOM quietness does not prove an asynchronous operation has completed; applications with delayed updates, continuously changing DOM or incomplete busy semantics need application-specific postconditions. Do not turn this on globally based only on mocked readiness tests.

## Next measurement run

Use a small authorized, already-working case pack with frozen inputs and the same model/configuration. Capture one cold and one warm run, separating model load, prompt processing, generation, page preparation, execution, settling and retry count. Then repeat with adaptive settling enabled on that same pack, checking all business assertions and downloaded replay. A failed/partial result is not a successful speedup.

Only after that comparison select the next intervention: model residency/keep-alive if loading dominates; input reduction if prompt processing dominates; task-specific output limits if generation dominates; or a persistent sidecar if startup/SDK overhead is material. Persistent workers need tenant/request isolation, cancellation and crash recovery tests. This pass does not add one or silently change model/quality/provider settings.

## Validation evidence

`target/performance-regression.log`: 104 tests, zero failures/errors/skips, completed 2026-09-27T22:37:41+03:00. Includes Precision, provider client, waits, execution, reuse, runner API and portal checks. Sidecar passed `node --check`.

The final exhausted-budget shortcut is verified separately in `target/performance-final-focused.log`; read its actual result rather than attributing that later change to the earlier 104-test run. Adaptive readiness tests use a mocked WebDriver. No live Cursor or Ollama call, hardware profiling, model change, installation, commit or publication is part of this evidence.

## Second pass: action eligibility and real browser evidence

Precision now applies `StepIntentBinder.actionCompatible` before browser liveness probes. This is the same rule already enforced when accepting a model choice; controls that cannot perform the requested action are removed before expensive browser checks and model shortlisting. Spent/failed-control filtering, liveness, locator validation and actual execution remain required. A synthetic username-entry test with 100 buttons and one input now makes one browser probe instead of 101. This does not imply a 101-fold end-to-end speedup.

`PostActionSettleLiveSmokeTest` is an explicit opt-in local synthetic Chrome test, using a supplied existing ChromeDriver executable. It installs nothing, contacts no model and restores settings/quits its own browser. The first sandboxed run could not start Chrome; the approved outside-sandbox retry passed on 2026-09-27T22:45:00+03:00:

- Ready page: 893 ms settling versus the configured 5000 ms fixed pause.
- Same-URL page with a 1500 ms delayed update and `aria-busy`: 2147 ms settling, with the resulting text verified as Complete.
- Test result: 1 passed, 0 failed/skipped. Evidence: `target/performance-live-browser.log`.

The focused second-pass checks passed 26 tests (`target/performance-second-focused.log`). The broader regression passed 147 tests, zero failures/errors/skips, at 2026-09-27T22:46:17+03:00 (`target/performance-second-regression.log`). This is the selected regression cluster, not the full repository suite; installation drills were skipped separately by their configuration.

Adaptive waiting remains opt-in: this fixture establishes browser behavior for document readiness/DOM quietness/busy signaling, not universal readiness for arbitrary delayed applications. No live provider timing was available in existing logs; `ollama ps` showed no loaded models at inspection. Model quality settings, residency and persistent Cursor processes therefore remain unchanged pending a measured authorized workflow.

## Third pass: task-specific output budgets and HTTP reuse

Completed locally; evidence reviewed 2026-09-28. Ollama candidate-selection calls in `AuthoringService` now use `completeRankingJson`, with an output ceiling of 512 tokens (or a smaller explicitly configured client budget). Full-suite generation and JSON repair calls retain their original budgets, including the 8192-token generation setting. The cap limits output, not input context, and does not change the selected model or its thinking setting.

A response ending with `done_reason=length` during ranking raises an error instead of accepting a potentially partial candidate selection. Existing bounded healing/fallback logic handles that error. Thread-local request scope is restored in `finally`, including HTTP timeout and truncation paths; subsequent generation requests do not inherit the ranking cap. Provider timing is retained even for a truncated ranking response.

`LocalLlmClient` instances now share one stateless Java HTTP transport, permitting connection reuse and avoiding per-instance connection pools. Models, endpoints, prompts, deadlines and provider authorization remain per request. No cookie handler or authenticator was added. Sharing the transport does not share model conversation history.

Validation:

- Focused checks: 29 tests, zero failures/errors/skips (`target/performance-model-budget.log`).
- Broader final authoring/generation/Precision/reuse/privacy/portal regression: 386 tests, zero failures/errors, one skip; BUILD SUCCESS at 2026-09-27T22:51:23+03:00 (`target/performance-third-regression.log`).
- Named skip: `FacebookSubmitLiveRebindTest.liveRegPageSubmitBindPrefersButtonWhenPresent`, missing local Facebook IR.
- `OllamaRequestBudgetTest` uses a controlled local HTTP server, including simultaneous clients and a delayed response. It does not invoke a real model. All tests restore their policy settings and stop their server/executors.

Limits: no measured live inference speedup or candidate-quality comparison yet. Thinking-heavy models may reach the ranking cap; those outcomes fail honestly and must be included in the live benchmark rather than hidden. Cursor remains on isolated one-shot Auto requests. Persistent Cursor workers, model residency tuning and global activation of adaptive waits remain pending measured workflow evidence and their own lifecycle/quality gates. Nothing in this phase changes the public release decision.
