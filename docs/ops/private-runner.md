# Private runner

A customer-operated runner connects **outbound** to the control plane. The control plane never needs inbound access to the customer network.

**Deployment isolation validation (P2-03) is a separate requirement.** Local runner tests do not certify a shared or dedicated production host. Public rollout stays HOLD; validation candidate is `2721e6d`.

## What this release implements

- Enrollment and revocation APIs on the project (`POST/GET/DELETE /api/projects/{id}/runners`). OWNER and ADMIN may enroll or revoke; MEMBER may list. Tokens are `tp_run_…` and stored as SHA-256 only.
- Runner HTTP API under `/api/v1/runners/**` (Bearer `tp_run_`): heartbeat, tenant-scoped claim, frozen input download, lease heartbeat, HMAC artifact upload, complete.
- In-process portal workers skip jobs with `runner=private`. Claim uses existing durable leases; expired **BROWSER** leases become `INTERRUPTED_UNCERTAIN`; other stages re-queue.
- Jobs still freeze library/environment pins, provider allowlist snapshots, and budget reservations at queue time. Complete rejects a changed input hash or allowlist.
- Agent: `delivery.runner.PrivateRunnerAgent` (`--portal --token --work-dir --dry-run`). Installer scripts write a start wrapper under `scripts/private-runner/`. The agent heartbeats the job lease during work and posts `CANCELLED` instead of `COMPLETED` when the portal requested cancel.
- Dry-run execution uses `DryRunExecuteService` on the runner host. Live browser/CDP uses `ExecuteJobRunner` when `--dry-run false`. The agent posts claim stage `BROWSER` before live execution so expired disconnects follow `INTERRUPTED_UNCERTAIN` instead of silent re-queue.
- Local live validation (`0c49603`, `PrivateRunnerLiveBrowserProcessTest`): separate portal + agent processes, `--dry-run false`, headless Chrome, controlled `127.0.0.1` pages. Covers claim → frozen inputs → browser execution → signed artifact upload → completion, plus cancel and BROWSER-stage disconnect. This does not close deployed isolation or customer-app acceptance.

## Not included

- Auto-update channel or attested runner builds
- Network isolation proof that a private app is unreachable from the shared control plane (P2-03)

## Operator steps

1. Project Settings → Private runners → enroll. Copy the `tp_run_` token once.
2. On the customer host: `scripts/private-runner/install.ps1` or `install.sh`, set `KEEL_PORTAL_URL` and `KEEL_RUNNER_TOKEN`, start the wrapper.
3. Submit Execute with `"runner":"private"` (public job API) or form field `runner=private`.
4. Revoke the runner to stop further claims immediately.

Evidence egress defaults from `PrivateRunnerRules.EvidenceEgress`: artifacts and screenshots may upload; network bodies and secrets must not be posted back except the signed artifact zip.
