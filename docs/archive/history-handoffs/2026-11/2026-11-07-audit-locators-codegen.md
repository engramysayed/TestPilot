# Audit — defects, business gaps, locator + codegen enhancements (2026-11-07)

**Scope:** whole `src/main/java/delivery` tree (≈100k lines), with depth on authoring → prove → codegen locator flow, tunnel (A5), jobs/leases, credits.
**Graph:** HEAD `43922ae` = graph build commit (graph fresh; 88 dirty files on top).
**Build:** not run in this session (Maven Central unreachable from sandbox) — run the commands at the bottom.

---

## 1. Fixed this session (tunnel A5)

| # | Defect | Fix |
|---|--------|-----|
| T1 | `tunnel start` re-enrolled every run (new token, relay URL changes) | CLI caches creds in `KEEL_HOME/tunnel/`; enroll accepts `reuseToken`+`agentSecret`; `--re-enroll` |
| T2 | Stale `tunnel-index/{oldToken}.json` + relay queue after re-enroll | Deleted on new enrollment; `TunnelRelayService.forget(old)` |
| T3 | **`active` never expired** → execute jobs routed to a dead tunnel forever | `delivery.tunnel.heartbeat-ttl-seconds` (60); `isLive()` gates job baseUrl, public proxy, badge (`stale`) |
| T4 | **Relay race**: request offered before waiter registered → fast agent `complete` 404, caller hangs 90s | Register future before `offer` |
| T5 | Timed-out request stayed queued; late agent executed it (side effects, nobody waiting) | Removed from queue on timeout |
| T6 | **Query string dropped** through `/tunnel/p/{token}/…` (`getRequestURI` has no query) | Appended `getQueryString()` |
| T7 | Agent loop died on any IOException / local app down / `Expect` header (java.net.http restricted) | Backoff on poll errors, 502 on local failure, restricted headers stripped |
| T8 | CLI Basic auth sent as `Bearer Basic …` | Scheme passthrough |
| T9 | `tunnel start` fell through to help → exit 2 | `return 0` |
| T10 | **Security:** portal `JSESSIONID`/remember-me cookies forwarded to the tunneled app; app could `Set-Cookie: JSESSIONID` on portal origin | Stripped both directions (`TunnelPublicProxyCookieTest`) |
| T11 | Local proxy sent chunked empty body for 204/304/HEAD | `-1` length |
| T12 | 500 on agent timeout / queue full | 504 / 503 + `Retry-After` |

## 2. Open defects (not fixed — need a decision or larger change)

| Sev | Area | Defect | Suggested fix |
|-----|------|--------|---------------|
| **High** | Tunnel security | `/tunnel/p/**` is served **on the portal origin**. Any page served through a tunnel runs JS same-origin with the portal: it can call `/api/**` with the visitor's session (CSRF is ignored for `/api/**`, and same-origin JS can set `X-Keel-Requested-With`). T10 removes cookie leakage but not this. | Serve the tunnel on a **separate host** (`tunnel.<domain>`); reject `/tunnel/p/**` when `Host` ≠ tunnel host; reject portal routes on the tunnel host. |
| High | Tunnel | Relay headers are `Map<String,String>` → **multiple `Set-Cookie` collapse to one** (`TunnelAgentExecutor` takes `v.get(0)`). Apps whose login sets ≥2 cookies fail only through the tunnel. | Change relay contract to `Map<String,List<String>>` (agent JSON arrays); backward-compatible reader. |
| High | Jobs | `JobLeaseReconciler` runs **only at startup**. Execute/Hunt/Generate workers **never heartbeat** (only `ConversionWorker` does; lease = 45s), so a periodic reconciler can't be added safely today → a hung job stays RUNNING until restart (Admin Ops text literally says "restart the portal"). | Add a heartbeat thread to all workers (copy `ConversionWorker.runWithProgress`), then schedule `reconcileExpiredLeases()` every 60s. |
| Med | Credits | `REASON_JOB_REFUND` is defined but **never used**. Jobs that fail for platform reasons (`INTERRUPTED_UNCERTAIN`, worker crash, LLM provider outage, cancelled before browser start) keep the debit. | `CreditService.refundForJob(jobId, reason)` idempotent on `refund:job:{id}`; call from fail paths with infra categories (`RetryCategory`). |
| Med | Tunnel | Agent secret stored plaintext in `enrollment.json`; token in URL (logs, Referer). | Hash secret at rest; `Referrer-Policy: no-referrer` on proxied responses; edge rate limits (already listed). |
| Low | Tunnel | `TunnelEnrollmentService.sessions` map grows unbounded (one entry per enroll, never removed except on re-enroll now). | Drop the in-memory map; disk is the source of truth. |

## 3. Locator quality — enhancements (ranked by expected impact)

### L1. Four locator ranking policies that disagree (biggest win)

| Component | Order |
|-----------|-------|
| `authoring/CandidateScorer.stability` (bind) | hook 50 > **testid 45** > role+aria 40 > aria 36 > name 30 > id 24 > css 12 > xpath 8 |
| `snapshot/LocatorRanker` (prove, runtime-verified) | hook 1.0 > **testid 0.95** > role+name 0.85 > label 0.75 > title/alt 0.7 > text 0.65 > id 0.6 |
| `codegen/SemanticLocatorLadder` (what ships in the ZIP) | hook 100 > **role 80 > label 70 > testid 60** > css 40 > id 30 > name 25 > xpath 10 |
| `codegen/LocatorPreference` | +2 for anything containing `btn`/`button`, **−3 for every id/name** |
| `job/ProveWiring` tier map | id with ≥3 digits = dynamic (different rule from `GeneratedIdDetector`); `name` = LABEL; any xpath = CSS_CLASS |

