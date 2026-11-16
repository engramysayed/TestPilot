# Operator runbooks

These are first-response notes. They do not close Phase 2 isolation/privacy gates.

## Stuck leases

`JobLeaseReconciler` runs at process start. Restart the portal. Browser-stage expiry becomes `INTERRUPTED_UNCERTAIN` — do not auto-replay; the user must review.

## Isolation errors

Application-layer `TargetNetworkPolicy` / `WorkerNetworkGuard` blocks are not worker-network isolation. If a job hits a blocked destination, treat it as a policy deny. Do not widen shared mode with dedicated CIDRs.

## Storage pressure

Check disk for `delivery-store`. Retention sweeper honors `delivery.retention.days`. Expired artifacts must not download a newer ZIP.

## Repeated subprocess timeouts

`ProcessSupervisor` kills the compile/process tree. Inspect job message and force-stop `CANCELLING` rows if the worker is gone.

## Budget / Precision exhaustion

Precision max-calls are snapshotted at admit. Changing project settings does not raise a running job's cap. Start a new job after raising the project limit.

## Queue saturation

Admission rejects before a QUEUED row (`QUEUE_SATURATED` / `INSTALL_BUSY`). Counts are in-process JobEntity rows, not a distributed limiter.
