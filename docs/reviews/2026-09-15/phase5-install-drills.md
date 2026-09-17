# Phase 5 isolated-install drills (2026-09-17)

Workstation `Ramy-Sayed`. Public launch remains **HOLD**.

These are **two Spring Boot test installations** with distinct store roots and `delivery.install.mode`. They are not production VMs, not a kernel firewall, and not a signed P0-01.

## Command

```
mvn "-Dtest=InstallNetworkBridgeTest,SharedInstallDrillTest,DedicatedInstallDrillTest" test
```

**Result:** Tests run: 7, Failures: 0, 2026-09-17T12:54:15+03:00.

`HuntApiTest` was re-run after `HuntWorker` gained an `Environment` constructor argument (BUILD SUCCESS). The prior 121-test RC suite was **not** repeated.

## Shared drill (`delivery.install.mode=shared`)

Store: `./target/p5-shared-install/store`. Poison `delivery.install.private-cidrs=10.0.0.0/8` is present and **must not** apply.

- `/api/health` reports `installMode=shared`; `/api/ready` store writable.
- `TargetNetworkPolicy.forJob` after `InstallNetworkBridge` blocks `http://10.0.0.8:8080`.
- Two tenants: Bob cannot list Alice artifacts (404). Snapshot/restore of this store-root restores sentinel ZIP bytes and SHA-256.
- Generate **import** (JSON library, not live LLM stories) → dry-run Execute COMPLETED → dry-run Automate NEW COMPLETED → download ZIP → `mvn test-compile` on the unzipped customer POM.

## Dedicated drill (`delivery.install.mode=dedicated`, `10.0.0.0/8`)

Store: `./target/p5-dedicated-install/store`.

- `/api/health` reports `installMode=dedicated`.
- `forJob("http://10.0.0.8:8080/app")` allows that origin; `192.168.0.4` stays blocked.
- Same tenant-boundary + snapshot/restore on **this** store-root.

## Still open

- Production shared/dedicated hosts
- Kernel / worker-network isolation on those hosts
- Restore on those hosts' FileStores
- Live LLM Generate and live browser Automate/Execute against representative apps
- Named product/security/privacy approvals
