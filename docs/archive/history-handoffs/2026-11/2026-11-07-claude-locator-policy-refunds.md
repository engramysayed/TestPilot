# Shared locator policy, Arabic steps, refunds, tunnel isolation, durable leases — session handoff (2026-11-07, Claude)

**Repo:** `TestPilot` · **HEAD at start:** `43922ae` (= Graphify build) · **Nothing committed.**
**Build/test:** NOT run in this session (Maven Central unreachable from the sandbox). Run the commands at the bottom first.

## Shipped (code + tests)

| Area | What changed | Key files |
|------|--------------|-----------|
| Tunnel (A5) | Enrollment reuse (`reuseToken` + CLI cache `KEEL_HOME/tunnel/`, `--re-enroll`), stale index cleanup, heartbeat TTL (`stale`), relay race fix, query string, 504/503, agent resilience, Basic auth, exit code | `portal/tunnel/*`, `portal/cli/*`, `TunnelEnrollmentDiskStoreTest` |
| Tunnel security | **Dedicated host**: `/tunnel/p/**` only on the tunnel host, portal routes refused there; same host as portal ⇒ relay disabled; loopback = dev. Portal cookies stripped both directions. `Referrer-Policy: no-referrer` | `TunnelHostPolicy`, `TunnelHostIsolationFilter`, `TunnelHostPolicyTest`, `TunnelPublicProxyCookieTest` |
| Tunnel cookies | Multi-value headers through the relay (`multiHeaders`), every `Set-Cookie` survives | `TunnelRelayService.completeMulti`, `TunnelAgentController`, broker test `publicProxy_keepsEverySetCookie` |
| Durable jobs | `LeaseKeeper` renews every claimed job's lease (all workers, 15s); `JobLeaseReconciler` now also runs every 60s; hung jobs lapse after `-Ddelivery.jobs.max-runtime-minutes` (180) | `portal/jobs/LeaseKeeper`, `PortalStore.beginWork`, `LeaseKeeperTest` |
| Job error code | `JobEntity.error_code` persisted (was memory-only → lost on restart) | `JobEntity`, `PortalStore.syncJobPersistence/hydrate` |
| Refunds | `refund_requests` table; auto-open for charged jobs that FAILED with `RetryCategory.INFRA`; owner "Request refund" on the run page; **Admin → Refunds** list/detail (who, project, run, charge, failure category/code/message, diagnostics, timeline, ledger, links to run/evidence/failure pack, recommendation) with full/partial approve or reject-with-reason; idempotent `JOB_REFUND` ledger | `RefundRequest*`, `CreditService.refundForJob/chargedForJob`, `AdminRefundsController`, `JobRefundController`, `admin-refunds.html`, `status.html`, `RefundRequestTest` |
| Locator policy | One ranking for bind, prove, codegen, vision: **hook → test id → unique id → unique name → role+name → label → text → CSS → XPath → ordinal**; per-project mode `stable-first` (default) / `user-facing-first` (Project → Settings → Locator ranking) | `delivery/locator/*`, `CandidateScorer.stability`, `LocatorRanker.policyScore`, `SemanticLocatorLadder.rank/safePromotion`, `ProveWiring.currentScore`, `PreferredHooksStore.loadMode/saveMode` |
| Test id first | A verified test id (or hook) is promoted over the proven primary even with no hooks; stable-first also promotes a human id/name over a copy-dependent primary | `SemanticLocatorLadder.safePromotion` |
| Arabic / Unicode steps | `IntentText` (normalize, tokens, whole-word, Arabic verbs → classifier keywords); used by scorer, binder, required-control filler | `authoring/IntentText`, `IntentTextTest` |
| Matching quality | whole-word matching (no "add" ⊂ "Address"), text XPath bind score 8 → 30, O(1) attribute uniqueness index, ASP.NET/JSF names + generated classes treated as brittle, brittle fallbacks dropped | `CandidateScorer`, `DomCandidateExtractor`, `GeneratedIdDetector`, `MultiLocatorBag`, `EmitStepNormalizer` |
| Codegen bug | Bag fallbacks emitted `By.css(...)` (does not exist) as **unused** `_fbN` fields. Now one `FirstOf.of(...)` field (first matching locator wins, fallback wins logged) | `Page.java.ftl`, `PageAccumulator.emittableBag`, template `utils/locators/FirstOf.java` |
| Vision grounding | New locators follow the policy (test id before id) and try the next option when one is invalid (generated id no longer aborts grounding); JS id detector knows ASP.NET/JSF | `ElementGrounder.buildLocator`, `SeleniumGroundingBrowser` |
| Prompts | `grounding/locator-policy` + `heal/pick` describe the new order and bilingual matching; `VERSION` 2026.11.07 | `src/main/resources/prompts/*` |
| Docs | History / knowledge / handoff split; README visuals | `docs/README.md`, `docs/knowledge/CURRENT.md`, `docs/history/HISTORY.md` |

## Changed test expectations (on purpose)

- `CodegenW2EmitTest`, `CodegenW3EmitTest`: test id is primary; role/aria is a fallback; no `_fb1`, no `By.css(`.
- `LocatorRankingLiveSmokeTest`: generated id scores 0.0; `name="q"` beats placeholder; fallback scores 1.0 / 0.90.
- `PreferredHookRankerTest`: hook scores 1.0 (was reported as TEST_ID 0.95).
- Prompt version tests: `2026.11.07`.

## Verify (Windows)

```bat
mvn -pl keel-portal -am -DskipTests package
mvn -pl keel-portal "-Dtest=Tunnel*Test,LeaseKeeperTest,RefundRequestTest,LocatorStabilityPolicyTest,IntentTextTest,CandidateScorer*Test,Codegen*EmitTest,PreferredHookRankerTest,NestedHookVisionGrounderTest,GroundingAuditCharacterizationTest,Prompt*Test,AdminPrompts*Test" test
mvn -pl keel-portal test
```

Then: restart the portal, open **Admin → Refunds**, and Project → Settings → **Locator ranking**. Regenerate an Automate ZIP and check pages use `FirstOf` / test ids.

## Open

See [`docs/knowledge/REVIEW-2026-11-07.md`](/docs/knowledge/REVIEW-2026-11-07.md) for mobile, UI, generate, heal/vision follow-ups.
