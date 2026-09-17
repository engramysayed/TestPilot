# Private runner — foundation versus execution

Enrollment, tenant binding, heartbeat, claim, revocation, and offline recovery **rules** live in `delivery.runner.PrivateRunnerRules`. They are a foundation for a customer-operated runner that connects **outbound** to the control plane. The control plane never needs inbound access to the customer network.

**Deployment isolation validation (P2-03) is a separate requirement.** Passing these unit rules does not certify a shared or dedicated production host. Public rollout stays HOLD; candidate `75e6996` is unchanged.

## Implemented (rules only)

- Enrollment record: runner id, tenant id, enrolled-at, revoked-at, last heartbeat
- Claim allowed only for the bound tenant, while not revoked, and while heartbeat is inside the grace window (default 45s)
- Cross-tenant or revoked runners cannot claim
- Offline recovery uses the durable job contract: expired **BROWSER** leases become `INTERRUPTED_UNCERTAIN`; other stages re-queue (`DurableJobClaim.reconcileExpired`)
- Evidence egress policy defaults: artifacts and screenshots may leave the runner; network bodies and secrets do not

## Not implemented (actual runner execution)

These capabilities are **not** product-ready. Do not treat the rules class as a shippable runner.

- No runner agent binary, installer, or auto-update channel
- No enrollment API, token issuance, or revocation UI for runners
- No heartbeat/claim HTTP endpoints consumed by an out-of-process worker
- No job download of pinned library/environment onto a customer host
- No browser/CDP execution on the customer machine (portal jobs still run **in-process** in the portal JVM)
- No signed artifact upload from a remote runner, or trust/attestation of runner builds
- No queue assignment that prefers or requires a private runner instead of in-process workers
- No network isolation proof that a private app is unreachable from the shared control plane (that is P2-03)

Until those exist **and** P2-03 isolation is validated on a deployment host, private applications should not be promised as a supported execution mode.
