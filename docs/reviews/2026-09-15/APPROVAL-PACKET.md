# Named approval packet — candidate `2721e6d` (shared first release)

**Status:** signatures **pending**. An agent commit is not an approval. Do not treat this packet as product, security, or privacy sign-off.

**Public rollout:** HOLD. This packet does not authorize publishing or enabling public access.

**First-release scope:** **shared hosting only.** Dedicated-install identity/CIDR sign-off and private-runner operations are **deferred** with those products. Implementation is preserved.

## Subject

Validation candidate **`2721e6d6cf0083192718b21a25864c27ffa68702`** (unchanged)  
Nominated product SHA **`23f93514383982fbc53074d6b36abf5bcaa9ce56`**

Documentation-only commits after `2721e6d` are **not** the code candidate.

## Linked evidence

| Artifact | What it covers |
|----------|----------------|
| [LAUNCH-DECISION.md](LAUNCH-DECISION.md) | HOLD; shared-only remaining gates |
| [phase5-validation.md](phase5-validation.md) | Environment inspection, suite counts, skipped tests |
| [vision-live-smoke-rca.md](vision-live-smoke-rca.md) | Live UI-TARS misses; restricted out of first-release support |
| [phase5-install-drills.md](phase5-install-drills.md) | Local shared JVM drill (staging still required) |
| [phase5-restore-drill.md](phase5-restore-drill.md) | Workstation restore (staging still required) |
| [shared-staging.md](../../ops/shared-staging.md) | Shared staging procedure (host access missing) |
| [constitution-supersession.md](constitution-supersession.md) | Constitution 1.2.0 |
| [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) | Phase 6 complete; dedicated/private-runner launch deferred |
| [APPROVALS.md](APPROVALS.md) | Signature table |
| [dedicated-install.md](dedicated-install.md) | Preserved; **not** a first-launch approval |

## What each role is asked to approve (first release)

| Gate | Role | Asked to confirm | Evidence they must see | Signature |
|------|------|------------------|------------------------|-----------|
| First-release result / dry-run / invention contract | Product | Dry-run stays `SIMULATED`; invented values labeled; **shared** is the launch product | Result-integrity docs + deterministic suite on `2721e6d` | _pending_ |
| Hosted-target and tenant isolation model | Security | Shared public-origin policy, opaque `ws_` tenants, cross-tenant rejection | Shared drill + `TenantIsolationApiTest`; **shared staging isolation still missing** | _pending_ |
| AI provider / data / screenshot retention policy | Privacy | Allowlist fail-closed, screenshot redaction, sidecar scrub; live UI-TARS grounding **not** a launch guarantee | Local redaction/canary tests + [vision-live-smoke-rca.md](vision-live-smoke-rca.md) | _pending_ |
| Constitution 1.2.0 supersession | Product | Local-only AI and Excel-only input are not runtime guarantees | [constitution-supersession.md](constitution-supersession.md) | _pending_ |

Dedicated-install CIDR approval is **not** required to close first launch. Keep the row in [APPROVALS.md](APPROVALS.md) marked deferred.

Approvers must write **name, date, and decision (approve / reject / hold)** on the matching [APPROVALS.md](APPROVALS.md) first-release rows. Until then every first-release row stays `_unsigned_`.

## Known gaps the packet does not hide

- No shared staging host was available.
- Workstation restore is not shared-staging P4-02.
- Live G→E→A was not run on an authorized representative application.
- Dedicated and private-runner launch validation were **removed from first-launch gates**, not deleted from the product.
