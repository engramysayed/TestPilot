# Named approval packet — candidate `2721e6d` (product `23f9351`)

**Status:** signatures **pending**. An agent commit is not an approval. Do not treat this packet as product, security, or privacy sign-off.

**Public rollout:** HOLD. This packet does not authorize publishing or enabling public access.

## Subject

Current validation candidate **`2721e6d6cf0083192718b21a25864c27ffa68702`**  
`test(phase5): assert preferred hooks write to the tenant site folder`

Nominated product SHA **`23f93514383982fbc53074d6b36abf5bcaa9ce56`**  
`feat(phase6): gate webhook destinations and rematerialize pinned reruns`

`2721e6d` changes only the preferred-hooks patch test. Documentation-only commits after `2721e6d` are **not** the code candidate.

## Linked evidence (all attributed to `23f9351`)

| Artifact | What it covers |
|----------|----------------|
| [LAUNCH-DECISION.md](LAUNCH-DECISION.md) | HOLD decision and remaining gates |
| [phase5-validation.md](phase5-validation.md) | Environment inspection, `23f9351` default-suite failures, `2721e6d` deterministic counts, skipped tests |
| [phase5-install-drills.md](phase5-install-drills.md) | Isolated shared/dedicated JVMs on `23f9351` |
| [phase5-restore-drill.md](phase5-restore-drill.md) | Workstation DB/artifacts/key checksum restore |
| [dedicated-install.md](dedicated-install.md) | Dedicated identity and CIDR policy |
| [constitution-supersession.md](constitution-supersession.md) | Constitution 1.2.0 local-only / Excel-only supersession |
| [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) | Phase 6 product acceptance on `23f9351`; Phase 5 still open |
| [APPROVALS.md](APPROVALS.md) | Signature table |

## What each role is asked to approve

| Gate | Role | Asked to confirm | Evidence they must see | Signature |
|------|------|------------------|------------------------|-----------|
| First-release result / dry-run / invention contract | Product | Dry-run stays `SIMULATED` and is not advertised as browser proof; invented values stay labeled | Result-integrity docs + deterministic suite on `2721e6d` | _pending_ |
| Hosted-target and tenant isolation model | Security | Shared vs dedicated network policy, opaque `ws_` tenants, cross-tenant rejection | Install drills + `TenantIsolationApiTest`; **deployed isolation still missing** | _pending_ |
| AI provider / data / screenshot retention policy | Privacy | Allowlist fail-closed, screenshot redaction, sidecar scrub, retention claims | Local redaction/canary tests; **no signed policy yet** | _pending_ |
| Dedicated-install identity and CIDR document | Security | Dedicated uses the same identity model; CIDRs are install-wide only | [dedicated-install.md](dedicated-install.md) | _pending_ |
| Constitution 1.2.0 supersession | Product | Local-only AI and Excel-only input are not runtime guarantees | [constitution-supersession.md](constitution-supersession.md) | _pending_ |

Approvers must write **name, date, and decision (approve / reject / hold)** on the matching [APPROVALS.md](APPROVALS.md) row. Until then every row stays `_unsigned_`.

## Known gaps the packet does not hide

- No production shared or dedicated host was available.
- Workstation restore is not P4-02.
- Live Generate → Execute → Automate NEW → replay → UPDATE → replay was not run on authorized representative applications.
- Phase 6 product acceptance on this SHA is separate from Phase 5 launch.