Effect: bind + prove pick `data-testid`, then codegen promotes `[role][aria-label='Place order']` to primary (see `docs/handoff/samples/W2_ladder_Checkout_Page.java`). aria-label/text are **copy- and language-dependent** — for bilingual (Arabic/English) apps the shipped primary breaks when the locale switches, while the stable test id sits in fallback #1.

**Recommendation:** one `LocatorStabilityPolicy` used by all four, with a per-project mode:
- `stable-first` (default for apps with test ids / multilingual): hook > testid > human id > name (form controls) > role+name > label-anchored > text > attr css > ordinal.
- `user-facing-first` (current W2 behaviour, Playwright style) as opt-in.
Replace `ProveWiring`'s `\d{3,}` rule and `LocatorPreference`'s blanket id/name penalty with `GeneratedIdDetector`.
`CodegenW2EmitTest` pins the current order — update it with the decision.

### L2. ASCII-only tokenization breaks intent matching for Arabic / accented UIs
`CandidateScorer.tokens`, `StepIntentBinder.tokens`/`hayContainsToken`/`normalizeLocatorToken`, `RequiredControlFiller`, `OutcomeKind` all split on `[^a-z0-9]`. An Arabic step without quotes ("اضغط على حفظ") yields **zero tokens** → `intentMatch == 0` → `score()` drops every candidate (it requires an intent signal) → step falls to LLM/heal or TODO. Fix: `[^\p{L}\p{N}]+` with `UNICODE_CHARACTER_CLASS`, Unicode word boundaries in `hayContainsToken`, normalise Arabic (strip tashkeel, unify أ/إ/آ→ا, ة→ه, ى→ي), and add an Arabic stop list (اضغط، انقر، اختر، أدخل، تحقق، الصفحة، زر…).

### L3. Substring matching produces false intent hits
`CandidateScorer.intentMatch`/`labelDistance` use `label.contains(token)`: "add" matches "Address", "save" matches "Unsaved changes". `StepIntentBinder.hayContainsToken` already does whole-word matching — reuse it in the scorer; give exact-label equality a clear bonus over contains.

### L4. Text/label XPath under-scored at bind time
`CandidateScorer.stability` gives any xpath 8, so `//button[contains(normalize-space(.),'Save')]` (validator-approved, robust for buttons) scores below an unremarkable `name`. Score label-anchored / tag-scoped text xpaths ~32–34; keep ordinals at 2.

### L5. `uniqueAttribute` is O(n²)
`DomCandidateExtractor.uniqueAttribute` scans `doc.getAllElements()` for every (element, attribute) check. On 5–10k-node SPAs this is tens of millions of comparisons per snapshot, per step. Build an `attr→value→count` index once per `extract()`.

### L6. `name` attribute treated as stable without framework checks
ASP.NET WebForms (`ctl00$MainContent$txtUser`), JSF (`j_idt12:email`), and Angular-generated names pass `usableIdentifier` partially. Extend `GeneratedIdDetector.FRAMEWORK_SHAPES` with `^ctl\d+\$`, `j_id(t)?\d+`, `:\d+:` segments; prefer the trailing human segment via `[name$='txtUser']`.

### L7. Hashed class names in fallbacks
Validator rejects class CSS as primary, but bag fallbacks can still carry `button.submit` / `.css-1x2y3z` / `.Button_root__a8f3k` (CSS-modules / emotion / styled-components). Filter fallbacks through a generated-class detector so fallback #2 isn't dead on next deploy.

## 4. Codegen — enhancements

| # | Enhancement | Why |
|---|-------------|-----|
| C1 | Apply L1 policy in `MultiLocatorBag` + `EmitStepNormalizer` | Shipped primary = most stable, not most "semantic". |
| C2 | Emit a per-locator `@KeelStability("testid|role|text|ordinal")` comment/annotation and a ZIP `locator-report.md` (counts per tier, list of ordinal/text primaries) | Customer sees brittleness before running; feeds A4 run memory. |
| C3 | When the primary is text/aria based, always add the test id / human id as fallback even if it scored lower (today fallbacks are capped at 2 and may be two text variants) | Locale switch survives via fallback. |
| C4 | Refresh `docs/handoff/samples/*` — they show `By.css(...)` and `_fb1/_fb2` duplicate fields that the generator no longer emits | Samples are what reviewers read; `By.css` would not compile. |
| C5 | Feed heal-accepted locators (A4 run memory / `HealAcceptPatcher`) back as **preferred** candidates on next Automate UPDATE, not only as heal hints | Closes the loop: a locator healed once shouldn't be re-derived wrong next regenerate. |

---

## Verify (run on Windows)

```bat
mvn -pl keel-portal -am -DskipTests package
mvn -pl keel-portal "-Dtest=TunnelBrokerApiTest,TunnelRelayServiceTest,TunnelJobBaseUrlServiceTest,TunnelEnrollmentDiskStoreTest,TunnelPublicProxyCookieTest,FailurePackApiTest,StructuredPlanApiTest,RunMemoryApiTest,SuiteSliceEApiTest,VisionPolicyApiTest" test
powershell -File scripts/tunnel-live-smoke.ps1
```
